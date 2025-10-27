package utils.async.command;

import java.io.File;
import java.io.IOException;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.util.concurrent.AbstractService;

import utils.LoggerSettable;
import utils.async.Guard;
import utils.func.FOption;
import utils.io.IOUtils;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ProgramService extends AbstractService implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(ProgramService.class);
	private static final File APPLICATION_LOG = new File("application.log");
	
	private final ProgramServiceConfig m_config;
	private final Guard m_guard = Guard.create();
	private Logger m_logger = null;
	@GuardedBy("m_guard") private CommandExecution m_exec;
	@GuardedBy("m_guard") private boolean m_firstExec = true;	// 첫번째로 program을 실행하는 경우.
	@GuardedBy("m_guard") private boolean m_stopRequested = false;	// 사용자가 명시적으로 중지 요청한 경우.
	
	public static ProgramService create(ProgramServiceConfig config) {
		return new ProgramService(config);
	}
	public static ProgramService create(File configFile) throws IOException {
		String configJsonStr = IOUtils.toString(configFile);
		StringSubstitutor interpolator = StringSubstitutor.createInterpolator();
		configJsonStr = interpolator.replace(configJsonStr);
		
		ProgramServiceConfig program = JsonMapper.builder()
												.addModule(new JavaTimeModule())
												.findAndAddModules()
												.build()
												.readerFor(ProgramServiceConfig.class)
												.readValue(configJsonStr);
		return create(program);
	}

	private ProgramService(ProgramServiceConfig config) {
		m_config = config;
	}
	
	@Override
	public Logger getLogger() {
		return FOption.getOrElse(m_logger, s_logger);
	}
	
	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	@Override
	public String toString() {
		return String.format("ProgramService[%s]", m_config);
	}

	@Override
	protected void doStart() {
		m_guard.run(() -> {
			m_firstExec = true;
		});
		runCommand();
	}

	@Override
	protected void doStop() {
		m_guard.run(() -> {
			// 사용자가 명시적으로 중지 요청한 경우에는 재시작 시키지 않도록 하기 위해 플래그 설정.
			m_stopRequested = true;
			
			if ( m_exec != null ) {
				m_exec.cancel(true);
			}
			m_exec = null;
		});
	}
	
	private void runCommand() {
		m_stopRequested = false;
		File logFile = new File(m_config.getWorkingDirectory(), APPLICATION_LOG.getName());
		m_exec = CommandExecution.builder()
								.addCommand(m_config.getCommandLine())
								.setWorkingDirectory(m_config.getWorkingDirectory())
								.setEnvironmentVariables(m_config.getEnvironmentVariables())
								.setEnvironmentFile(m_config.getEnvironmentFile())
								.setTimeout(null)	// 무한대기
								.redirectErrorStream()
								.redirectStdoutToFile(logFile)
								.build();
		m_exec.whenStartedAsync(() -> {
			m_guard.run(() -> {
				if ( m_firstExec ) {
					notifyStarted();
					m_firstExec = false;
				}
			});
		});
		m_exec.whenFinished(result -> {
			// 명시적으로 중지 요청된 경우에는 재시작하지 않음.
			if ( result.isNone() ) {
				getLogger().info("the command execution is stopped by user request");
				
				notifyStopped();
				return;
			}
			
			switch ( m_config.getRestartPolicy() ) {
				case ALWAYS:	// 항상 재시작
					if ( !result.isNone() ) {
						rerunCommand();
					}
					else {
						notifyStopped();
					}
					break;
				case ON_COMPLETED:	// 정상 종료된 경우에만 재시작
					if ( result.isSuccessful() ) {
						rerunCommand();
					}
					else if ( result.isFailed() ) {
						notifyFailed(result.getCause());
					}
					else {
						notifyStopped();
					}
					break;
				case ON_FAILED:	// 오류로 실패한 경우에만 재시작
					if ( result.isFailed() ) {
						rerunCommand();
					}
					else {
						notifyStopped();
					}
					break;
				case NO:	// 재시작하지 않음.
					if ( result.isFailed() ) {
						notifyFailed(result.getCause());
					}
					else {
						notifyStopped();
					}
					break;
				default:
					throw new IllegalStateException("Unknown restart policy: " + m_config.getRestartPolicy());
			}
		});
		m_exec.start();
	}
	
	private void rerunCommand() {
		getLogger().info("restarting the command after " + m_config.getRestartDelay());
		
		new Thread(() -> {
			try {
				Thread.sleep(m_config.getRestartDelay().toMillis());
			}
			catch ( InterruptedException e ) { }
			
			runCommand();
		}).start();
	}

	public static final void main(String... args) throws Exception {
		ProgramService companion = ProgramService.create(new File("companions/program.json"));
		ServiceShutdownHook.register("test", companion);
		
		companion.startAsync();
		companion.awaitTerminated();
	}
}
