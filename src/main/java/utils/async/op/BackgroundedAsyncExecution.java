package utils.async.op;

import java.util.Objects;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;
import utils.async.AbstractAsyncExecution;
import utils.async.AsyncExecution;
import utils.async.CancellableWork;
import utils.async.Result;



/**
 * {@literal BackgroundedAsyncExecution}은 주어진 전방(foreground) 연산 및 후방(background) 연산을
 * 병렬로 수행시키는 비동기 수행 클래스를 정의한다.
 * <p>
 * 본 비동기 수행이 시작되면 지정된 전방 및 후방 비동기 수행이 동시에 시작되어, 전방 연산이 종료될 때까지
 * 수행된다. 만일 전방 연산 종료될 때 후방 연산이 수행 중인 경우면 후방 연산은 강제로 취소되며
 * 후방 연산이 전방 연산보다 먼저 종료되는 경우는 전방 연산만 계속 수행된다.
 * 본 연산이 시작될 때 전방 연산이 시작이 통보될 때 등록된 리스너들에게 시작 통보를 내리고,
 * 본 연산을 취소시키면 수행 중인 전/후방 연산을 모두 취소 요청을 하고, 전방 연산이 취소 완료될 때
 * 본 연산의 리스너들에게 취소 통보를 전달한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class BackgroundedAsyncExecution<T> extends AbstractAsyncExecution<T>
									implements AsyncExecution<T>, CancellableWork {
	private static final Logger s_logger = LoggerFactory.getLogger(BackgroundedAsyncExecution.class);
	
	private final AsyncExecution<T> m_fgAsync;
	private final AsyncExecution<?> m_bgAsync;
	private Executor m_executor = null;
	
	/**
	 * 배경 수행 비동기 수행 객체를 생성한다.
	 * 
	 * @param fgAsync		수행시킬 전방 비동기 수행
	 * @param bgAsync		수행시킬 후방 비동기 수행
	 */
	BackgroundedAsyncExecution(AsyncExecution<T> fgAsync, AsyncExecution<?> bgAsync) {
		Objects.requireNonNull(fgAsync, "foreground AsyncExecution");
		Objects.requireNonNull(bgAsync, "background AsyncExecution");

		m_fgAsync = fgAsync;
		m_fgAsync.whenStarted(this::notifyStarted);
		m_fgAsync.whenFinished(this::onForegroundFinished);
		m_bgAsync = bgAsync;
		
		setLogger(s_logger);
	}
	
	public final AsyncExecution<T> getForegroundAsyncExecution() {
		return m_fgAsync;
	}
	
	public final AsyncExecution<?> getBackgroundAsyncExecution() {
		return m_bgAsync;
	}

	@Override
	public void start() {
		notifyStarting();
		
		Try.run(m_bgAsync::start)
			.onFailure(e -> s_logger.warn("failed to start background exec=" + m_bgAsync
										+ ", cause=" + e));
		m_fgAsync.start();
	}

	@Override
	public boolean cancelWork() {
		m_bgAsync.cancel(true);
		return m_fgAsync.cancel(true);
	}

	@Override
	public Executor getExecutor() {
		return m_executor;
	}

	@Override
	public void setExecutor(Executor executor) {
		m_executor = executor;
	}
	
	private void onForegroundFinished(Result<T> result) {
		// foreground가 종료되면 무조건 background aop를 종료시킨다.
		m_bgAsync.cancel(true);
		
		result.ifCompleted(this::notifyCompleted)
				.ifFailed(this::notifyFailed)
				.ifCancelled(this::notifyCancelled);
	}
}