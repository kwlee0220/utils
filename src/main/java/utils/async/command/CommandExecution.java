package utils.async.command;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.lang3.time.DurationUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.KeyedValueList;
import utils.Preconditions;
import utils.ProcessTree;
import utils.Split;
import utils.StrSubstitutor;
import utils.async.AbstractThreadedExecution;
import utils.async.AsyncState;
import utils.async.CancellableWork;
import utils.func.Optionals;
import utils.io.EnvironmentFileLoader;
import utils.io.FileUtils;
import utils.stream.FStream;


/**
 * 주어진 명령어 프로그램을 sub-process를 통해 실행시키는 비동기 작업.
 * <p>
 * {@link Builder}로 생성하며, 실행은 부모 클래스 {@link AbstractThreadedExecution}의 계약을 따른다.
 *
 * <h3>변수 치환</h3>
 * Command line의 각 argument에는 두 단계의 변수 치환이 순차로 적용된다:
 * <ol>
 *   <li>일반 변수 치환 — 예약어 {@code ${WORKING_DIR}} 등 미리 정의된 시스템 변수를 치환한다.</li>
 *   <li>명령 변수 치환 — {@link Builder#addVariable(CommandVariable)}로 등록된
 *       {@link CommandVariable}을 {@code ${name}} 또는 {@code ${name:modifier}} 형태로 치환한다.</li>
 * </ol>
 * 두 단계 모두에서 매칭되지 않은 {@code ${X}}는 그대로 sub-process에 전달된다
 * (escape나 sub-process 측 shell 확장 의도일 수 있음).
 *
 * <h3>표준 입출력 redirect 기본값</h3>
 * <ul>
 *   <li>{@code stdin} — 미설정 (ProcessBuilder의 기본 {@link Redirect#PIPE PIPE}). 자식 프로세스가
 *       stdin을 읽으려 하면 부모가 입력을 닫을 때까지 hang될 수 있으므로, 사용하지 않는 경우
 *       {@link Builder#discardStdin()} 호출 권장.</li>
 *   <li>{@code stdout} — {@link Redirect#DISCARD}.</li>
 *   <li>{@code stderr} — {@link Redirect#DISCARD}. {@link Builder#redirectErrorStream()} 호출 시 stdout으로 합쳐짐.
 *       4개 stderr 메소드({@code redirectErrorStream}/{@code redirectStderrToFile}/{@code inheritStderr}/{@code discardStderr})는
 *       마지막 호출이 우선한다.</li>
 * </ul>
 *
 * <h3>종료 코드 처리</h3>
 * Sub-process 종료 코드가 {@code 0}이면 정상 완료로 간주하고, 그 외 값이면 작업 실패로 보아
 * {@link ExecutionException}을 던진다.
 *
 * <h3>환경 변수 파일</h3>
 * {@link Builder#environmentFile(File)}로 지정된 환경 변수 파일이 존재하지만 읽기에 실패하면
 * {@link ExecutionException}으로 fail-fast된다. 파일이 존재하지 않으면 무시된다.
 *
 * <h3>Timeout / Cancel</h3>
 * {@link Builder#timeout(Duration)}이 설정되어 있고 그 안에 종료되지 않으면
 * {@link TimeoutException}이 발생한다. {@link AbstractThreadedExecution#cancel(boolean) cancel(true)}
 * 호출 시 sub-process와 descendants는 SIGTERM으로 종료되고 작업은 취소 상태가 된다.
 * 어떤 경로로 종료되든 sub-process와 descendants가 leak되지 않도록 finally에서 정리된다
 * (SIGTERM 후 1초 grace, 이후 SIGKILL).
 *
 * <h3>{@link #close()} 시맨틱</h3>
 * {@link Closeable}이 호출되면 진행 중인 작업을 취소하고 완료를 기다린 뒤, 등록된 모든
 * {@link CommandVariable}을 close한다. {@code try-with-resources} 사용 시 sub-process leak 없이
 * 정리된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandExecution extends AbstractThreadedExecution<Void> implements CancellableWork, Closeable {
	private static final Logger s_logger = LoggerFactory.getLogger(CommandExecution.class);
	private static final Duration PROCESS_TERMINATION_GRACE = Duration.ofSeconds(1);
	private static final Duration PROCESS_TREE_REFRESH_INTERVAL = Duration.ofMillis(50);

	private final List<String> m_command;
	private final File m_workingDirectory;
	private final @Nullable File m_envFile;
	private final Map<String,String> m_environmentVariables;
	private final Map<String,CommandVariable> m_variables;
	private final @Nullable Redirect m_stdin;
	private final @Nullable File m_stdinFile;
	private final @Nullable Redirect m_stdout;
	private final @Nullable File m_stdoutFile;
	private final @Nullable Redirect m_stderr;
	private final @Nullable File m_stderrFile;
	private final @Nullable Duration m_timeout;
	
	@GuardedBy("m_aopGuard") private Process m_process;
	@GuardedBy("m_aopGuard") private ProcessTree m_processTree;
	
	private CommandExecution(Builder builder) {
		Preconditions.checkArgument(!builder.m_command.isEmpty(), "command must not be empty");

		// 방어적 복사: 빌드 후 호출자가 Builder 참조나 원본 컬렉션을 mutate해도 영향 없도록 한다.
		m_command = List.copyOf(builder.m_command);
		m_envFile = builder.m_envFile;
		m_environmentVariables = Map.copyOf(builder.m_environmentVariables);
		m_variables = builder.m_variables.toMap();
		m_stdin = builder.m_stdin;
		m_stdinFile = builder.m_stdinFile;
		m_stdout = builder.m_stdout;
		m_stdoutFile = builder.m_stdoutFile;
		m_stderr = builder.m_stderr;
		m_stderrFile = builder.m_stderrFile;
		m_timeout = builder.m_timeout;
		
		// Builder.workingDirectory()에서 isDirectory 검증을 수행하므로 여기서는 fallback만 처리한다.
		m_workingDirectory = Optionals.getOrElse(builder.m_workingDirectory, FileUtils::getCurrentWorkingDirectory);

		setLogger(s_logger);
	}
	
	@Override
	public void close() {
		// 실행 중이면 취소를 요청하고 종료를 기다려서 sub-process가 leak되지 않도록 한다.
		if ( !isDone() ) {
			cancel(true);
			try {
				waitForFinished();
			}
			catch ( InterruptedException e ) {
				// 'close()' 함수의 signature에 InterruptedException이 없으므로,
				// 현재 스레드의 인터럽트 상태를 복원한다.
				// 또한 위에서 이어질 변수 close 수행할 수 있다.
				Thread.currentThread().interrupt();
			}
		}

		// 등록된 모든 변수를 close 시킨다.
		FStream.from(m_variables.values())
				.forEachOrIgnore(CommandVariable::close);
	}
	
	/**
	 * Command 실행에서 사용 중인 Command variable 맵을 반환한다.
	 * 
	 * @return	Command variable 맵
	 */
	public Map<String,CommandVariable> getVariableMap() {
		return m_variables;
	}
	
	@Override
	protected void initializeThread() throws Exception {
		// AsyncState.STARTING 상태.
		// cancel하려고 해도 취소 작업이 대기 중인 상태.
		ProcessBuilder builder = buildProcessBuilder();
		Process process = builder.start();
		ProcessTree processTree = ProcessTree.of(process);
		processTree.refresh();   // root가 종료되기 전 descendants 초기 snapshot을 시도한다.
		m_aopGuard.runChecked(() -> {
			m_process = process;
			m_processTree = processTree;
		});
	}

	@Override
	public Void executeWork() throws InterruptedException, CancellationException, TimeoutException, ExecutionException {
		// (대부분의 경우) AsyncState.RUNNING 상태.
		// (드믄 경우) cancel이 호출된 상태라면 취소 작업이 대기 중인 상태.
		Process process = m_aopGuard.get(() -> m_process);
		ProcessTree processTree = m_aopGuard.get(() -> m_processTree);
		if ( process == null || processTree == null ) {
			// 연산이 중단(취소)된 상태.
			throw new CancellationException("Command execution is cancelled");
		}
		
		try {
			Duration timeout = m_timeout;
			if ( timeout != null ) {
				// 제한시간이 설정된 경우
				// 제한시간만큼 프로세스를 대기한다.
				boolean completed = processTree.waitForRootTerminated(PROCESS_TREE_REFRESH_INTERVAL, timeout);

				// 사용자의 강제 종료를 통해 프로그램이 종료되었을 수도 있기 때문에
				// 이를 확인하여 강제 종료된 경우 CancellationException을 발생시킨다.
				throwCancellationIfCancelRequested();

				if ( completed ) {
					// 제한시간 내에 마친 경우
					int retCode = process.exitValue();
					if ( retCode == 0 ) {
						// 프로그램 수행이 성공적으로 마친 경우
						return null;
					}
					else {
						// 프로그램 종료시 반환 코드가 0이 아닌 경우는 수행 실패로 간주한다.
						throw new ExecutionException("Process failed: retCode=" + retCode, null);
					}
				}
				else {
					// 제한시간이 경과한 경우. 프로세스 종료는 finally의 ensureProcessTerminated가 담당한다.
					throw new TimeoutException(timeout.toString());
				}
			}
			else {
				// 무제한 대기
				processTree.waitForRootTerminated(PROCESS_TREE_REFRESH_INTERVAL);
				int retCode = process.exitValue();
				getLogger().info("Process terminated: retCode={}", retCode);

				// 사용자의 강제 종료를 통해 프로그램이 종료되었을 수도 있기 때문에
				// 이를 확인하여 강제 종료된 경우 CancellationException을 발생시킨다.
				throwCancellationIfCancelRequested();

				if ( retCode == 0 ) {
					// 프로그램 수행이 성공적으로 마친 경우
					return null;
				}
				else {
					// 프로그램 종료시 반환 코드가 0이 아닌 경우는 수행 실패로 간주한다.
					throw new ExecutionException("Process failed: retCode=" + retCode, null);
				}
			}
		}
		finally {
			// 인터럽트/timeout/예외 등 어떤 경로로 빠져나가든 sub-process가 leak되지 않도록 정리한다.
			processTree.terminate(PROCESS_TERMINATION_GRACE);
			// defunct process 참조를 정리해 cancelWork가 죽은 프로세스에 destroy를
			// 시도하지 않도록 한다.
			m_aopGuard.run(() -> {
				m_process = null;
				m_processTree = null;
			});
		}
	}

	@Override
	public boolean cancelWork() {
		// 프로세스가 시작되어 아직 살아있는 경우에만 강제 종료한다.
		ProcessTree processTree = m_aopGuard.get(() -> m_processTree);
		if ( processTree != null ) {
			processTree.terminate(PROCESS_TERMINATION_GRACE);
		}
		return true;
	}
	
	@Override
	public String toString() {
		return FStream.from(m_command).join(' ');
	}
	
	/**
	 * 실행할 sub-process의 {@link ProcessBuilder}를 구성한다.
	 * <p>
	 * 다음 작업을 수행한다:
	 * <ol>
	 *   <li>command line의 변수 치환 (일반 변수 → 명령 변수)</li>
	 *   <li>working directory 설정</li>
	 *   <li>stdin/stdout/stderr redirect 적용</li>
	 *   <li>설정된 environment variables 및 environment file 병합</li>
	 * </ol>
	 *
	 * @throws ExecutionException environment file 로딩에 실패한 경우.
	 */
	private ProcessBuilder buildProcessBuilder() throws ExecutionException {
		// Command line에 포함된 command variable들을 실제 값으로 치환시킨다.
		// 미정의 명령 변수는 그대로 두어, 두 단계 모두에서 매칭되지 않은 ${X}는 sub-process에
		// 그대로 전달된다.
		CommandVariableLookup lut = new CommandVariableLookup(m_variables);
		StringSubstitutor cmdVarSubst = new StringSubstitutor(lut);
		
		// Command line에 포함된 일반 변수들을 실제 값으로 치환한다.
		Map<String,String> initMapping = Map.of("WORKING_DIR", m_workingDirectory.getAbsolutePath());
		StrSubstitutor subst = StrSubstitutor.with(initMapping)
									.failOnUndefinedVariable(false)
									.enableNestedSubstitution(true);
		List<String> command = FStream.from(m_command)
										.map(cmd -> {
											cmd = subst.replace(cmd);
											return cmdVarSubst.replace(cmd);
										})
										.toList();
		
		ProcessBuilder builder = new ProcessBuilder(command)
									.directory(m_workingDirectory);
		if ( getLogger().isInfoEnabled() ) {
			getLogger().info("WorkingDir: {}", m_workingDirectory);
			getLogger().info("Command (variable-replaced): {}", FStream.from(command).join(' '));
		}
		
		// Standard error 처리
		if ( m_stderrFile != null ) {
			builder.redirectError(Redirect.to(resolveAgainstWorkingDirectory(m_stderrFile)));
		}
		else if ( m_stderr != null ) {
			builder.redirectError(m_stderr);
		}
		else {
			builder.redirectErrorStream(true);
		}
		// Standard output 처리
		if ( m_stdoutFile != null ) {
			builder.redirectOutput(Redirect.to(resolveAgainstWorkingDirectory(m_stdoutFile)));
		}
		else if ( m_stdout != null ) {
			builder.redirectOutput(m_stdout);
		}

		// Standard input 처리
		if ( m_stdinFile != null ) {
			File stdinFile = resolveAgainstWorkingDirectory(m_stdinFile);
			Preconditions.checkArgument(stdinFile.isFile(), "stdin file does not exist: %s", stdinFile);
			builder.redirectInput(Redirect.from(stdinFile));
		}
		else if ( m_stdin != null ) {
			builder.redirectInput(m_stdin);
		}
		
		// 환경 변수 파일이 설정되면 해당 파일을 읽어 환경변수로 추가한다.
		Map<String,String> environments = Maps.newHashMap(m_environmentVariables);
		File configuredEnvFile = m_envFile;
		if ( configuredEnvFile != null ) {
			File envFile = ( !configuredEnvFile.isAbsolute() )
								? new File(m_workingDirectory, configuredEnvFile.getPath())
								: configuredEnvFile;
			if ( envFile.isFile() ) {
				getLogger().info("Loading environment file: {}", envFile.getAbsolutePath());
				try {
					for ( var ent: EnvironmentFileLoader.from(envFile).load().entrySet() ) {
						environments.putIfAbsent(ent.getKey(), ent.getValue());
					}
				}
				catch ( IOException e ) {
					throw new ExecutionException("failed to load environment file: "
													+ envFile.getAbsolutePath(), e);
				}
			}
		}
		
		// 환경변수 설정
		builder.environment().putAll(environments);
		
		return builder;
	}

	private File resolveAgainstWorkingDirectory(File file) {
		return file.isAbsolute() ? file : new File(m_workingDirectory, file.getPath());
	}
	
	private void throwCancellationIfCancelRequested() throws CancellationException {
		AsyncState state = getState();
		if ( state == AsyncState.CANCELLING || state == AsyncState.CANCELLED ) {
			throw new CancellationException();
		}
	}

	public static Builder builder() {
		return new Builder();
	}
	public static final class Builder {
		private List<String> m_command = Lists.newArrayList();
		private File m_workingDirectory;
		private Map<String,String> m_environmentVariables = Map.of();
		@Nullable private File m_envFile;
		private KeyedValueList<String,CommandVariable> m_variables = KeyedValueList.with(CommandVariable::getName);
		private Duration m_timeout;
		private Redirect m_stdin;
		private File m_stdinFile;
		private Redirect m_stdout = Redirect.DISCARD;
		private File m_stdoutFile;
		private Redirect m_stderr = Redirect.DISCARD;
		private File m_stderrFile;
		
		/**
		 * 현재 빌더 설정으로 {@link CommandExecution}을 생성한다.
		 * <p>
		 * Command line이 비어 있으면 ({@link #addCommand}가 한 번도 호출되지 않은 경우)
		 * {@link IllegalArgumentException}이 발생한다.
		 *
		 * @return	생성된 {@link CommandExecution}
		 * @throws IllegalArgumentException	command가 비어 있는 경우
		 */
		public CommandExecution build() {
			return new CommandExecution(this);
		}

		/**
		 * 실행할 command line의 token들을 추가한다.
		 * <p>
		 * 본 메소드는 누적적으로 동작한다 — 여러 번 호출하면 인자들이 기존 token 목록 뒤에
		 * 차례로 추가되며, 마지막 build 시점의 전체 목록이 sub-process의 command가 된다.
		 *
		 * @param command	추가할 token 리스트 (non-null, 각 원소도 non-null)
		 * @return	본 빌더 객체
		 * @throws IllegalArgumentException	{@code command}가 {@code null}이거나 원소 중 {@code null}이 있는 경우
		 */
		public Builder addCommand(List<String> command) {
			Preconditions.checkNotNullArgument(command, "command must not be null");
			for ( int i = 0; i < command.size(); i++ ) {
				Preconditions.checkNotNullArgument(command.get(i), "command element must not be null: index=%d", i);
			}

			m_command.addAll(command);
			return this;
		}

		/**
		 * 실행할 command line의 token들을 가변 인자로 추가한다.
		 * <p>
		 * 본 메소드는 누적적으로 동작한다 — 여러 번 호출하면 인자들이 기존 token 목록 뒤에 차례로 추가된다.
		 *
		 * @param command	추가할 token들 (non-null)
		 * @return	본 빌더 객체
		 * @throws IllegalArgumentException	{@code command}가 {@code null}인 경우
		 */
		public Builder addCommand(String... command) {
			Preconditions.checkNotNullArgument(command, "command must not be null");

			return addCommand(Arrays.asList(command));
		}

		/**
		 * Sub-process의 working directory를 설정한다.
		 * <p>
		 * 미설정 시 현재 JVM의 working directory가 사용된다. 본 메소드는 호출 시점에 디렉토리 존재
		 * 여부를 검증한다. 설정된 working directory는 stdin/stdout/stderr 파일의 상대 경로,
		 * environment file의 상대 경로 해석에도 기준이 된다.
		 *
		 * @param dir	working directory (non-null, 디렉토리여야 함)
		 * @return	본 빌더 객체
		 * @throws IllegalArgumentException	{@code dir}이 {@code null}이거나 디렉토리가 아닌 경우
		 */
		public Builder workingDirectory(File dir) {
			Preconditions.checkNotNullArgument(dir, "workingDirectory must not be null");
			Preconditions.checkArgument(dir.isDirectory(),
									"workingDirectory does not exist or is not a directory: %s", dir);

			m_workingDirectory = dir;
			return this;
		}

		/**
		 * Sub-process에 전달할 환경 변수들을 설정한다.
		 * <p>
		 * 호출자가 전달한 맵이 그대로 보관되었다가 build 시 방어적 복사된다. 본 메소드는 마지막
		 * 호출이 우선하므로, 누적이 필요하면 호출자가 직접 병합해야 한다.
		 * {@link #environmentFile(File)}로 지정된 파일의 환경 변수와 충돌하면 본 메소드로 지정된
		 * 값이 우선한다 (파일은 누락된 키만 채운다).
		 *
		 * @param envs	환경 변수 맵 (non-null)
		 * @return	본 빌더 객체
		 * @throws IllegalArgumentException	{@code envs}가 {@code null}인 경우
		 */
		public Builder environmentVariables(Map<String,String> envs) {
			Preconditions.checkNotNullArgument(envs, "environmentVariables must not be null");

			m_environmentVariables = envs;
			return this;
		}

		/**
		 * 환경 변수 파일을 설정한다.
		 * <p>
		 * 파일이 상대 경로이면 working directory를 기준으로 실행 시점에 해석된다.
		 * 파일이 존재하지 않으면 무시되며, 존재하지만 읽기에 실패하면 작업이 fail-fast 된다
		 * ({@link ExecutionException}). {@link #environmentVariables(Map)}로 명시된 변수가 우선하며,
		 * 파일은 누락된 키만 채운다.
		 *
		 * @param envFile	환경 변수 파일 (nullable)
		 * @return	본 빌더 객체
		 */
		public Builder environmentFile(@Nullable File envFile) {
			m_envFile = envFile;
			return this;
		}

		/**
		 * Command line 변수 치환에 사용할 {@link CommandVariable}을 등록한다.
		 * <p>
		 * 이미 같은 이름의 변수가 등록되어 있으면 새 변수가 기존 변수를 대체한다.
		 * 기존 변수를 보존하려면 {@link #addVariableIfAbsent(CommandVariable)}을 사용한다.
		 *
		 * @param var	등록할 변수 (non-null)
		 * @return	본 빌더 객체
		 * @throws IllegalArgumentException	{@code var}가 {@code null}인 경우
		 */
		public Builder addVariable(CommandVariable var) {
			Preconditions.checkNotNullArgument(var, "variable must not be null");

			m_variables.add(var);
			return this;
		}

		/**
		 * Command line 변수 치환에 사용할 {@link CommandVariable}을 등록한다 — 같은 이름의 변수가
		 * 이미 등록되어 있으면 무시한다.
		 * <p>
		 * 동작이 idempotent 하므로 여러 빌더 단계에서 기본 변수를 안전하게 등록할 때 유용하다.
		 *
		 * @param var	등록할 변수 (non-null)
		 * @return	본 빌더 객체
		 * @throws IllegalArgumentException	{@code var}가 {@code null}인 경우
		 */
		public Builder addVariableIfAbsent(CommandVariable var) {
			Preconditions.checkNotNullArgument(var, "variable must not be null");

			m_variables.addIfAbsent(var);
			return this;
		}
		
		/**
		 * stdin을 지정된 파일로 리다이렉트한다.
		 *
		 * @param file	stdin으로 리다이렉트할 파일. 존재해야 하며, 파일이 디렉토리거나 읽을 수 없는 경우
		 * 				예외가 발생한다. 파일이 상대 경로인 경우 working directory를 기준으로 실행 시점에
		 * 				해석된다.
		 * @return 본 빌더 객체. 본 메소드와 {@link #inheritStdin()} / {@link #discardStdin()}는
		 *         동일한 stdin 설정을 갱신하므로, 마지막 호출이 우선한다.
		 * @throws IllegalArgumentException	{@code file}이 {@code null}인 경우
		 */
		public Builder redirectStdinFromFile(File file) {
			Preconditions.checkNotNullArgument(file, "stdin file must not be null");

			m_stdinFile = file;
			m_stdin = null;
			return this;
		}
		
		/**
		 * stdin을 부모 프로세스의 stdin으로 리다이렉트한다.
		 *
		 * @return	본 빌더 객체.
		 *   		본 메소드와 {@link #redirectStdinFromFile(File)} / {@link #discardStdin()}는
		 *          동일한 stdin 설정을 갱신하므로, 마지막 호출이 우선한다.
		 */
		public Builder inheritStdin() {
			m_stdinFile = null;
			m_stdin = Redirect.INHERIT;
			return this;
		}
		
		/**
		 * stdin을 버린다 ({@link Redirect#DISCARD}).
		 * <p>
		 * 자식 프로세스가 stdin을 읽으려 하면 부모가 입력을 닫을 때까지 hang될 수 있으므로,
		 * stdin을 사용하지 않는 sub-process라면 본 메소드를 호출해 자식이 즉시 EOF를 받도록 하는 것이 안전하다.
		 *
		 * @return	본 빌더 객체.
		 *    		본 메소드와 {@link #redirectStdinFromFile(File)} / {@link #inheritStdin()}는
		 *			동일한 stdin 설정을 갱신하므로, 마지막 호출이 우선한다.
		 */
		public Builder discardStdin() {
			m_stdinFile = null;
			m_stdin = Redirect.DISCARD;
			return this;
		}
		
		/**
		 * stdout를 지정된 파일로 리다이렉트한다.
		 *
		 * @param file stdout로 리다이렉트할 파일. 존재하지 않으면 생성된다. 파일이 이미 존재하면
		 * 			기존 내용을 덮어쓴다. 파일이 디렉토리거나 생성할 수 없는 경우 예외가 발생한다.
		 * 			파일이 상대 경로인 경우 working directory를 기준으로 실행 시점에 해석된다.
		 * @return	본 빌더 객체.
		 * 			본 메소드와 {@link #inheritStdout()} / {@link #discardStdout()}는
		 *         	동일한 stdout 설정을 갱신하므로, 마지막 호출이 우선한다.
		 * @throws IllegalArgumentException	{@code file}이 {@code null}인 경우
		 */
		public Builder redirectStdoutToFile(File file) {
			Preconditions.checkNotNullArgument(file, "stdout file must not be null");

			m_stdoutFile = file;
			m_stdout = null;
			return this;
		}
		
		/**
		 * stdout를 부모 프로세스의 stdout으로 리다이렉트한다.
		 *
		 * @return 본 빌더 객체
		 *      본 메소드와 {@link #redirectStdoutToFile(File)} / {@link #discardStdout()}는
		 *      동일한 stdout 설정을 갱신하므로, 마지막 호출이 우선한다.
		 */
		public Builder inheritStdout() {
			m_stdoutFile = null;
			m_stdout = Redirect.INHERIT;
			return this;
		}
		
		/**
		 * stdout를 버린다.
		 *
		 * @return	본 빌더 객체.
		 *     		본 메소드와 {@link #redirectStdoutToFile(File)} / {@link #inheritStdout()}는
		 *     		동일한 stdout 설정을 갱신하므로, 마지막 호출이 우선한다.
		 */
		public Builder discardStdout() {
			m_stdoutFile = null;
			m_stdout = Redirect.DISCARD;
			return this;
		}
		
		/**
		 * stderr를 stdout으로 합치도록 설정한다 ({@link ProcessBuilder#redirectErrorStream(boolean)}와
		 * 동일).
		 *
		 * @return	본 빌더 객체.
		 * 			본 메소드와 {@link #redirectStderrToFile(File)} / {@link #inheritStderr()} /
		 * 			{@link #discardStderr()}는 동일한 stderr 설정을 갱신하므로, 마지막 호출이 우선한다.
		 */
		public Builder redirectErrorStream() {
			m_stderrFile = null;
			m_stderr = null;
			return this;
		}

		/**
		 * stderr를 지정된 파일로 리다이렉트한다.
		 * <p>
		 * 파일이 상대 경로인 경우 working directory를 기준으로 실행 시점에 해석된다.
		 *
		 * @param file	stderr를 리다이렉트할 파일.
		 * @return	본 빌더 객체.
		 * 			본 메소드와 {@link #redirectErrorStream()} / {@link #inheritStderr()} /
		 * 			{@link #discardStderr()}는 동일한 stderr 설정을 갱신하므로, 마지막 호출이 우선한다.
		 */
		public Builder redirectStderrToFile(File file) {
			Preconditions.checkNotNullArgument(file, "stderr file must not be null");

			m_stderrFile = file;
			m_stderr = null;
			return this;
		}

		/**
		 * stderr를 부모 프로세스의 stderr로 리다이렉트한다.
		 *
		 * @return	본 빌더 객체.
		 * 			본 메소드와 {@link #redirectErrorStream()} / {@link #redirectStderrToFile(File)} /
		 * 			{@link #discardStderr()}는 동일한 stderr 설정을 갱신하므로, 마지막 호출이 우선한다.
		 */
		public Builder inheritStderr() {
			m_stderrFile = null;
			m_stderr = Redirect.INHERIT;
			return this;
		}

		/**
		 * stderr를 버린다 ({@link Redirect#DISCARD}).
		 *
		 * @return	본 빌더 객체.
		 * 			본 메소드와 {@link #redirectErrorStream()} / {@link #redirectStderrToFile(File)} /
		 * 			{@link #inheritStderr()}는 동일한 stderr 설정을 갱신하므로, 마지막 호출이 우선한다.
		 */
		public Builder discardStderr() {
			m_stderrFile = null;
			m_stderr = Redirect.DISCARD;
			return this;
		}
		
		/**
		 * Sub-process의 실행 제한 시간을 설정한다.
		 * <p>
		 * 제한 시간 안에 sub-process가 종료되지 않으면 작업은 {@link TimeoutException}으로 실패하며
		 * sub-process와 descendants는 강제 종료된다. {@code null}을 전달하면 무제한 대기가 된다 (기본값).
		 * <p>
		 * 0 이하 또는 1ms 미만의 양수 duration은 거부된다 — 내부적으로 millisecond 단위 wait API에
		 * 전달되므로 의도와 다르게 즉시 timeout 처리되는 것을 방지하기 위함이다.
		 *
		 * @param timeout	제한 시간. {@code null}이면 무제한.
		 * @return	본 빌더 객체
		 * @throws IllegalArgumentException	{@code timeout}이 양수가 아니거나 {@code 1ms} 미만인 경우
		 */
		public Builder timeout(Duration timeout) {
			// toMillis() > 0 검증: sub-ms positive duration은 process.waitFor(0, MS)에서
			// 즉시 timeout으로 처리되어 의도와 다른 동작을 하므로 거부한다.
			Preconditions.checkArgument(timeout == null
									|| (DurationUtils.isPositive(timeout) && timeout.toMillis() > 0),
									"invalid timeout: %s", timeout);

			m_timeout = timeout;
			return this;
		}
	}
	
	private static final class CommandVariableLookup implements StringLookup {
		private final Map<String,CommandVariable> m_varMap;
		
		private CommandVariableLookup(Map<String,CommandVariable> vars) {
			m_varMap = vars;
		}

		// commons-text 1.14.0에서 StringLookup#lookup()이 @Deprecated 되었으나 여전히 SAM이라 구현 필수.
		// override 자체를 @Deprecated로 표시하면 javac/IDE 둘 다 추가 경고 없이 받아들인다.
		@Override
		@Deprecated
		public String lookup(String key) {
			// 변수 치환 과정에서 사용자가 사용하는 key는 '<변수 이름>:<modifier>' 형태를 갖는다.
			// 이때 '<modifier>'는 생략될 수 있으며 이때는 modifier.name으로 간주한다.
			// 만일 modifier.path를 사용하는 경우에는 변수 값이 저장된 파일의 경로를 의미하며
			// 해당 경로 값을 알기 위해 변수 값을 갖는 파일을 생성한다.
			
			Split split = Split.split(key, ":");

			// 변수 이름에 해당하는 CommandVariable 객체를 획득한다.
			CommandVariable commandVar = m_varMap.get(split.head());
			if ( commandVar == null ) {
				return null;
			}
			else {
				// Modifier 객체를 얻고, 이를 통한 variable replacement를 시도한다.
				return commandVar.getValueByModifier(split.tail().orElse("name"));
			}
		}
	}
}
