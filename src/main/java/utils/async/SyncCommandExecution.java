package utils.async;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import utils.KeyedValueList;
import utils.Utilities;
import utils.func.FOption;
import utils.func.Tuple;
import utils.io.FileUtils;
import utils.io.IOUtils;
import utils.stream.FStream;

/**
 * <code>CommandExecutor</code>는 주어진 명령어 프로그램을 sub-process를 통해 실행시키는 작업을 수행한다.
 * <p>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SyncCommandExecution {
	private static final Logger s_logger = LoggerFactory.getLogger(SyncCommandExecution.class);

	private final List<String> m_command;
	private final File m_workingDirectory;
	private final Map<String,Variable> m_variables;
	private final @Nullable Redirect m_stdin;
	private final @Nullable Redirect m_stdout;
	private final @Nullable Redirect m_stderr;
	private final @Nullable Duration m_timeout;

	public static enum State { NOT_STARTED, RUNNING, CANCEL_REQUESTED, FINISHED };
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private Process m_process;
	@GuardedBy("m_guard") private State m_state = State.NOT_STARTED;
	
	private SyncCommandExecution(Builder builder) {
		m_command = builder.m_command;
		m_workingDirectory = FOption.getOrElse(builder.m_workingDirectory, FileUtils.getCurrentWorkingDirectory());
		m_variables = builder.m_variables.toMap();
		m_stdin = builder.m_stdin;
		m_stdout = builder.m_stdout;
		m_stderr = builder.m_stderr;
		m_timeout = builder.m_timeout;
	}
	
	public State getState() {
		return m_guard.get(() -> m_state);
	}
	
	public Map<String,Variable> getVariableMap() {
		return m_variables;
	}

	public void run() throws InterruptedException, CancellationException, TimeoutException, IOException,
								ExecutionException {
		// Command line에 포함된 command variable을 실제 값으로 치환시킨다.
		VariableLookup lut = new VariableLookup(m_variables);
		StringSubstitutor subst = new StringSubstitutor(lut);
		List<String> command = FStream.from(m_command)
										.map(str -> subst.replace(str))
										.toList();
		
		ProcessBuilder builder = new ProcessBuilder(command);
		if ( m_workingDirectory != null ) {
			if ( !m_workingDirectory.isDirectory() ) {
				throw new IllegalArgumentException("Invalid working directory: " + m_workingDirectory);
			}
			builder.directory(m_workingDirectory);
			if ( s_logger.isDebugEnabled() ) {
				s_logger.debug("set working directory: " + m_workingDirectory);
			}
		}

		// Standard input 처리
		if ( m_stdin != null ) {
			builder.redirectInput(m_stdin);
		}
		// Standard output 처리
		if ( m_stdout != null ) {
			builder.redirectOutput(m_stdout);
		}
		// Standard error 처리
		if ( m_stdout != null ) {
			builder.redirectError(m_stderr);
		}

		if ( s_logger.isDebugEnabled() ) {
			String toStr = ( m_timeout != null ) ? ", timeout=" + m_timeout : "";
			s_logger.debug("starting program: {}{}", m_command, toStr);
		}
		try {
			Process process = m_guard.getOrThrow(() -> {
				switch ( m_state ) {
					case NOT_STARTED:
						m_state = State.RUNNING;
						return m_process = builder.start();
					case CANCEL_REQUESTED:
						m_state = State.FINISHED;
						throw new CancellationException("User cancellation has been requested");
					case RUNNING:
						throw new IllegalStateException("Already finished");
					case FINISHED:
						return null;
					default:
						throw new AssertionError();
				}
			});
			if ( process == null ) {
				return;
			}
			
			if ( m_timeout != null ) {
				boolean completed = process.waitFor(m_timeout.toMillis(), TimeUnit.MILLISECONDS);
				m_guard.runAndSignalAll(() -> m_state = State.FINISHED);
				if ( !completed ) {
					// 제한시간이 초과한 경우 -> 강제로 종료시킴.
					process.destroyForcibly();
					throw new TimeoutException(m_timeout.toString());
				}
			}
			else {
				// 제한시간이 설정되지 않은 경우에는 작업이 종료될 때까지 대기한다.
				int retCode = process.waitFor();
				m_guard.runAndSignalAll(() -> m_state = State.FINISHED);
				if ( retCode != 0 ) {
					// 프로그램 수행이 실패한 경우
					Exception cause = new Exception("Command terminates with error code " + retCode);
					throw new ExecutionException(cause);
				}
			}
		}
		catch ( IOException e ) {
			throw new ExecutionException("Failed to start process: command-line: " + m_command, e);
		}
	}

	public void cancel() throws InterruptedException {
		m_guard.runOrThrow(() -> {
			switch ( m_state ) {
				case NOT_STARTED:
					m_state = State.FINISHED;
					break;
				case RUNNING:
					if ( s_logger.isInfoEnabled() ) {
						s_logger.debug("killing process: pid={}", m_process.toHandle().pid());
					}
					m_process.destroy();
					m_state = State.CANCEL_REQUESTED;
				case CANCEL_REQUESTED:
					m_guard.awaitWhile(() -> m_state == State.CANCEL_REQUESTED);
					break;
				case FINISHED:
					break;
			}
		});
	}
	
	public static Builder builder() {
		return new Builder();
	}
	public static final class Builder {
		private List<String> m_command = Lists.newArrayList();
		private File m_workingDirectory;
		private KeyedValueList<String,Variable> m_variables = KeyedValueList.newInstance(Variable::getName);
		private Duration m_timeout;
		private Redirect m_stdin;
		private Redirect m_stdout = Redirect.DISCARD;
		private Redirect m_stderr = Redirect.DISCARD;
		
		public SyncCommandExecution build() {
			return new SyncCommandExecution(this);
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
		
		public Builder addVariable(Variable var) {
			m_variables.add(var);
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
		
		public Builder redictStdoutToFile(File file) {
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
		
		public Builder redictStderrToFile(File file) {
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
	
	public interface Variable {
		public String getName();
		public String getValue();
		
		public default String getValueByModifier(String mod) {
			switch ( mod ) {
				case "name":
					return getName();
				case "value":
					return getValue();
				default:
					throw new IllegalArgumentException("Unsupported Modifier: " + mod);
			}
		}
	}
	
	public static final class StringVariable implements Variable {
		private final String m_name;
		private final String m_value;
		
		private StringVariable(String name, String value) {
			m_name = name;
			m_value = value;
		}
		
		@Override
		public String getName() {
			return m_name;
		}

		@Override
		public String getValue() {
			return m_value;
		}
	}
	
	public static class FileVariable implements Variable {
		private final String m_name;
		private final File m_file;
		
		public FileVariable(String name, File file) {
			m_name = name;
			m_file = file;
		}
		
		@Override
		public String getName() {
			return m_name;
		}

		@Override
		public String getValue() {
			try {
				return IOUtils.toString(m_file);
			}
			catch ( IOException e ) {
				throw new RuntimeException("Failed to read FileVariable: name=" + m_name
											+ ", path=" + m_file.getAbsolutePath() + ", cause=" + e);
			}
		}
		
		public File getFile() {
			return m_file;
		}
		
		public void deleteFile() {
			m_file.delete();
		}

		@Override
		public String getValueByModifier(String mod) {
			switch ( mod ) {
				case "name":
					return getName();
				case "value":
					return getValue();
				case "path":
					return getFile().getAbsolutePath();
				default:
					throw new IllegalArgumentException("Unsupported Modifier: " + mod);
			}
		}
		
		@Override
		public String toString() {
			return String.format("FileVariable[%s]: %s", m_name, m_file.getAbsolutePath());
		}
	}
	
	private static final class VariableLookup implements StringLookup {
		private final Map<String,Variable> m_varMap;
		
		private VariableLookup(Map<String,Variable> vars) {
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
			Variable commandVar = m_varMap.get(parts._1);
			if ( commandVar == null ) {
				throw new IllegalArgumentException("Undefined CommandVariable: name=" + parts._1);
			}
			
			// Modifier 객체를 얻고, 이를 통한 variable replacement를 시도한다.
			return commandVar.getValueByModifier(parts._2);
		}
	}
}
