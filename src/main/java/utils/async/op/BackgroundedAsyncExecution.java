package utils.async.op;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.async.CancellableWork;
import utils.async.EventDrivenExecution;
import utils.async.StartableExecution;
import utils.func.Result;
import utils.func.Try;



/**
 * {@literal BackgroundedAsyncExecution}은 주어진 전방(foreground) 연산과 후방(background) 연산을
 * 병렬로 수행시키는 합성 비동기 수행 클래스이다.
 * <p>
 * 본 연산이 시작되면 후방 연산과 전방 연산이 함께 시작된다. 본 연산의 최종 결과(완료/실패/취소)는
 * 전방 연산의 종료 상태를 그대로 따른다.
 * <ul>
 *   <li>전방 연산이 종료될 때 후방 연산이 아직 수행 중이면, 후방 연산은 강제로 취소된다.</li>
 *   <li>후방 연산이 전방 연산보다 먼저 종료되면, 그 결과와 무관하게 전방 연산만 계속 수행된다.</li>
 *   <li>후방 연산의 시작이 실패해도 전방 연산은 정상적으로 시작된다 (실패는 로그로만 남는다).</li>
 * </ul>
 * 본 연산의 상태 전이는 전방 연산의 상태 전이를 그대로 따라간다. 전방 연산이 RUNNING으로 전이되면
 * 본 연산도 RUNNING으로 전이하여 등록된 시작 리스너들에게 통보하고, 전방 연산이 종료되면 본
 * 연산도 동일 결과로 종료되며 종료 리스너들에게 통보한다. 본 연산이 외부에서 취소되면 전방·후방
 * 연산이 모두 취소 요청되고, 전방 연산이 실제로 취소 종료되는 시점에 본 연산의 취소 통보가
 * 일어난다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class BackgroundedAsyncExecution<T> extends EventDrivenExecution<T>
											implements StartableExecution<T>, CancellableWork {
	private static final Logger s_logger = LoggerFactory.getLogger(BackgroundedAsyncExecution.class);
	
	private final StartableExecution<T> m_fgAsync;
	private final StartableExecution<?> m_bgAsync;

	/**
	 * 배경 수행 비동기 수행 객체를 생성한다.
	 *
	 * @param fgAsync		수행시킬 전방 비동기 수행
	 * @param bgAsync		수행시킬 후방 비동기 수행
	 */
	BackgroundedAsyncExecution(StartableExecution<T> fgAsync, StartableExecution<?> bgAsync) {
		Preconditions.checkNotNullArgument(fgAsync, "foreground AsyncExecution");
		Preconditions.checkNotNullArgument(bgAsync, "background AsyncExecution");

		m_fgAsync = fgAsync;
		m_bgAsync = bgAsync;

		setLogger(s_logger);
	}
	
	/**
	 * 전방(foreground) 비동기 수행 객체를 반환한다.
	 *
	 * @return 전방 비동기 수행 객체. 본 합성 실행의 결과/실패/취소가 이 실행을 따른다.
	 */
	public final StartableExecution<T> getForegroundAsyncExecution() {
		return m_fgAsync;
	}

	/**
	 * 후방(background) 비동기 수행 객체를 반환한다.
	 *
	 * @return 후방 비동기 수행 객체. 전방 수행이 종료되면 강제 취소된다.
	 */
	public final StartableExecution<?> getBackgroundAsyncExecution() {
		return m_bgAsync;
	}

	/**
	 * 전방·후방 비동기 수행을 동시에 시작한다.
	 * <p>
	 * 이미 시작/종료 상태이면 아무 작업도 하지 않는다. 후방 수행을 먼저 시작하고 이어서
	 * 전방 수행을 시작한다. 후방 수행 시작이 실패해도 로그만 남기고 전방 수행은 정상적으로
	 * 시작된다. 본 실행의 STARTED 통보는 전방 수행이 RUNNING 상태로 전이될 때 (등록한
	 * 콜백을 통해) 일어난다.
	 */
	@Override
	public void start() {
		if ( !notifyStarting() ) {
			return;
		}

		m_fgAsync.whenStarted(this::notifyStarted);
		m_fgAsync.whenFinished(this::onForegroundFinished);

		Try.run(m_bgAsync::start)
			.ifFailed(e -> s_logger.warn("failed to start background exec=" + m_bgAsync
										+ ", cause=" + e));
		m_fgAsync.start();
	}

	/**
	 * 전방·후방 비동기 수행에 모두 강제 취소를 요청한다.
	 * <p>
	 * {@link CancellableWork} 구현으로서, 본 실행에 대한 {@link #cancel(boolean)} 호출 시
	 * 내부적으로 호출된다. 후방을 먼저 취소한 뒤 전방을 취소한다.
	 *
	 * @return 전방 수행의 {@code cancel(true)} 결과.
	 */
	@Override
	public boolean cancelWork() {
		m_bgAsync.cancel(true);
		return m_fgAsync.cancel(true);
	}

	/**
	 * 전방 수행이 종료되었을 때 호출되는 콜백.
	 * <p>
	 * 후방 수행을 강제 취소하고, 전방 수행의 결과(성공/실패/취소)에 따라 본 실행의
	 * 종료 상태를 결정한다. 후방 수행이 이미 종료된 경우 {@code cancel(true)}는 무시되므로
	 * 후방 수행의 상태와 무관하게 안전하다.
	 */
	private void onForegroundFinished(Result<T> result) {
		Try.run(() -> m_bgAsync.cancel(true));
		result.ifSuccessful(this::notifyCompleted)
				.ifFailed(this::notifyFailed)
				.ifNone(this::notifyCancelled);
	}
}