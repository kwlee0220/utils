package utils.async.command;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.util.concurrent.AbstractService;

import utils.LoggerSettable;
import utils.RuntimeInterruptedException;
import utils.async.StartableExecution;
import utils.async.op.AsyncExecutions;
import utils.func.Optionals;
import utils.func.Result;
import utils.io.IOUtils;
import utils.thread.Guard;


/**
 * 외부 프로그램(자식 프로세스)을 Guava {@link AbstractService} 라이프사이클에 맞춰
 * 실행·감독하는 서비스.
 * <p>
 * 주어진 {@link ProgramServiceConfig}의 명령행을 {@link CommandExecution}으로 구성하여
 * 비동기 실행하고, 실행이 종료되면 설정된 {@link RestartPolicy}에 따라
 * 재시작 여부를 결정한다. 재시작 시 {@code restartDelay}가 양수이면 그만큼 지연 후 재시작한다.
 * <ul>
 *   <li>첫 실행이 RUNNING으로 전이되면 본 서비스도 RUNNING으로 전이한다 (그 이후 재시작은
 *       서비스 상태에 영향을 주지 않는다).</li>
 *   <li>사용자가 {@link #stopAsync()}로 명시적 중지 요청을 하면 재시작 정책과 무관하게 종료된다.</li>
 *   <li>정책상 비-재시작 종료가 실패라면 서비스를 FAILED, 그 외엔 TERMINATED로 전이한다.</li>
 * </ul>
 * 자식 프로세스의 표준 출력/오류는 작업 디렉터리의 {@code application.log}에 병합 기록된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ProgramService extends AbstractService implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(ProgramService.class);
	private static final File APPLICATION_LOG = new File("application.log");
	
	private final ProgramServiceConfig m_config;
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private StartableExecution<Void> m_exec;
	@GuardedBy("m_guard") private boolean m_stopRequested = false;	// 사용자가 명시적으로 중지 요청한 경우.
	@GuardedBy("m_guard") private boolean m_restarting = false;	// 재시작 여부를 판단 중인 경우
																// (true인 경우는 m_exec이 다음 실행으로 바뀌는
																// 중이므로, doStop()이 들어와도 m_exec.cancel()을
																// 시도하지 않도록 guard)

	private volatile Logger m_logger = null;
	
	/**
	 * 주어진 설정으로 {@link ProgramService} 인스턴스를 생성한다.
	 *
	 * @param config 프로그램 실행 설정 (명령행, 작업 디렉터리, 환경 변수, 재시작 정책 등).
	 * @return 생성된 서비스 인스턴스.
	 */
	public static ProgramService create(ProgramServiceConfig config) {
		return new ProgramService(config);
	}

	/**
	 * 설정 파일(JSON)을 읽어 {@link ProgramService} 인스턴스를 생성한다.
	 * <p>
	 * 파일 내용은 Apache Commons Text의 {@link StringSubstitutor#createInterpolator()}로
	 * 환경 변수 및 시스템 프로퍼티가 치환된 뒤 JSON으로 파싱된다. Jackson {@link JsonMapper}는
	 * {@link JavaTimeModule}을 포함해 활성 모듈을 자동 로드한다.
	 *
	 * @param configFile JSON 설정 파일.
	 * @return 생성된 서비스 인스턴스.
	 * @throws IOException 파일 읽기 또는 JSON 파싱이 실패한 경우.
	 */
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
		return Optionals.getOrElse(m_logger, s_logger);
	}
	
	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	@Override
	public String toString() {
		return String.format("ProgramService[%s]", m_config);
	}

	/**
	 * 서비스 시작 콜백. Guava {@link AbstractService}가 호출한다.
	 * <p>
	 * 첫 번째 {@link CommandExecution}을 구성하여 비동기로 시작한다. 자식 프로세스가 RUNNING
	 * 상태에 도달하면 {@link #notifyStarted()}를 통해 본 서비스도 RUNNING으로 전이한다.
	 */
	@Override
	protected void doStart() {
		StartableExecution<Void> exec = buildExecution(true);
		m_guard.run(() -> m_exec = exec);
		exec.start();
	}

	/**
	 * 서비스 중지 콜백. Guava {@link AbstractService}가 호출한다.
	 * <p>
	 * 사용자 명시적 중지를 표시하는 플래그를 세팅하고 현재 수행 중인 명령 실행을 강제 취소한다.
	 * 취소된 실행의 종료 콜백은 {@code Result.none}을 받게 되어 재시작 정책을 평가하지 않고
	 * {@link #notifyStopped()}를 호출한다.
	 */
	@Override
	protected void doStop() {
		// 재시작 여부를 판단 중인 경우에는 m_exec이 다음 실행으로 바뀌는 중이므로, doStop()이 들어와도
		// m_exec.cancel()을 시도하지 않도록 guard한다.
		try {
			StartableExecution<Void> toCancel = m_guard.awaitCondition(() -> !m_restarting)
														.andGet(() -> {
															m_stopRequested = true;
															StartableExecution<Void> e = m_exec;
															m_exec = null;
															return e;
														});
			if ( toCancel != null ) {
				toCancel.cancel(true);
			}
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new RuntimeInterruptedException("interrupted while waiting for restart decision", e);
		}
	}

	/**
	 * 명령 실행 객체를 구성하고 시작·종료 콜백을 등록하여 반환한다. 실제 {@code start()}
	 * 호출은 호출자가 수행한다.
	 * <p>
	 * 부착되는 콜백은 다음과 같다:
	 * <ul>
	 *   <li>{@code firstExec == true}: STARTED 콜백이 본 서비스의 {@link #notifyStarted()}를 호출
	 *       (서비스를 RUNNING으로 전이).</li>
	 *   <li>{@code firstExec == false}: 재시작 로그 콜백을 부착하고, {@code restartDelay}가 양수이면
	 *       {@link AsyncExecutions#delay(StartableExecution, Duration)}로 지연 시작 래퍼를 씌운다.</li>
	 * </ul>
	 * 양 경우 모두 종료 콜백(whenFinished)이 부착되어, 종료 시 재시작 정책을 평가하고 다음 실행을
	 * 빌드·시작하거나 {@link #notifyStopped()}/{@link #notifyFailed(Throwable)}로 서비스를 종료시킨다.
	 *
	 * @param firstExec 첫 실행 여부.
	 * @return 시작 가능한 실행 객체. 지연 래퍼가 적용된 경우 {@code StartableExecution<Void>} 형태.
	 */
	private StartableExecution<Void> buildExecution(boolean firstExec) {
		CommandExecution cmdExec = buildCommandExecution();
		
		StartableExecution<Void> exec = cmdExec;
		if ( firstExec ) {
			cmdExec.whenStarted(() -> {
				getLogger().info("the command is started");
				notifyStarted();
			});
		}
		else {
			Duration delay = m_config.getRestartDelay();
			if ( delay.isPositive() ) {
				exec = AsyncExecutions.delay(cmdExec, delay);
				exec.whenStarted(() -> {
					getLogger().info("restarting the command after " + m_config.getRestartDelay());
				});
			}
			else {
				cmdExec.whenStarted(() -> {
					getLogger().info("restarting the command");
				});
			}
		}
		
		exec.whenFinished(result -> {
			boolean stopRequested = m_guard.get(() -> {
				if ( !m_stopRequested ) {
					m_restarting = true;
				}
				return m_stopRequested;
			});
			if ( stopRequested ) {
				getLogger().info("the command execution is stopped by user request during restart");
				notifyStopped();
				return;
			}
			
			// 재시작 여부를 판단함.
			try {
				StartableExecution<Void> nextExec = getNextExecution(result);
				if ( nextExec != null ) {
					m_guard.run(() -> m_exec = nextExec);
					nextExec.start();
				}
				// nextExec가 null인 경우는 재시작하지 않고 종료하는 경우이므로, getNextExecution()에서
				// notifyStopped()/notifyFailed()가 호출되어 이미 서비스가 종료된 상태다.
			}
			finally {
				m_guard.run(() -> m_restarting = false);
			}
		});
		
		return exec;
	}
	
	/**
	 * 설정으로부터 {@link CommandExecution}을 구성한다.
	 * <p>
	 * 타임아웃은 무한대(null), 표준 오류는 표준 출력으로 합류, 표준 출력은 작업 디렉터리의
	 * {@code application.log}로 기록된다.
	 *
	 * @return 빌드된 {@link CommandExecution}.
	 */
	private CommandExecution buildCommandExecution() {
		File logFile = new File(m_config.getWorkingDirectory(), APPLICATION_LOG.getName());
		getLogger().info("command output will be redirected to " + logFile.getAbsolutePath());
		
		return CommandExecution.builder()
								.addCommand(m_config.getCommandLine())
								.workingDirectory(m_config.getWorkingDirectory())
								.environmentVariables(m_config.getEnvironments())
								.environmentFile(m_config.getEnvironmentFile())
								.timeout(null)	// 무한대기
								.redirectErrorStream()
								.redirectStdoutToFile(logFile)
								.build();
	}

	private StartableExecution<Void> getNextExecution(Result<Void> result) {
		// 재시작이 필요한 경우엔 nextExec는 non-null이 됨.
		StartableExecution<Void> nextExec = null;
		switch ( m_config.getRestartPolicy() ) {
			case ALWAYS:
			    nextExec = buildExecution(false);
			    break;
			case ON_COMPLETED:	// 정상 종료된 경우에만 재시작
				if ( result.isSuccessful() ) {
					nextExec = buildExecution(false);
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
					nextExec = buildExecution(false);
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
		
		return nextExec;
	}

	/**
	 * 데모/수동 테스트용 진입점. {@code companions/program.json}을 설정 파일로 사용하여
	 * 서비스를 기동하고, 종료 시까지 대기한다. {@link ServiceShutdownHook}을 통해
	 * JVM 종료 시 자식 프로세스도 정리된다.
	 *
	 * @param args 무시.
	 * @throws Exception 설정 로드 또는 실행 중 예외.
	 */
	public static final void main(String... args) throws Exception {
		ProgramService companion = ProgramService.create(new File("companions/program.json"));
		ServiceShutdownHook.register("test", companion);

		companion.startAsync();
		companion.awaitTerminated();
	}
}
