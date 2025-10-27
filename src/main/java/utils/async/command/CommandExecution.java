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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import utils.KeyedValueList;
import utils.Tuple;
import utils.Utilities;
import utils.async.AbstractThreadedExecution;
import utils.async.AsyncState;
import utils.async.CancellableWork;
import utils.async.Guard;
import utils.func.FOption;
import utils.func.Unchecked;
import utils.io.EnvironmentFileLoader;
import utils.io.FileUtils;
import utils.stream.FStream;


/**
 * <code>CommandExecution</code>는 주어진 명령어 프로그램을 sub-process를 통해 실행시키는 작업을 수행한다.
 * <p>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandExecution extends AbstractThreadedExecution<Void> implements CancellableWork, Closeable {
	private static final Logger s_logger = LoggerFactory.getLogger(CommandExecution.class);

	private final List<String> m_command;
	private final File m_workingDirectory;
	private final File m_envFile;
	private final Map<String,String> m_environmentVariables;
	private final Map<String,CommandVariable> m_variables;
	private final Map<String,String> m_substVariables;
	private final @Nullable Redirect m_stdin;
	private final @Nullable Redirect m_stdout;
	private final @Nullable Redirect m_stderr;
	private final @Nullable Duration m_timeout;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private Process m_process;
	
	private CommandExecution(Builder builder) {
		m_command = builder.m_command;
		m_envFile = builder.m_envFile;
		m_environmentVariables = builder.m_environmentVariables;
		m_variables = builder.m_variables.toMap();
		m_stdin = builder.m_stdin;
		m_stdout = builder.m_stdout;
		m_stderr = builder.m_stderr;
		m_timeout = builder.m_timeout;
		
		m_workingDirectory = FOption.getOrElse(builder.m_workingDirectory, FileUtils.getCurrentWorkingDirectory());
		if ( !m_workingDirectory.isDirectory() ) {
			throw new IllegalArgumentException("Invalid working directory: " + m_workingDirectory);
		}
		m_substVariables = Map.of("WORKING_DIR", m_workingDirectory.getAbsolutePath());
		
		setLogger(s_logger);
	}
	
	@Override
	public void close() {
		// 등록된 모든 변수을 close 시킨다.
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
	public Void executeWork() throws InterruptedException, CancellationException,
										TimeoutException, ExecutionException {
		// Command line에 포함된 command variable들을 실제 값으로 치환시킨다.
		VariableLookup lut = new VariableLookup(m_variables);
		StringSubstitutor subst = new StringSubstitutor(lut).setEnableUndefinedVariableException(true);
		List<String> command = FStream.from(m_command)
										.map(str -> {
											str = Utilities.substributeString(str, m_substVariables);
											return subst.replace(str);
										})
										.toList();
		
		ProcessBuilder builder = new ProcessBuilder(command)
									.directory(m_workingDirectory);
		if ( getLogger().isInfoEnabled() ) {
			getLogger().info("WorkingDir: {}", m_workingDirectory);
			getLogger().info("Command (variable-replaced): {}", FStream.from(command).join(' '));
		}
		
		// Standard output 처리
		if ( m_stderr != null ) {
			builder.redirectError(m_stderr);
		}
		else {
			builder.redirectErrorStream(true);
		}
		// Standard error 처리
		if ( m_stdout != null ) {
			builder.redirectOutput(m_stdout);
		}

		// Standard input 처리
		if ( m_stdin != null ) {
			builder.redirectInput(m_stdin);
		}
		
		// 환경 변수 파일이 설정되면 해당 파일을 읽어 환경변수로 추가한다.
		Map<String,String> environments = Maps.newHashMap(m_environmentVariables);
		if ( m_envFile != null ) {
			File envFile = ( !m_envFile.isAbsolute() )
								? new File(m_workingDirectory, m_envFile.getPath())
								: m_envFile;
			getLogger().info("Loading environment file: {}", envFile.getAbsolutePath());
			try {
				for ( Map.Entry<String,String> ent: EnvironmentFileLoader.from(envFile).load().entrySet() ) {
					environments.putIfAbsent(ent.getKey(), ent.getValue());
				}
			}
			catch ( IOException ignored ) {
				getLogger().warn("failed to load environment file: {}", m_envFile.getAbsolutePath());
			}
		}
		
		// 환경변수 설정
		builder.environment().putAll(environments);
		
		try {
			Process process = m_guard.getChecked(() -> m_process = builder.start());
			if ( m_timeout != null ) {
				// 제한시간이 설정된 경우
				// 제한시간만큼나 프로세스를 대기한다.
				boolean completed = process.waitFor(m_timeout.toMillis(), TimeUnit.MILLISECONDS);
				
				// 사용자의 강제 종료를 통해 프로그램이 종료되었을 수도 있기 때문에
				// 이를 확인하여 강제 종료된 경우 CancellationException을 발생시킨다.
				checkAfterTerminated();
				
				if ( completed ) {
					// 제한시간 내에 마친 경우
					int retCode = process.exitValue();
					if ( retCode == 0 ) {
						// 프로그램 수행이 성공적으로 마친 경우
						return null;
					}
					else {
						// 프로그램 종료시 반환 코드가 0이 아닌 경우는 수행 실패로 간주한다.
						throw new ExecutionException("Process failed: retCode=" + retCode,
														new Exception("Program error code: " + retCode));
					}
				}
				else {
					// 제한시간이 경과한 경우
					// 프로세스는 살이 있기 때문에 (강제로) 종료시킨다.
					process.destroy();
					CompletableFuture.runAsync(() -> {
						// 1초가 경과된 이후에도 프로세스가 계속 살아있는 경우에는 
						// 강제로 종료시킨다.
						Unchecked.runOrIgnore(() -> Thread.sleep(1000));
						if ( process.isAlive() ) {
							process.destroyForcibly();
						}
					});
					throw new TimeoutException(m_timeout.toString());
				}
			}
			else {
				// 무제한 대기
				int retCode = process.waitFor();
				getLogger().info("Process terminated: retCode={}", retCode);
				
				// 사용자의 강제 종료를 통해 프로그램이 종료되었을 수도 있기 때문에
				// 이를 확인하여 강제 종료된 경우 CancellationException을 발생시킨다.
				checkAfterTerminated();
				
				if ( retCode == 0 || retCode == 143 ) {
					// 프로그램 수행이 성공적으로 마친 경우
					return null;
				}
				else {
					// 프로그램 종료시 반환 코드가 0이 아닌 경우는 수행 실패로 간주한다.
					throw new ExecutionException("Process failed: retCode=" + retCode,
													new Exception("Program error code: " + retCode));
				}
			}
		}
		catch ( IOException e ) {
			throw new ExecutionException("Failed to start process: command-line: " + m_command, e);
		}
	}

	@Override
	public boolean cancelWork() {
		return m_guard.get(() -> {
			// 프로그램이 이미 시작된 경우에는 강제로 종료시킨다.
			if ( m_process != null ) {
				if ( getLogger().isInfoEnabled() ) {
					getLogger().debug("killing process: pid={}", m_process.toHandle().pid());
				}
				m_process.destroy();
			}
			return true;
		});
	}
	
	@Override
	public String toString() {
		String cmdLine = FStream.from(m_command).join(' ');
		return String.format("%s", cmdLine);
	}
	
	private void checkAfterTerminated() throws CancellationException {
		if ( getState() == AsyncState.CANCELLED ) {
			getLogger().info("Process is killed by user");
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
		private File m_envFile;
		private KeyedValueList<String,CommandVariable> m_variables = KeyedValueList.with(CommandVariable::getName);
		private Duration m_timeout;
		private Redirect m_stdin;
		private Redirect m_stdout = Redirect.DISCARD;
		private Redirect m_stderr = Redirect.DISCARD;
		
		public CommandExecution build() {
			return new CommandExecution(this);
		}
		
		public Builder addCommand(List<String> command) {
			m_command.addAll(command);
			return this;
		}
		
		public Builder addCommand(String... command) {
			return addCommand(Arrays.asList(command));
		}
		
		public Builder setWorkingDirectory(File dir) {
			m_workingDirectory = dir;
			return this;
		}
		
		public Builder setEnvironmentVariables(Map<String,String> envs) {
			m_environmentVariables = envs;
			return this;
		}
		
		public Builder setEnvironmentFile(File envFile) {
			m_envFile = envFile;
			return this;
		}
		
		public Builder addVariable(CommandVariable var) {
			m_variables.add(var);
			return this;
		}
		public Builder addVariableIfAbscent(CommandVariable var) {
			m_variables.addIfAbscent(var);
			return this;
		}
		
		public Builder redirectStdinFromFile(File file) {
			m_stdin = Redirect.from(file);
			return this;
		}
		
		public Builder inheritStdin() {
			m_stdin = Redirect.INHERIT;
			return this;
		}
		
		public Builder discardStdin() {
			m_stdin = Redirect.DISCARD;
			return this;
		}
		
		public Builder redirectStdoutToFile(File file) {
			m_stdout = Redirect.to(file);
			return this;
		}
		
		public Builder inheritStdout() {
			m_stdout = Redirect.INHERIT;
			return this;
		}
		
		public Builder discardStdout() {
			m_stdout = Redirect.DISCARD;
			return this;
		}
		
		public Builder redirectErrorStream() {
			m_stderr = null;
			return this;
		}
		
		public Builder redirectStderrToFile(File file) {
			m_stderr = Redirect.to(file);
			return this;
		}
		
		public Builder inheritStderr() {
			m_stderr = Redirect.INHERIT;
			return this;
		}
		
		public Builder discardStderr() {
			m_stderr = Redirect.DISCARD;
			return this;
		}
		
		public Builder setTimeout(Duration timeout) {
			m_timeout = timeout;
			return this;
		}
	}
	
	private static final class VariableLookup implements StringLookup {
		private final Map<String,CommandVariable> m_varMap;
		
		private VariableLookup(Map<String,CommandVariable> vars) {
			m_varMap = vars;
		}

		@Override
		public String lookup(String key) {
			// 변수 치환 과정에서 사용자가 사용하는 key는 '<변수 이름>:<modifier>' 형태를 갖는다.
			// 이때 '<modifier>'는 생략될 수 있으며 이때는 modifier.name으로 간주한다.
			// 만일 modifier.path를 사용하는 경우에는 변수 값이 저장된 파일의 경로를 의미하며
			// 해당 경로 값을 알기 위해 변수 값을 갖는 파일을 생성한다.
			
			Tuple<String,String> parts = Utilities.split(key, ':', Tuple.of(key, "name"));
			
			// 변수 이름에 해당하는 CommandVariable 객체를 획득한다.
			CommandVariable commandVar = m_varMap.get(parts._1);
			if ( commandVar == null ) {
				return null;
            }
			else {
				// Modifier 객체를 얻고, 이를 통한 variable replacement를 시도한다.
				return commandVar.getValueByModifier(parts._2);
			}
		}
	}
}
