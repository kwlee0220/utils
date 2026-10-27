package utils.async.command;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
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
import utils.RuntimeInterruptedException;
import utils.Split;
import utils.StrSubstitutor;
import utils.async.AbstractThreadedExecution;
import utils.async.AsyncState;
import utils.async.CancellableWork;
import utils.async.Executions;
import utils.func.Optionals;
import utils.io.EnvironmentFileLoader;
import utils.io.FileUtils;
import utils.stream.FStream;


/**
 * 주어진 명령어 프로그램을 sub-process를 통해 실행시키는 비동기 작업.
 * <p>
 * {@link Builder}로 생성하며, 실행은 부모 클래스 {@link AbstractThreadedExecution}의 계약을 따른다.
 *
 * <h2>변수 치환</h2>
 * Command line의 각 argument에는 두 단계의 변수 치환이 순차로 적용된다:
 * <ol>
 *   <li>일반 변수 치환 — 예약어 {@code ${WORKING_DIR}} 등 미리 정의된 시스템 변수를 치환한다.</li>
 *   <li>명령 변수 치환 — {@link Builder#addVariable(CommandVariable)}로 등록된
 *       {@link CommandVariable}을 {@code ${name}} 또는 {@code ${name:modifier}} 형태로 치환한다.</li>
 * </ol>
 * 1단계에서 매칭되지 않은 {@code ${X}}는 그대로 2단계로 넘어가지만, 2단계에서도 매칭되지 않으면
 * {@link IllegalArgumentException}이 발생하여 sub-process가 실행되지 않고 작업이 실패한다.
 * 변수 이름 오타가 원본 토큰 그대로 sub-process에 전달되어 조용히 잘못 동작하는 것을 막기 위함이다.
 * <p>
 * 2단계에서 치환된 변수 값은 다시 치환하지 않는다. 즉 값에 {@code ${...}}가 들어 있으면 그대로
 * sub-process에 전달되며, 다른 변수 값으로 바뀌거나 미정의 변수로 실패하지 않는다.
 *
 * <h2>표준 입출력</h2>
 * {@link Builder#stdin(Input)}, {@link Builder#stdout(Output)}, {@link Builder#stderr(Output)}에 각 스트림의
 * 연결 방식을 값 객체({@link Input}, {@link Output})로 지정한다. 같은 스트림을 다시 지정하면 마지막 값이 쓰인다.
 * <pre>{@code
 * CommandExecution.builder()
 *     .addCommand(...)
 *     .stdin(Input.file(inFile))
 *     .stdout(Output.file(logFile).append())
 *     .stderr(Output.toStdout())          // stderr를 stdout에 합침
 *     .build();
 * }</pre>
 * 기본값은 다음과 같다:
 * <ul>
 *   <li>{@code stdin} — {@link Input#none()} (OS의 null device). 자식 프로세스가 stdin을 읽으면 즉시 EOF를
 *       받는다. 본 클래스는 {@link Process}를 노출하지 않아 호출자가 pipe에 쓸 방법이 없으므로,
 *       ProcessBuilder의 기본값인 {@link Redirect#PIPE PIPE}를 쓰면 stdin을 읽는 자식이 영원히 멈추게 된다.</li>
 *   <li>{@code stdout} — {@link Output#discard()}.</li>
 *   <li>{@code stderr} — {@link Output#discard()}.</li>
 * </ul>
 * 파일의 상대 경로는 working directory를 기준으로 실행 시점에 해석된다.
 *
 * <h2>종료 코드 처리</h2>
 * Sub-process 종료 코드가 성공 코드(기본 {@code 0}, {@link Builder#successExitCodes(int...)}로 변경)이면
 * 정상 완료로 간주하고, 그 외 값이면 작업 실패로 보아 {@link CommandFailedException}({@link ExecutionException}의
 * 하위 타입)을 던진다. 정상 완료된 경우에도 실제 종료 코드는 {@link #getExitCode()}로 얻을 수 있다. 이 예외로 종료 코드와,
 * stderr(또는 stderr가 합쳐진 stdout)가 파일로 redirect된 경우 그 파일의 마지막 부분을 얻을 수 있다.
 *
 * <h2>환경 변수 파일</h2>
 * {@link Builder#environmentFile(File)}로 지정된 환경 변수 파일이 존재하지만 읽기에 실패하면
 * {@link ExecutionException}으로 fail-fast된다. 파일이 존재하지 않으면 무시된다.
 *
 * <h2>Timeout / Cancel</h2>
 * {@link Builder#timeout(Duration)}이 설정되어 있고 그 안에 종료되지 않으면
 * {@link TimeoutException}이 발생한다. {@link AbstractThreadedExecution#cancel(boolean) cancel(true)}
 * 호출 시 sub-process와 descendants는 SIGTERM으로 종료되고 작업은 취소 상태가 된다.
 * 종료 요청과 grace 대기는 별도 쓰레드에서 수행되므로 {@code cancel(true)}는 프로세스 종료를 기다리지 않고
 * 반환한다. 어떤 경로로 종료되든 sub-process와 descendants가 leak되지 않도록 finally에서 정리된다
 * (SIGTERM 후 1초 grace, 이후 SIGKILL).
 * <p>
 * <b>descendants 정리는 최선 노력(best-effort)이다.</b> descendants는 {@link ProcessHandle}의 부모·자식 관계를
 * 주기적으로 조회하여 추적하므로, 조회 사이에 자식을 만들고 곧바로 종료하는 부모(예: daemon화를 위해 fork 후
 * 종료하는 프로그램)의 자식은 부모와의 관계가 끊겨 추적되지 않고 남을 수 있다. 확실한 정리가 필요하면 명령이
 * 스스로 자식을 정리하도록 하거나, OS의 프로세스 그룹·작업 객체(job object)를 쓰는 방식으로 감싸야 한다.
 *
 * <h2>로그</h2>
 * 변수 값에는 비밀값이 들어 있을 수 있으므로 변수가 치환된 command line은 로그에 남기지 않는다.
 * INFO에는 실행 파일과 working directory만, DEBUG에는 치환 전 command line을 남긴다.
 *
 * <h2>{@link #close()} 시맨틱</h2>
 * {@link Closeable}이 호출되면 진행 중인 작업을 취소하고 완료를 기다린 뒤, 등록된 모든
 * {@link CommandVariable}을 close한다. {@code try-with-resources} 사용 시 sub-process leak 없이
 * 정리된다. 작업이 {@code STARTING} 상태에 cancel timeout 이상 머물러 첫 취소가 실패하면
 * (예: Executor 큐에서 시작이 지연된 경우), 시작 단계가 끝나기를 기다린 뒤 다시 취소한다.
 * 따라서 Executor가 작업을 실행하지 않는 동안에는 {@code close()}도 대기한다.
 * 대기 중 인터럽트되면 인터럽트 상태를 복원하고 즉시 변수 정리로 넘어간다.
 * <p>
 * 작업이 이미 종료 상태여도(예: 초기화 도중 외부에서 {@code notifyFailed()}로 종료를 통지한 경우) 작업 쓰레드가
 * 아직 프로세스를 쥐고 있을 수 있으므로, {@code close()}는 남은 프로세스를 종료시키고 작업 쓰레드가 프로세스
 * 정리를 마칠 때까지 기다린 뒤 반환한다. 즉 상태의 종료와 자원 정리 완료를 구분하여, 후자까지 보장한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandExecution extends AbstractThreadedExecution<Void> implements CancellableWork, Closeable {
	private static final Logger s_logger = LoggerFactory.getLogger(CommandExecution.class);
	private static final Duration PROCESS_TERMINATION_GRACE = Duration.ofSeconds(1);
	private static final Duration PROCESS_TREE_REFRESH_INTERVAL = Duration.ofMillis(50);
	/** 실패 시 {@link CommandFailedException}에 담는 출력 파일 끝부분의 최대 크기(byte). */
	private static final int OUTPUT_TAIL_BYTES = 4096;
	/** OS의 null device를 읽는 stdin redirect. ({@link Redirect#DISCARD}는 출력 전용이라 stdin에 쓸 수 없다.) */
	private static final Redirect NULL_INPUT = Redirect.from(Redirect.DISCARD.file());

	/**
	 * Sub-process의 stdin 연결 방식.
	 *
	 * @see Builder#stdin(Input)
	 */
	public sealed interface Input permits Input.None, Input.Inherit, Input.FromFile {
		/**
		 * OS의 null device({@code /dev/null}, Windows는 {@code NUL})에 연결한다. 자식 프로세스는 읽기 시도 시
		 * 즉시 EOF를 받는다. 기본값이다.
		 *
		 * @return	null device 입력.
		 */
		public static Input none() {
			return None.INSTANCE;
		}

		/**
		 * 부모 프로세스의 stdin을 물려준다.
		 *
		 * @return	상속 입력.
		 */
		public static Input inherit() {
			return Inherit.INSTANCE;
		}

		/**
		 * 지정된 파일을 stdin으로 연결한다. 파일은 실행 시점에 존재해야 하며, 없으면 작업이
		 * {@link IllegalArgumentException}으로 실패한다.
		 *
		 * @param file	입력 파일. 상대 경로이면 working directory 기준으로 실행 시점에 해석된다.
		 * @return	파일 입력.
		 * @throws IllegalArgumentException	{@code file}이 {@code null}인 경우.
		 */
		public static Input file(File file) {
			return new FromFile(file);
		}

		/** {@link #none()}. */
		public enum None implements Input { INSTANCE }
		/** {@link #inherit()}. */
		public enum Inherit implements Input { INSTANCE }
		/** {@link #file(File)}. */
		public record FromFile(File file) implements Input {
			public FromFile {
				Preconditions.checkNotNullArgument(file, "stdin file must not be null");
			}
		}
	}

	/**
	 * Sub-process의 stdout/stderr 연결 방식.
	 *
	 * @see Builder#stdout(Output)
	 * @see Builder#stderr(Output)
	 */
	public sealed interface Output permits Output.Discard, Output.Inherit, Output.ToFile, Output.ToStdout {
		/**
		 * 출력을 버린다. 기본값이다.
		 *
		 * @return	버리는 출력.
		 */
		public static Output discard() {
			return Discard.INSTANCE;
		}

		/**
		 * 부모 프로세스의 같은 스트림(stdout 또는 stderr)을 물려준다.
		 *
		 * @return	상속 출력.
		 */
		public static Output inherit() {
			return Inherit.INSTANCE;
		}

		/**
		 * 지정된 파일에 기록한다. 파일이 이미 있으면 덮어쓰며, 이어 쓰려면 {@link ToFile#append()}를 붙인다.
		 *
		 * @param file	출력 파일. 없으면 생성된다. 상대 경로이면 working directory 기준으로 실행 시점에 해석된다.
		 * @return	파일 출력 (덮어쓰기).
		 * @throws IllegalArgumentException	{@code file}이 {@code null}인 경우.
		 */
		public static ToFile file(File file) {
			return new ToFile(file, false);
		}

		/**
		 * stderr를 stdout에 합친다 ({@link ProcessBuilder#redirectErrorStream(boolean)}). stderr에만 지정할 수 있다.
		 *
		 * @return	stdout으로 합치는 출력.
		 */
		public static Output toStdout() {
			return ToStdout.INSTANCE;
		}

		/** {@link #discard()}. */
		public enum Discard implements Output { INSTANCE }
		/** {@link #inherit()}. */
		public enum Inherit implements Output { INSTANCE }
		/** {@link #toStdout()}. */
		public enum ToStdout implements Output { INSTANCE }
		/**
		 * {@link #file(File)}.
		 *
		 * @param file		출력 파일.
		 * @param appending	{@code true}이면 기존 내용 뒤에 이어 쓰고, {@code false}이면 덮어쓴다.
		 */
		public record ToFile(File file, boolean appending) implements Output {
			public ToFile {
				Preconditions.checkNotNullArgument(file, "output file must not be null");
			}

			/**
			 * 기존 내용을 지우지 않고 파일 뒤에 이어 쓰도록 한다.
			 *
			 * @return	이어 쓰는 파일 출력.
			 */
			public ToFile append() {
				return new ToFile(file, true);
			}
		}
	}

	private final List<String> m_command;
	private final File m_workingDirectory;
	private final @Nullable File m_envFile;
	private final Map<String,String> m_environmentVariables;
	private final Map<String,CommandVariable> m_variables;
	private final Input m_stdin;
	private final Output m_stdout;
	private final Output m_stderr;
	private final @Nullable Duration m_timeout;
	private final Set<Integer> m_successExitCodes;
	
	@GuardedBy("m_aopGuard") private Process m_process;
	@GuardedBy("m_aopGuard") private ProcessTree m_processTree;
	// 작업 쓰레드가 프로세스를 다루는 중인지 여부. initializeThread() 진입 시 true가 되고, 프로세스 정리를 마치면
	// (executeWork()의 finally, abortAfterInitialize(), 초기화 실패) false가 된다. close()는 이것이 false가 되기를 기다린다.
	@GuardedBy("m_aopGuard") private boolean m_workerActive = false;
	@GuardedBy("m_aopGuard") private boolean m_closeRequested = false;
	private volatile @Nullable Integer m_exitCode = null;
	
	private CommandExecution(Builder builder) {
		Preconditions.checkArgument(!builder.m_command.isEmpty(), "command must not be empty");

		// 방어적 복사: 빌드 후 호출자가 Builder 참조나 원본 컬렉션을 mutate해도 영향 없도록 한다.
		m_command = List.copyOf(builder.m_command);
		m_envFile = builder.m_envFile;
		m_environmentVariables = Map.copyOf(builder.m_environmentVariables);
		m_variables = Collections.unmodifiableMap(builder.m_variables.toMap());
		m_stdin = builder.m_stdin;
		m_stdout = builder.m_stdout;
		m_stderr = builder.m_stderr;
		m_timeout = builder.m_timeout;
		m_successExitCodes = builder.m_successExitCodes;
		
		// Builder.workingDirectory()에서 isDirectory 검증을 수행하므로 여기서는 fallback만 처리한다.
		m_workingDirectory = Optionals.getOrElse(builder.m_workingDirectory, FileUtils::getCurrentWorkingDirectory);

		setLogger(s_logger);
	}
	
	@Override
	public void close() {
		// 이후 작업 쓰레드가 초기화에 들어오면 프로세스를 만들지 않도록 한다.
		m_aopGuard.run(() -> m_closeRequested = true);
		try {
			// 실행 중이면 취소를 요청하고 종료를 기다려서 sub-process가 leak되지 않도록 한다.
			if ( !isDone() ) {
				if ( !cancel(true) && !isDone() ) {
					// STARTING 상태가 cancel timeout을 넘기면 cancel(true)는 false를 반환한다
					// (예: Executor 큐에서 시작이 지연된 경우). 이대로 종료를 기다리면 명령이
					// 스스로 끝날 때까지 막히므로, 시작 단계가 끝나기를 기다린 뒤 다시 취소한다.
					waitWhileStarting();
					cancel(true);
				}
				waitForFinished();
			}

			// 종료 상태가 되었어도 작업 쓰레드가 아직 프로세스를 쥐고 있을 수 있다(예: 외부에서 notifyFailed()로
			// 종료를 통지한 경우). 남은 프로세스를 직접 종료시키고, 작업 쓰레드가 정리를 마칠 때까지 기다린다.
			ProcessTree remaining = m_aopGuard.get(() -> m_processTree);
			if ( remaining != null ) {
				remaining.terminate(PROCESS_TERMINATION_GRACE);
			}
			m_aopGuard.awaitCondition(() -> !m_workerActive).andReturn();
		}
		catch ( InterruptedException | RuntimeInterruptedException e ) {
			// 'close()' 함수의 signature에 InterruptedException이 없으므로,
			// 현재 스레드의 인터럽트 상태를 복원한다. (cancel()은 대기 중 인터럽트되면
			// RuntimeInterruptedException을 던진다.)
			Thread.currentThread().interrupt();
		}
		finally {
			// 등록된 모든 변수를 close 시킨다.
			FStream.from(m_variables.values())
					.forEachOrIgnore(CommandVariable::close);
		}
	}
	
	/**
	 * Command 실행에서 사용 중인 Command variable 맵을 반환한다.
	 * <p>
	 * 읽기 전용이다. 변수를 바깥에서 제거하면 {@link #close()}의 정리 대상에서 빠지므로 수정을 허용하지 않는다.
	 * 
	 * @return	Command variable 맵 (수정 불가)
	 */
	public Map<String,CommandVariable> getVariableMap() {
		return m_variables;
	}

	/**
	 * Sub-process의 종료 코드를 반환한다.
	 * <p>
	 * 성공 코드가 여러 개인 명령(예: {@code grep}의 0=찾음, 1=못 찾음)은 정상 완료 후 본 메소드로 어떤 코드로
	 * 끝났는지 구분한다. 실패한 경우에는 {@link CommandFailedException#getExitCode()}로도 얻을 수 있다.
	 *
	 * @return	종료 코드. 프로세스가 아직 종료되지 않았거나, 종료 코드를 얻기 전에 timeout/취소로 끝난 경우에는
	 * 			빈 값.
	 */
	public OptionalInt getExitCode() {
		Integer code = m_exitCode;
		return (code != null) ? OptionalInt.of(code) : OptionalInt.empty();
	}
	
	@Override
	protected void initializeThread() throws Exception {
		// AsyncState.STARTING 상태.
		// cancel하려고 해도 취소 작업이 대기 중인 상태.
		boolean closed = m_aopGuard.get(() -> {
			if ( m_closeRequested ) {
				return true;
			}
			m_workerActive = true;
			return false;
		});
		if ( closed ) {
			throw new CancellationException("Command execution is closed");
		}

		Process process = null;
		try {
			ProcessBuilder builder = buildProcessBuilder();
			process = builder.start();
			ProcessTree processTree = ProcessTree.of(process);
			processTree.refresh();   // root가 종료되기 전 descendants 초기 snapshot을 시도한다.
			Process started = process;
			m_aopGuard.runChecked(() -> {
				m_process = started;
				m_processTree = processTree;
			});
		}
		catch ( Exception e ) {
			// 프로세스를 만든 뒤 초기화가 실패하면 프로세스가 남지 않도록 정리한다.
			if ( process != null ) {
				ProcessTree.of(process).terminate(PROCESS_TERMINATION_GRACE);
			}
			releaseProcess();
			throw e;
		}
	}

	/**
	 * 초기화는 끝났지만 그 사이 외부에서 종료(실패·취소)되어 {@link #executeWork()}가 호출되지 않는 경우에
	 * 시작된 프로세스를 정리한다.
	 */
	@Override
	protected void abortAfterInitialize() {
		ProcessTree processTree = m_aopGuard.get(() -> m_processTree);
		try {
			if ( processTree != null ) {
				processTree.terminate(PROCESS_TERMINATION_GRACE);
			}
		}
		finally {
			releaseProcess();
		}
	}

	private void releaseProcess() {
		m_aopGuard.run(() -> {
			m_process = null;
			m_processTree = null;
			m_workerActive = false;
		});
	}

	@Override
	protected Void executeWork() throws InterruptedException, CancellationException, TimeoutException, ExecutionException {
		// (대부분의 경우) AsyncState.RUNNING 상태.
		// (드믄 경우) cancel이 호출된 상태라면 취소 작업이 대기 중인 상태.
		Process process = m_aopGuard.get(() -> m_process);
		ProcessTree processTree = m_aopGuard.get(() -> m_processTree);
		try {
			if ( process == null || processTree == null ) {
				// 연산이 중단(취소)된 상태.
				throw new CancellationException("Command execution is cancelled");
			}

			// 제한시간이 설정된 경우에는 제한시간만큼만, 그렇지 않으면 종료될 때까지 프로세스를 대기한다.
			Duration timeout = m_timeout;
			boolean terminated;
			if ( timeout != null ) {
				terminated = processTree.waitForRootTerminated(PROCESS_TREE_REFRESH_INTERVAL, timeout);
			}
			else {
				processTree.waitForRootTerminated(PROCESS_TREE_REFRESH_INTERVAL);
				terminated = true;
			}

			// 사용자의 강제 종료를 통해 프로그램이 종료되었을 수도 있기 때문에
			// 이를 확인하여 강제 종료된 경우 CancellationException을 발생시킨다.
			throwCancellationIfCancelRequested();

			if ( !terminated ) {
				// 제한시간이 경과한 경우. 프로세스 종료는 finally의 terminate가 담당한다.
				throw new TimeoutException(timeout.toString());
			}

			int retCode = process.exitValue();
			m_exitCode = retCode;
			getLogger().info("Process terminated: retCode={}", retCode);
			if ( !m_successExitCodes.contains(retCode) ) {
				// 반환 코드가 성공 코드가 아닌 경우는 수행 실패로 간주한다.
				throw new CommandFailedException(retCode, readOutputTail());
			}
			return null;
		}
		finally {
			// 인터럽트/timeout/예외 등 어떤 경로로 빠져나가든 sub-process가 leak되지 않도록 정리한다.
			// 정리는 종료 상태 전이보다 먼저 끝나므로, 종료 통지를 받은 쪽은 프로세스가 정리된 뒤에 깨어난다.
			if ( processTree != null ) {
				processTree.terminate(PROCESS_TERMINATION_GRACE);
			}
			// defunct process 참조를 정리해 cancelWork가 죽은 프로세스에 destroy를 시도하지 않도록 하고,
			// close()에 정리가 끝났음을 알린다.
			releaseProcess();
		}
	}

	@Override
	public boolean cancelWork() {
		// 프로세스가 시작되어 아직 살아있는 경우에만 종료시킨다. ProcessTree.terminate()는 grace 동안
		// 기다리므로, cancel(true)를 호출한 쓰레드(예: Guava의 lock을 쥔 ProgramService.doStop())를
		// 붙잡지 않도록 별도 쓰레드에서 수행한다. SIGTERM을 받은 root가 종료되면 executeWork()가 깨어나
		// finally에서 남은 descendants를 정리한다.
		ProcessTree processTree = m_aopGuard.get(() -> m_processTree);
		if ( processTree != null ) {
			CompletableFuture.runAsync(() -> processTree.terminate(PROCESS_TERMINATION_GRACE),
										Executions.getExecutor());
		}
		return true;
	}

	/**
	 * 실패 원인 파악을 위해 출력 파일의 마지막 부분을 읽는다.
	 * <p>
	 * stderr가 파일로 redirect되었으면 그 파일을, 아니면 stdout 파일(stderr가 합쳐졌을 수 있음)을 읽는다.
	 * 출력이 파일로 redirect되지 않았거나 읽지 못하면 {@code null}을 반환한다. stdout을 append 모드로
	 * 기록하는 경우에는 이전 실행의 출력이 포함될 수 있다.
	 */
	private @Nullable String readOutputTail() {
		File file;
		if ( m_stderr instanceof Output.ToFile err ) {
			file = err.file();
		}
		else if ( m_stdout instanceof Output.ToFile out ) {
			file = out.file();
		}
		else {
			return null;
		}
		file = resolveAgainstWorkingDirectory(file);
		try ( RandomAccessFile raf = new RandomAccessFile(file, "r") ) {
			long length = raf.length();
			long start = Math.max(0, length - OUTPUT_TAIL_BYTES);
			byte[] buf = new byte[(int)(length - start)];
			raf.seek(start);
			raf.readFully(buf);
			String tail = new String(buf, StandardCharsets.UTF_8);
			if ( start > 0 ) {
				// 중간부터 읽은 경우 잘린 첫 줄은 버린다.
				int nl = tail.indexOf('\n');
				tail = (nl >= 0) ? tail.substring(nl + 1) : tail;
			}
			return tail.strip();
		}
		catch ( IOException e ) {
			getLogger().debug("failed to read output tail: file={}, cause={}", file, ""+e);
			return null;
		}
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
		// 이 단계가 마지막이므로 여기서도 매칭되지 않은 ${X}는 예외로 처리한다. 원본 토큰을
		// 그대로 넘기면 변수 이름 오타를 sub-process가 조용히 삼키게 된다.
		// 치환된 변수 값은 데이터이므로 그 안의 ${...}는 다시 치환하지 않는다. 재치환하면 값에 들어 있는
		// ${other:value}가 다른 변수 값으로 바뀌고(값 노출), 값의 ${X}가 미정의 변수 예외로 실패한다.
		CommandVariableLookup lut = new CommandVariableLookup(m_variables);
		StringSubstitutor cmdVarSubst = new StringSubstitutor(lut)
												.setEnableUndefinedVariableException(true)
												.setDisableSubstitutionInValues(true);
		
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
		// 변수 값(비밀값일 수 있음)이 치환된 command line은 로그에 남기지 않는다.
		if ( getLogger().isInfoEnabled() ) {
			getLogger().info("starting command: program={}, workingDir={}",
							command.get(0), m_workingDirectory.getAbsolutePath());
		}
		if ( getLogger().isDebugEnabled() ) {
			getLogger().debug("command line (before variable substitution): {}", FStream.from(m_command).join(' '));
		}
		
		// 표준 입출력 연결
		builder.redirectInput(toRedirect(m_stdin));
		builder.redirectOutput(toRedirect(m_stdout));
		if ( m_stderr == Output.ToStdout.INSTANCE ) {
			builder.redirectErrorStream(true);
		}
		else {
			builder.redirectError(toRedirect(m_stderr));
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

	private Redirect toRedirect(Input input) {
		if ( input instanceof Input.FromFile in ) {
			File file = resolveAgainstWorkingDirectory(in.file());
			Preconditions.checkArgument(file.isFile(), "stdin file does not exist: %s", file);
			return Redirect.from(file);
		}
		else if ( input == Input.Inherit.INSTANCE ) {
			return Redirect.INHERIT;
		}
		else {
			return NULL_INPUT;
		}
	}

	private Redirect toRedirect(Output output) {
		if ( output instanceof Output.ToFile out ) {
			File file = resolveAgainstWorkingDirectory(out.file());
			return out.appending() ? Redirect.appendTo(file) : Redirect.to(file);
		}
		else if ( output == Output.Inherit.INSTANCE ) {
			return Redirect.INHERIT;
		}
		else if ( output == Output.Discard.INSTANCE ) {
			return Redirect.DISCARD;
		}
		else {
			throw new IllegalStateException("unexpected output: " + output);
		}
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
		private Set<Integer> m_successExitCodes = Set.of(0);
		private Input m_stdin = Input.none();
		private Output m_stdout = Output.discard();
		private Output m_stderr = Output.discard();
		
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
		 * 이미 같은 이름의 변수가 등록되어 있으면 {@link IllegalArgumentException}이 발생하며,
		 * 기존 변수는 그대로 유지된다. 대체를 허용하지 않는 이유는, 대체된 변수가 {@link CommandExecution#close()}
		 * 대상에서 빠져 파일 기반 변수의 자원이 정리되지 않고, 같은 이름의 입력 값이 조용히 사라질 수
		 * 있기 때문이다. 이미 등록된 경우를 무시하려면 {@link #addVariableIfAbsent(CommandVariable)}을,
		 * 등록 여부를 미리 확인하려면 {@link #hasVariable(String)}을 사용한다.
		 *
		 * @param var	등록할 변수 (non-null)
		 * @return	본 빌더 객체
		 * @throws IllegalArgumentException	{@code var}가 {@code null}이거나, 같은 이름의 변수가 이미
		 * 									등록되어 있는 경우
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
		
		public boolean hasVariable(String varId) {
			return m_variables.containsKey(varId);
		}
		
		/**
		 * stdin 연결 방식을 지정한다. 기본값은 {@link Input#none()}이다.
		 *
		 * @param input	stdin 연결 방식.
		 * @return	본 빌더 객체.
		 * @throws IllegalArgumentException	{@code input}이 {@code null}인 경우.
		 */
		public Builder stdin(Input input) {
			Preconditions.checkNotNullArgument(input, "stdin must not be null");

			m_stdin = input;
			return this;
		}

		/**
		 * stdout 연결 방식을 지정한다. 기본값은 {@link Output#discard()}이다.
		 *
		 * @param output	stdout 연결 방식. {@link Output#toStdout()}는 지정할 수 없다.
		 * @return	본 빌더 객체.
		 * @throws IllegalArgumentException	{@code output}이 {@code null}이거나 {@link Output#toStdout()}인 경우.
		 */
		public Builder stdout(Output output) {
			Preconditions.checkNotNullArgument(output, "stdout must not be null");
			Preconditions.checkArgument(output != Output.ToStdout.INSTANCE,
										"Output.toStdout() is only for stderr");

			m_stdout = output;
			return this;
		}

		/**
		 * stderr 연결 방식을 지정한다. 기본값은 {@link Output#discard()}이며, {@link Output#toStdout()}로
		 * stdout에 합칠 수 있다.
		 *
		 * @param output	stderr 연결 방식.
		 * @return	본 빌더 객체.
		 * @throws IllegalArgumentException	{@code output}이 {@code null}인 경우.
		 */
		public Builder stderr(Output output) {
			Preconditions.checkNotNullArgument(output, "stderr must not be null");

			m_stderr = output;
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

		/**
		 * 정상 완료로 간주할 종료 코드를 지정한다. 기본값은 {@code 0}뿐이다.
		 * <p>
		 * 예: {@code grep}은 1(못 찾음)도 정상 결과이므로 {@code successExitCodes(0, 1)}로 지정한다.
		 * 지정한 코드로 끝나면 작업은 COMPLETED가 되고, 실제 코드는 {@link CommandExecution#getExitCode()}로
		 * 얻는다. 그 외 코드로 끝나면 {@link CommandFailedException}으로 실패한다. 기존 설정을 대체하며 누적되지 않는다.
		 *
		 * @param codes	성공 종료 코드들. 하나 이상이어야 한다.
		 * @return	본 빌더 객체.
		 * @throws IllegalArgumentException	{@code codes}가 {@code null}이거나 비어 있는 경우.
		 */
		public Builder successExitCodes(int... codes) {
			Preconditions.checkNotNullArgument(codes, "success exit codes must not be null");
			Preconditions.checkArgument(codes.length > 0, "success exit codes must not be empty");

			m_successExitCodes = Arrays.stream(codes).boxed().collect(java.util.stream.Collectors.toUnmodifiableSet());
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
			
			// Modifier가 없는 경우에는 기본 modifier.name으로 간주한다.
			String modifier = split.tail().orElse("name");

			// 변수 이름에 해당하는 CommandVariable 객체를 획득한다.
			CommandVariable commandVar = m_varMap.get(split.head());
			if ( commandVar == null ) {
				throw new IllegalArgumentException("Command variable not found: " + split.head());
			}
			// Modifier 객체를 얻고, 이를 통한 variable replacement를 시도한다.
			return commandVar.getValueByModifier(modifier);
		}
	}
}
