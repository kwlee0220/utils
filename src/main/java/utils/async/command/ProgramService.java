package utils.async.command;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.text.StringSubstitutor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.util.concurrent.AbstractService;

import utils.LoggerSettable;
import utils.Preconditions;
import utils.async.StartableExecution;
import utils.async.op.AsyncExecutions;
import utils.func.Optionals;
import utils.func.Result;
import utils.async.command.CommandExecution.Output;
import utils.thread.Guard;


/**
 * 외부 프로그램(자식 프로세스)을 Guava {@link AbstractService} 라이프사이클에 맞춰
 * 실행·감독하는 서비스.
 * <p>
 * 주어진 {@link ProgramServiceConfig}의 명령행을 {@link CommandExecution}으로 구성하여
 * 비동기 실행하고, 실행이 종료되면 설정된 {@link RestartPolicy}에 따라
 * 재시작 여부를 결정한다. 재시작 시 {@code restartDelay}가 양수이면 그만큼 지연 후 재시작한다.
 * <p>
 * <b>재시작 backoff</b>: 실행이 {@code stableRunTime}(기본 10초)보다 짧게 끝나는 일이 연달아 일어나면, 두 번째
 * 재시작부터 지연을 두 배씩 늘려 {@code maxRestartDelay}(기본 1분)까지 키운다. 시작하자마자 죽는 프로그램이 쉬지
 * 않고 재시작되며 CPU와 {@code application.log}를 소모하는 것을 막기 위함이다. 한 번이라도 {@code stableRunTime}
 * 이상 실행되면 지연은 {@code restartDelay}로 돌아간다. 늘어나는 지연의 시작값은 {@code restartDelay}와 1초 중 큰
 * 값이다({@code restartDelay}가 0이어도 backoff가 동작하도록). {@code maxRestartDelay}가 {@code restartDelay}
 * 이하이면 backoff를 하지 않는다.
 * <p>
 * <b>재시작 횟수 제한</b>: {@code restartLimit}이 양수이면, {@code restartLimitInterval}(기본 1분) 안에 그 횟수를
 * 넘겨 재시작하려 할 때 서비스를 FAILED로 전이한다. 계속 죽는 프로그램을 "실행 중"으로 두지 않고 상태로 드러내기
 * 위함이다. 기본값 0은 제한하지 않는다.
 * <ul>
 *   <li>첫 실행이 RUNNING으로 전이되면 본 서비스도 RUNNING으로 전이한다 (그 이후 재시작은
 *       서비스 상태에 영향을 주지 않는다).</li>
 *   <li>첫 실행이 RUNNING에 이르지 못하고 끝나면(실행 파일이 없거나 환경 변수 파일을 읽지 못하는 등)
 *       재시작 정책과 무관하게 <b>시작 실패</b>로 보아 서비스를 FAILED로 전이한다. 재시작 정책은 서비스가
 *       RUNNING이 된 이후의 종료에만 적용된다.</li>
 *   <li>첫 실행이 {@code startTimeout}({@link ProgramServiceConfig#getStartTimeout()}) 안에 RUNNING이 되지
 *       못하면 서비스를 {@link TimeoutException} 원인으로 FAILED로 전이하고 그 실행을 취소한다.
 *       {@code startTimeout}이 0이면 제한하지 않는다.</li>
 *   <li>시작 중({@code STARTING})에 {@link #stopAsync()}가 호출되면 시작 중인 실행을 취소하고 서비스를
 *       TERMINATED로 전이한다.</li>
 *   <li>재시작하려는 다음 실행을 구성하거나 시작하지 못하면(예: working directory가 사라짐) 서비스를
 *       FAILED로 전이한다. 자식 프로세스 없이 RUNNING으로 남지 않도록 하기 위함이다.</li>
 *   <li>사용자가 {@link #stopAsync()}로 명시적 중지 요청을 하면 재시작 정책과 무관하게 종료된다.</li>
 *   <li>정책상 비-재시작 종료가 실패라면 서비스를 FAILED, 그 외엔 TERMINATED로 전이한다.</li>
 * </ul>
 * 자식 프로세스의 표준 출력/오류는 작업 디렉터리의 {@code application.log}에 병합 기록된다. 재시작해도
 * 이전 실행의 출력(종료 원인 등)이 지워지지 않도록 파일 뒤에 이어 쓴다. 파일 크기는 제한하지 않으므로
 * 필요하면 외부에서 정리(rotate)해야 한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ProgramService extends AbstractService implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(ProgramService.class);
	private static final File APPLICATION_LOG = new File("application.log");
	/** Backoff로 늘어나는 재시작 지연의 최소 시작값. */
	private static final Duration MIN_BACKOFF_DELAY = Duration.ofSeconds(1);
	
	private final ProgramServiceConfig m_config;
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private StartableExecution<Void> m_exec;
	@GuardedBy("m_guard") private boolean m_stopRequested = false;	// 사용자가 명시적으로 중지 요청한 경우.
	// 실행 종료 후 다음 동작(재시작/종료)을 판단 중인 경우. 이 동안 doStop()이 호출되면 중지 요청만 기록하고,
	// 판단을 확정하는 쪽(onExecutionFinished)이 중지를 처리한다. doStop()은 Guava의 lock을 쥔 채 호출되므로
	// 판단이 끝나기를 기다리면 안 된다(판단 쪽의 notifyStopped()/notifyFailed()가 같은 lock을 필요로 한다).
	@GuardedBy("m_guard") private boolean m_restarting = false;
	// 서비스 시작의 결말(첫 실행의 RUNNING 도달, 첫 실행의 종료, 시작 제한 시간 초과 중 먼저 일어난 것)이 정해졌는지 여부.
	// 셋 중 하나만 서비스 상태를 바꾸도록 m_guard 아래에서 먼저 표시한 쪽이 이긴다.
	@GuardedBy("m_guard") private boolean m_startSettled = false;
	@GuardedBy("m_guard") private boolean m_startTimedOut = false;	// 시작 제한 시간 초과로 서비스가 실패한 경우.
	
	// 재시작 backoff/횟수 제한 상태. 실행 종료 콜백(onExecutionFinished → decide)에서만 사용된다. 다음 실행은 이전
	// 실행의 종료 콜백이 끝난 뒤에 시작되므로 콜백들은 순차로 호출되어 별도 동기화가 필요 없다.
	private int m_consecutiveShortRuns = 0;
	private final ArrayDeque<Long> m_restartNanos = new ArrayDeque<>();
	// 현재 실행의 자식 프로세스가 시작된(RUNNING) 시각. 시작되지 못한 실행은 -1.
	private volatile long m_runStartNanos = -1;
	// 운영 조회용 통계. 쓰기는 순차적인 실행 콜백에서만 일어난다.
	private volatile int m_restartCount = 0;
	private volatile OptionalInt m_lastExitCode = OptionalInt.empty();
	
	// 명령 실행에 사용할 executor. null이면 실행마다 쓰레드를 만든다. 테스트에서 시작 지연을 재현하는 데 사용한다.
	@Nullable private volatile Executor m_executor = null;

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
	 * 파일을 JSON으로 먼저 파싱한 뒤, <b>문자열 값</b>에 포함된 참조({@code ${env:HOME}}, {@code ${sys:user.dir}} 등)를
	 * Apache Commons Text의 {@link StringSubstitutor#createInterpolator()}로 치환한다. 원문 전체를 치환한 뒤 파싱하면
	 * 따옴표·역슬래시·줄바꿈이 든 값이 JSON 구문을 깨뜨리기 때문이다. 따라서 참조는 문자열 안에 써야 한다
	 * (숫자 항목도 {@code "restartLimit": "${env:LIMIT}"}처럼 쓰면 Jackson이 숫자로 변환한다). 객체의 키는 치환하지
	 * 않는다. Jackson {@link JsonMapper}는 {@link JavaTimeModule}을 포함해 활성 모듈을 자동 로드한다.
	 *
	 * @param configFile JSON 설정 파일.
	 * @return 생성된 서비스 인스턴스.
	 * @throws IOException 파일 읽기 또는 JSON 파싱이 실패한 경우.
	 */
	public static ProgramService create(File configFile) throws IOException {
		JsonMapper mapper = JsonMapper.builder()
									.addModule(new JavaTimeModule())
									.findAndAddModules()
									.build();
		JsonNode config = mapper.readTree(configFile);
		substituteStrings(config, StringSubstitutor.createInterpolator());

		return create(mapper.treeToValue(config, ProgramServiceConfig.class));
	}

	/**
	 * JSON 트리의 모든 문자열 값에 변수 치환을 적용한다 (객체의 키는 제외).
	 */
	private static void substituteStrings(JsonNode node, StringSubstitutor interpolator) {
		if ( node instanceof ObjectNode obj ) {
			for ( Iterator<Map.Entry<String,JsonNode>> it = obj.fields(); it.hasNext(); ) {
				Map.Entry<String,JsonNode> field = it.next();
				if ( field.getValue().isTextual() ) {
					field.setValue(TextNode.valueOf(interpolator.replace(field.getValue().textValue())));
				}
				else {
					substituteStrings(field.getValue(), interpolator);
				}
			}
		}
		else if ( node instanceof ArrayNode array ) {
			for ( int i = 0; i < array.size(); ++i ) {
				JsonNode elm = array.get(i);
				if ( elm.isTextual() ) {
					array.set(i, TextNode.valueOf(interpolator.replace(elm.textValue())));
				}
				else {
					substituteStrings(elm, interpolator);
				}
			}
		}
	}

	private ProgramService(ProgramServiceConfig config) {
		Preconditions.checkNotNullArgument(config, "config is null");
		Preconditions.checkArgument(config.getCommandLine() != null && !config.getCommandLine().isEmpty(),
									"commandLine must not be empty: %s", config);

		// 서비스 실행 중 호출자가 설정 객체를 바꿔도 다음 재시작의 명령·정책이 달라지지 않도록 복사해 둔다.
		m_config = new ProgramServiceConfig(config);
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
	 * 명령 실행에 사용할 executor를 설정한다. 서비스 시작 전에 호출해야 한다.
	 *
	 * @param executor	executor. {@code null}이면 실행마다 쓰레드를 만든다.
	 */
	/**
	 * 서비스가 명령을 재시작한 횟수를 반환한다. 첫 실행은 세지 않으며, {@code restartDelay} 동안 대기 중인 재시작도
	 * 센다.
	 *
	 * @return	재시작 횟수.
	 */
	public int getRestartCount() {
		return m_restartCount;
	}

	/**
	 * 가장 최근에 종료 코드를 남기고 끝난 자식 프로세스의 종료 코드를 반환한다.
	 * <p>
	 * 중지나 취소로 종료 코드 없이 끝난 실행은 반영하지 않으므로, 재시작을 거듭하다 중지된 서비스에서도 마지막으로
	 * 스스로 종료한 프로세스의 코드를 얻을 수 있다. 서비스 실패 원인을 파악하는 데 사용한다.
	 *
	 * @return	종료 코드. 아직 종료 코드를 남기고 끝난 실행이 없으면 빈 값.
	 */
	public OptionalInt getLastExitCode() {
		return m_lastExitCode;
	}

	void setExecutor(@Nullable Executor executor) {
		m_executor = executor;
	}

	/**
	 * 서비스 시작 콜백. Guava {@link AbstractService}가 호출한다.
	 * <p>
	 * 첫 번째 {@link CommandExecution}을 구성하여 비동기로 시작한다. 자식 프로세스가 RUNNING
	 * 상태에 도달하면 {@link #notifyStarted()}를 통해 본 서비스도 RUNNING으로 전이한다.
	 * {@code startTimeout}이 설정되어 있으면 그 시간이 지나도 RUNNING이 되지 못할 때 서비스를 실패시키도록
	 * 예약한다.
	 */
	@Override
	protected void doStart() {
		StartableExecution<Void> exec = buildExecution(true, Duration.ZERO);
		m_guard.run(() -> m_exec = exec);

		Duration startTimeout = m_config.getStartTimeout();
		if ( startTimeout != null && startTimeout.compareTo(Duration.ZERO) > 0 ) {
			CompletableFuture.delayedExecutor(startTimeout.toNanos(), TimeUnit.NANOSECONDS)
							.execute(() -> onStartTimeout(exec, startTimeout));
		}
		exec.start();
	}

	/**
	 * {@code startTimeout}이 지났을 때 호출된다. 첫 실행이 아직 RUNNING이 되지도, 종료되지도 않았고 중지 요청도
	 * 없으면 서비스를 실패시키고 첫 실행을 취소한다. 그 외에는 아무것도 하지 않는다.
	 */
	private void onStartTimeout(StartableExecution<Void> exec, Duration startTimeout) {
		boolean timedOut = m_guard.get(() -> {
			if ( m_startSettled || m_stopRequested ) {
				return false;
			}
			m_startSettled = true;
			m_startTimedOut = true;
			return true;
		});
		if ( timedOut ) {
			TimeoutException cause = new TimeoutException("the command did not start within " + startTimeout);
			getLogger().warn("the command did not start within {}, failing the service", startTimeout);
			notifyFailed(cause);
			// 아직 시작 중인 실행은 cancel이 시작 단계가 끝나기를 기다릴 수 있으므로 별도 쓰레드에서 취소한다.
			// 시작 단계가 cancel timeout보다 길어 취소가 실패하더라도, 늦게 RUNNING이 된 실행은 시작 콜백에서
			// 다시 취소된다.
			CompletableFuture.runAsync(() -> exec.cancel(true));
		}
	}

	/**
	 * 시작 중({@code STARTING}) 중지 콜백. 서비스가 RUNNING이 되기 전에 {@link #stopAsync()}가 호출되면
	 * Guava {@link AbstractService}가 호출한다.
	 * <p>
	 * 사용자 명시적 중지를 표시하고 시작 중인 실행을 취소한다. 취소된 실행의 종료 콜백이 중지 요청을 보고
	 * {@link #notifyStopped()}를 호출한다.
	 * <p>
	 * 본 메소드는 Guava의 lock을 쥔 채 호출되므로, 시작 중인 실행의 취소 대기(최대 cancel timeout)를 여기서
	 * 하지 않도록 취소 요청은 비동기로 보낸다. 또한 실행 참조({@code m_exec})는 지우지 않는다. 취소가 늦어
	 * 실행이 RUNNING에 이르면 Guava가 {@link #notifyStarted()} 직후 {@link #doStop()}을 호출하는데,
	 * 그때 같은 실행을 다시 취소할 수 있어야 하기 때문이다.
	 */
	@Override
	protected void doCancelStart() {
		StartableExecution<Void> toCancel = m_guard.get(() -> {
			m_stopRequested = true;
			return m_exec;
		});
		if ( toCancel != null ) {
			CompletableFuture.runAsync(() -> toCancel.cancel(true));
		}
	}

	/**
	 * 서비스 중지 콜백. Guava {@link AbstractService}가 호출한다.
	 * <p>
	 * 사용자 명시적 중지를 표시하는 플래그를 세팅하고 현재 수행 중인 명령 실행을 강제 취소한다.
	 * 취소된 실행의 종료 콜백은 중지 요청을 보고 재시작 정책을 평가하지 않은 채 {@link #notifyStopped()}를 호출한다.
	 * <p>
	 * 본 메소드는 Guava의 lock을 쥔 채 호출되므로 아무것도 기다리지 않는다. 실행이 막 종료되어 다음 동작을
	 * 판단하는 중이면 중지 요청만 기록하고 반환하며, 판단을 확정하는 쪽이 다음 실행을 시작하지 않고 서비스를
	 * 중지시킨다.
	 */
	@Override
	protected void doStop() {
		// 본 메소드는 Guava의 lock을 쥔 채 호출되므로 아무것도 기다리지 않는다.
		// 다음 동작을 판단 중이면(m_restarting) 중지 요청만 기록하고, 판단을 확정하는 쪽이 중지를 처리한다.
		StartableExecution<Void> toCancel = m_guard.get(() -> {
			m_stopRequested = true;
			if ( m_restarting ) {
				return null;
			}
			StartableExecution<Void> e = m_exec;
			m_exec = null;
			return e;
		});
		if ( toCancel != null ) {
			// cancel()은 실행의 lock을 잡는다. 실행의 종료 콜백 쪽이 그 lock을 쥔 채 Guava의 lock을 기다리는 경우와
			// 교착되지 않도록, Guava의 lock 밖(별도 쓰레드)에서 취소한다.
			CompletableFuture.runAsync(() -> toCancel.cancel(true));
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
	 *   <li>양 경우 모두 자식 프로세스가 시작된 시각을 기록한다 (backoff 판단용 실행 시간 측정).</li>
	 * </ul>
	 * 양 경우 모두 종료 콜백(whenFinished)이 부착되어, 종료 시 재시작 정책을 평가하고 다음 실행을
	 * 빌드·시작하거나 {@link #notifyStopped()}/{@link #notifyFailed(Throwable)}로 서비스를 종료시킨다.
	 *
	 * @param firstExec 첫 실행 여부.
	 * @param restartDelay	재시작 지연 ({@code firstExec}이면 무시된다).
	 * @return 시작 가능한 실행 객체. 지연 래퍼가 적용된 경우 {@code StartableExecution<Void>} 형태.
	 */
	private StartableExecution<Void> buildExecution(boolean firstExec, Duration restartDelay) {
		CommandExecution cmdExec = buildCommandExecution();
		cmdExec.whenStarted(() -> m_runStartNanos = System.nanoTime());
		// 종료 코드는 재시작 판단(onExecutionFinished)보다 먼저 기록되도록 먼저 등록한다.
		cmdExec.whenFinished(r -> cmdExec.getExitCode().ifPresent(code -> m_lastExitCode = OptionalInt.of(code)));
		
		StartableExecution<Void> exec = cmdExec;
		if ( firstExec ) {
			cmdExec.whenStarted(() -> {
				boolean timedOut = m_guard.get(() -> {
					if ( m_startTimedOut ) {
						return true;
					}
					m_startSettled = true;
					return false;
				});
				if ( timedOut ) {
					// 시작 제한 시간이 지난 뒤에야 RUNNING이 된 경우. 서비스는 이미 실패했으므로 실행을 취소한다.
					// 본 콜백은 실행의 lock 안에서 호출되므로 취소는 별도 쓰레드에서 요청한다.
					getLogger().info("the command started after the start timeout, cancelling it");
					CompletableFuture.runAsync(() -> cmdExec.cancel(true));
					return;
				}
				getLogger().info("the command is started");
				notifyStarted();
			});
		}
		else {
			// 재시작 지연이 설정된 경우에는 AsyncExecutions.delay로 지연 시작 래퍼를 씌운다.
			if ( restartDelay.compareTo(Duration.ZERO) > 0 ) {
				exec = AsyncExecutions.delay(cmdExec, restartDelay);
				exec.whenStarted(() -> {
					getLogger().info("restarting the command after {}", restartDelay);
				});
			}
			else {
				cmdExec.whenStarted(() -> {
					getLogger().info("restarting the command");
				});
			}
		}
		
		// 종료 처리는 실제 명령 실행(cmdExec)의 종료를 기준으로 한다. 지연 래퍼는 취소되면 대상 프로세스의 정리가
		// 끝나기 전에 먼저 종료되므로, 래퍼를 기준으로 하면 프로세스가 남은 채 서비스가 종료될 수 있다.
		// cmdExec은 프로세스 정리를 마친 뒤 종료 상태로 전이한다.
		// 또한 콜백이 실행의 lock 밖에서 돌도록 비동기로 등록한다. 콜백은 Guava의 상태 전이(notifyXxx)를 호출하므로,
		// lock을 쥔 채 호출하면 Guava의 lock을 쥔 쪽의 cancel()과 교착될 수 있다.
		AtomicBoolean handled = new AtomicBoolean();
		Consumer<Result<Void>> onFinished = result -> {
			if ( handled.compareAndSet(false, true) ) {
				onExecutionFinished(result);
			}
		};
		cmdExec.whenFinishedAsync(onFinished);
		if ( exec != cmdExec ) {
			// 지연 래퍼가 대상을 시작하지 못하고 실패한 경우에는 대상이 종료되지 않으므로 래퍼의 결과로 처리한다.
			// (래퍼가 취소된 경우에는 대상도 취소되어 위의 콜백이 호출된다.)
			exec.whenFinishedAsync(result -> {
				if ( result.isFailed() && cmdExec.isNotStarted() ) {
					onFinished.accept(result);
				}
			});
		}
		
		return exec;
	}
	
	/**
	 * 설정으로부터 {@link CommandExecution}을 구성한다.
	 * <p>
	 * 타임아웃은 무한대(null), 표준 오류는 표준 출력으로 합류, 표준 출력은 작업 디렉터리의
	 * {@code application.log} 뒤에 이어 기록된다(재시작해도 이전 실행의 출력을 지우지 않는다).
	 *
	 * @return 빌드된 {@link CommandExecution}.
	 */
	private CommandExecution buildCommandExecution() {
		File workingAbsDir = m_config.getWorkingDirectory().getAbsoluteFile();
		
		// m_config.getWorkingDirectory()가 relative path인 경우,
		// 현재 작업 디렉터리 기준으로 절대 경로를 만든다.
		File logFile = new File(workingAbsDir, APPLICATION_LOG.getName());
		getLogger().info("command output will be redirected to " + logFile);
		
		CommandExecution exec = CommandExecution.builder()
												.addCommand(m_config.getCommandLine())
												.workingDirectory(workingAbsDir)
												.environmentVariables(m_config.getEnvironments())
												.environmentFile(m_config.getEnvironmentFile())
												.timeout(null)	// 무한대기
												.stderr(Output.toStdout())
												.stdout(Output.file(logFile).append())
												.build();
		Executor executor = m_executor;
		if ( executor != null ) {
			exec.setExecutor(executor);
		}
		return exec;
	}

	/**
	 * 첫 실행이 RUNNING에 이르지 못하고 끝난 결과를 서비스의 시작 실패 원인으로 변환한다.
	 */
	private static Throwable toStartFailureCause(Result<Void> result) {
		if ( result.isFailed() ) {
			return result.getCause();
		}
		else if ( result.isNone() ) {
			return new CancellationException("the command was cancelled before it started");
		}
		else {
			return new IllegalStateException("the command terminated before it started");
		}
	}

	/**
	 * 명령 실행이 종료되었을 때 호출되어 다음 동작(재시작 또는 서비스 종료)을 수행한다.
	 * <p>
	 * 처리는 세 단계로 나뉜다.
	 * <ol>
	 *   <li>판단: {@link #decide(Result)}로 다음 동작을 정한다. 이 단계에서는 Guava의 상태 전이
	 *       ({@code notifyXxx})를 호출하지 않는다.</li>
	 *   <li>확정: {@code m_guard}를 잡고 판단 중 표시를 해제하면서, 그 사이 중지 요청이 있었는지 확인한다.
	 *       중지 요청이 있었으면 사용자 중지를 우선한다.</li>
	 *   <li>반영: 확정된 동작에 따라 다음 실행을 시작하거나 서비스를 종료시킨다.</li>
	 * </ol>
	 * {@link #doStop()}은 판단 중이면 기다리지 않고 중지 요청만 기록하므로, Guava의 lock을 쥔 {@code doStop()}과
	 * 상태 전이를 하려는 본 메소드가 서로를 기다리는 일(deadlock)이 생기지 않는다.
	 */
	private void onExecutionFinished(Result<Void> result) {
		Entry entry = m_guard.get(() -> {
			if ( m_startTimedOut ) {
				return Entry.IGNORE;
			}
			m_startSettled = true;
			if ( m_stopRequested ) {
				return Entry.STOP;
			}
			m_restarting = true;
			return Entry.DECIDE;
		});
		if ( entry == Entry.IGNORE ) {
			// 시작 제한 시간 초과로 서비스가 이미 실패한 뒤 취소된 첫 실행이 종료된 경우.
			return;
		}
		if ( entry == Entry.STOP ) {
			getLogger().info("the command execution is stopped by user request");
			notifyStopped();
			return;
		}

		// 1. 판단
		Decision decision;
		try {
			decision = decide(result);
		}
		catch ( Exception e ) {
			// 다음 실행을 구성하지 못한 경우(예: working directory가 사라짐). 서비스가 자식 프로세스 없이
			// RUNNING으로 남지 않도록 서비스를 실패시킨다.
			getLogger().error("failed to restart the command, failing the service: " + this, e);
			decision = Decision.fail(e);
		}

		// 2. 확정
		StartableExecution<Void> next = decision.next();
		boolean stoppedMeanwhile = m_guard.get(() -> {
			m_restarting = false;
			if ( m_stopRequested ) {
				return true;
			}
			if ( next != null ) {
				m_exec = next;
			}
			return false;
		});
		if ( stoppedMeanwhile ) {
			// 판단하는 사이에 중지가 요청된 경우. 만들어 둔 다음 실행은 시작하지 않고 버린다.
			getLogger().info("the command execution is stopped by user request during restart");
			notifyStopped();
			return;
		}

		// 3. 반영
		if ( next != null ) {
			try {
				next.start();
				++m_restartCount;
			}
			catch ( Exception e ) {
				if ( m_guard.get(() -> m_stopRequested) ) {
					// 확정 직후 doStop()이 아직 시작되지 않은 이 실행을 취소한 경우. 취소된 실행의 종료 콜백이
					// 이미 서비스를 중지시켰다.
					return;
				}
				getLogger().error("failed to restart the command, failing the service: " + this, e);
				notifyFailed(e);
			}
		}
		else if ( decision.failure() != null ) {
			notifyFailed(decision.failure());
		}
		else {
			notifyStopped();
		}
	}

	/**
	 * 종료된 실행의 결과와 재시작 정책으로부터 다음 동작을 판단한다. 서비스 상태는 바꾸지 않는다.
	 * <p>
	 * 첫 실행이 RUNNING에 이르지 못하고 끝난 경우(서비스가 아직 {@code STARTING})에는 재시작 정책과 무관하게
	 * 시작 실패로 판단한다. 재시작해도 같은 원인으로 실패할 가능성이 높고, 서비스가 STARTING에 갇히지 않도록 하기
	 * 위함이다.
	 *
	 * @param result	종료된 실행의 결과.
	 * @return	다음 동작.
	 */
	private Decision decide(Result<Void> result) {
		if ( state() == State.STARTING ) {
			Throwable cause = toStartFailureCause(result);
			getLogger().warn("the command failed to start, failing the service: cause={}", cause.toString());
			return Decision.fail(cause);
		}

		switch ( m_config.getRestartPolicy() ) {
			case ALWAYS:
				return restart();
			case ON_COMPLETED:	// 정상 종료된 경우에만 재시작
				if ( result.isSuccessful() ) {
					return restart();
				}
				return result.isFailed() ? Decision.fail(result.getCause()) : Decision.stop();
			case ON_FAILED:	// 오류로 실패한 경우에만 재시작
				return result.isFailed() ? restart() : Decision.stop();
			case NO:	// 재시작하지 않음.
				return result.isFailed() ? Decision.fail(result.getCause()) : Decision.stop();
			default:
				throw new IllegalStateException("Unknown restart policy: " + m_config.getRestartPolicy());
		}
	}

	/** 실행 종료 콜백의 진입 시 처리 방식. */
	private enum Entry {
		IGNORE,		// 시작 제한 시간 초과로 서비스가 이미 실패함.
		STOP,		// 중지 요청이 있었음.
		DECIDE,		// 재시작 정책에 따라 다음 동작을 판단함.
	}

	/**
	 * 재시작을 판단한다. 방금 끝난 실행 시간으로 backoff 상태를 갱신하고, 재시작 횟수 제한을 넘으면 서비스 실패로,
	 * 아니면 backoff가 반영된 지연으로 다음 실행을 구성한다.
	 */
	private Decision restart() {
		long now = System.nanoTime();
		long startedAt = m_runStartNanos;
		m_runStartNanos = -1;
		Duration runTime = (startedAt >= 0) ? Duration.ofNanos(now - startedAt) : Duration.ZERO;
		if ( runTime.compareTo(m_config.getStableRunTime()) >= 0 ) {
			m_consecutiveShortRuns = 0;
		}
		else {
			++m_consecutiveShortRuns;
		}

		int limit = m_config.getRestartLimit();
		if ( limit > 0 ) {
			Duration interval = m_config.getRestartLimitInterval();
			while ( !m_restartNanos.isEmpty() && now - m_restartNanos.peekFirst() > interval.toNanos() ) {
				m_restartNanos.removeFirst();
			}
			if ( m_restartNanos.size() >= limit ) {
				String msg = String.format("restart limit exceeded: %d restarts within %s", limit, interval);
				getLogger().error("{}, failing the service", msg);
				return Decision.fail(new IllegalStateException(msg));
			}
			m_restartNanos.addLast(now);
		}

		Duration delay = computeRestartDelay(m_config.getRestartDelay(), m_config.getMaxRestartDelay(),
											m_consecutiveShortRuns);
		if ( m_consecutiveShortRuns >= 2 && delay.compareTo(m_config.getRestartDelay()) > 0 ) {
			getLogger().warn("the command exited after {} ({} short runs in a row), backing off: restart in {}",
							runTime, m_consecutiveShortRuns, delay);
		}
		return Decision.restart(buildExecution(false, delay));
	}

	/**
	 * 연속으로 짧게 끝난 실행 횟수에 따른 재시작 지연을 계산한다.
	 * <p>
	 * 짧은 실행이 한 번이면 {@code base}, 이후로는 {@code max(base, 1초)}에서 시작해 횟수마다 두 배씩 늘린
	 * 값을 {@code max}로 제한한다. {@code max}가 {@code base} 이하이면 항상 {@code base}이다.
	 *
	 * @param base		기본 재시작 지연 ({@code restartDelay}).
	 * @param max		최대 재시작 지연 ({@code maxRestartDelay}).
	 * @param shortRuns	연속으로 짧게 끝난 실행 횟수 (방금 끝난 실행 포함).
	 * @return	재시작 지연.
	 */
	static Duration computeRestartDelay(Duration base, Duration max, int shortRuns) {
		if ( shortRuns <= 1 || max.compareTo(base) <= 0 ) {
			return base;
		}
		Duration delay = (base.compareTo(MIN_BACKOFF_DELAY) >= 0) ? base : MIN_BACKOFF_DELAY;
		for ( int i = 1; i < shortRuns && delay.compareTo(max) < 0; ++i ) {
			delay = delay.multipliedBy(2);
		}
		return (delay.compareTo(max) < 0) ? delay : max;
	}

	/**
	 * 실행 종료 후의 다음 동작. {@code next}가 있으면 재시작, {@code failure}가 있으면 서비스 실패,
	 * 둘 다 없으면 서비스 종료를 뜻한다.
	 */
	private record Decision(@Nullable StartableExecution<Void> next, @Nullable Throwable failure) {
		static Decision restart(StartableExecution<Void> next) {
			return new Decision(next, null);
		}
		static Decision fail(Throwable cause) {
			return new Decision(null, cause);
		}
		static Decision stop() {
			return new Decision(null, null);
		}
	}
}
