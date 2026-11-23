package utils.rpc.restful;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import utils.Holder;
import utils.LoggerSettable;
import utils.Preconditions;
import utils.Throwables;
import utils.async.AsyncResult;
import utils.async.AsyncState;
import utils.async.StartableExecution;
import utils.async.op.AsyncExecutions;
import utils.func.Nullables;
import utils.thread.Guard;

/**
 * 비동기 연산(operation)들의 수명주기를 HTTP 요청에 매핑하는 서버측 핸들러.
 * <p>
 * 요청마다 {@link #createExecution}로 새 {@link StartableExecution}을 만들어 시작({@link #start})하고,
 * 시작된 연산을 세션 endpoint로 식별하여 상태 조회({@link #status})·취소({@link #cancel}) 요청을 처리한 뒤
 * 그 결과를 {@link RpcResponseMessage}로 반환한다. 여러 연산을 동시에 다룰 수 있으며, 진행 중인
 * 세션은 내부 맵에 보관된다.
 * <p>
 * 연산이 {@code RUNNING}에 도달하면 {@link #allocateSession}으로 세션을 발급하여 등록하고, 연산이
 * 종료되면 {@link #releaseSession}으로 자원을 해제한다. 종료된 세션은 {@link #getSessionRetainTimeout()}(기본 30초)
 * 동안 남아 있어, 그동안은 몇 번 조회해도 같은 종료 결과를 돌려준다. 그래서 응답을 잃은 클라이언트가 다시 조회하거나,
 * 종료 직후 들어온 취소 요청 뒤에 결과를 조회해도 결과를 받는다.
 * <p>
 * 연산 생성·세션 발급/해제·출력 추출({@link #createExecution}/{@link #allocateSession}/
 * {@link #releaseSession}/{@link #getExecutionOutput})은 추상 메소드이므로 하위 클래스가 구현하며,
 * 시작/보존/취소 대기 시간은 {@link #getStartTimeout()}/{@link #getSessionRetainTimeout()}/{@link #getCancelWait()}를
 * 재정의하여 조정할 수 있다.
 * <p>
 * 응답 메시지의 상태는 내부 execution의 {@link AsyncResult} 상태를 그대로 반영한다
 * ({@code RUNNING}/{@code COMPLETED}/{@code FAILED}/{@code CANCELLED}).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class RESTfulAsyncRpcServer implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(RESTfulAsyncRpcServer.class);
	
	private static final Duration DEFAULT_START_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration DEFAULT_SESSION_RETAIN_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration DEFAULT_CANCEL_WAIT = Duration.ofSeconds(2);
	
	private Logger m_logger = s_logger;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private final Map<String, AsyncRpcSession> m_sessions = new HashMap<>();
	
	/**
	 * 연산이 종료된 후 {@link StartableExecution}으로부터 연산 출력값을 추출하여 반환한다.
	 *
	 * @param execution	종료된 연산 실행 객체.
	 * @return	연산 출력값을 담은 맵. 연산 출력이 없으면 빈 맵을 반환한다.
	 */
	abstract protected Map<String,JsonNode> getExecutionOutput(StartableExecution<?> execution);
	
	/**
	 * 연산이 {@code RUNNING}에 도달하기를 기다리는 최대 시간을 반환한다.
	 * <p>
	 * {@code null}을 반환하면 기본값(5초)이 사용된다. 하위 클래스가 재정의하여 조정한다.
	 *
	 * @return	시작 대기 제한 시간. 기본값을 사용하려면 {@code null}.
	 */
	@Nullable protected Duration getStartTimeout() { return null; }

	/**
	 * 종료된 세션을 결과 회수를 위해 내부 맵에 더 보관하는 시간을 반환한다.
	 * <p>
	 * {@code null}을 반환하면 기본값(30초)이 사용된다. 하위 클래스가 재정의하여 조정한다.
	 *
	 * @return	세션 보존 시간. 기본값을 사용하려면 {@code null}.
	 */
	@Nullable protected Duration getSessionRetainTimeout() { return null; }

	/**
	 * 취소 요청 뒤 연산이 끝나기를 기다리는 최대 시간을 반환한다.
	 * <p>
	 * 이 안에 끝나면 {@link #cancel}은 종료 상태를, 끝나지 않으면 {@code RUNNING}(취소는 받아들여졌고 멈추는 중)을
	 * 돌려준다. 취소 요청을 보내는 클라이언트의 응답 대기 시간(mdtpy 3초)보다 짧아야 한다.
	 * {@code null}을 반환하면 기본값(2초)이 사용된다. 하위 클래스가 재정의하여 조정한다.
	 *
	 * @return	취소 대기 시간. 기본값을 사용하려면 {@code null}.
	 */
	@Nullable protected Duration getCancelWait() { return null; }
	
	/**
	 * 연산 시작 요청 메시지를 기반으로 새로운 {@link StartableExecution}을 생성한다.
	 *
	 * @param reqMsg	연산 시작 요청 메시지.
	 * @return	생성된 연산 실행 객체.
	 * @throws IOException	연산 생성 중 입출력 오류가 발생한 경우.
	 */
	abstract protected StartableExecution<?> createExecution(RpcRequestMessage reqMsg) throws IOException;

	/**
	 * 연산이 시작될 때 호출되어 이 연산을 식별하는 세션을 발급한다.
	 * <p>
	 * 연산이 {@link utils.async.AsyncState#RUNNING} 상태에 도달하는 시점에 호출되며, 반환된 세션의
	 * {@link AsyncRpcSession#getSessionEndpoint() endpoint}가 이후 응답 메시지의
	 * {@code sessionEndpoint}로
	 * 사용되고, 연산 종료 시 세션이 {@link #releaseSession(AsyncRpcSession)}의 인자로 전달된다.
	 *
	 * @param execution	세션을 발급할 대상 연산.
	 * @return	발급된 세션. ({@code null} 불가)
	 */
	abstract protected AsyncRpcSession allocateSession(StartableExecution<?> execution);

	/**
	 * 연산이 종료될 때 호출되어 {@link #allocateSession}으로 발급했던 세션의 자원을 해제한다.
	 * <p>
	 * 연산의 모든 종료 케이스(성공/실패/취소)에 대해 호출된다.
	 *
	 * @param session	해제할 세션.
	 */
	abstract protected void releaseSession(AsyncRpcSession session);
	
	/**
	 * 비동기 연산 서버를 생성한다.
	 */
	protected RESTfulAsyncRpcServer() {
	}

	/**
	 * 요청 메시지로 새 연산을 만들어 시작시키고, 시작이 확인될 때까지 대기한 뒤 현재 상태를 반환한다.
	 * <p>
	 * {@link #createExecution}로 연산을 생성한 뒤, 연산이 {@code RUNNING}에 도달하면 세션을 발급·등록
	 * ({@link #allocateSession})하고 종료 시 세션을 해제하는({@link #releaseSession}) 리스너를 등록한 다음
	 * 연산을 시작시킨다. 이어서 {@link #getStartTimeout()}(기본 5초)까지 연산이 {@code RUNNING}에
	 * 도달하기를 기다린다.
	 * <p>
	 * 연산 생성 중 입출력 오류가 나거나, 시작 도중 예외가 발생하거나, 제한 시간 내에 시작되지 못하면
	 * 실패({@code FAILED}) 응답을 반환한다.
	 * <p>
	 * 연산이 시작되면 그 사이에 이미 종료되었더라도 항상 {@code RUNNING}과 세션 endpoint를 반환하고, 결과는
	 * {@link #status}로만 돌려준다. 비동기 클라이언트(mdtpy 등)는 시작 응답으로 {@code RUNNING}·{@code FAILED}만
	 * 받기 때문이다.
	 *
	 * @param reqMsg	연산 시작 요청 메시지. ({@code null} 불가)
	 * @return	{@code RUNNING}(세션 endpoint 포함) 또는 {@code FAILED}(시작하지 못한 경우) 응답 메시지.
	 * @throws IllegalArgumentException	{@code reqMsg}가 {@code null}인 경우.
	 */
	public RpcResponseMessage start(RpcRequestMessage reqMsg) {
		Preconditions.checkNotNullArgument(reqMsg, "reqMsg is null");
		
		// 연산 시작 요청 메시지를 바탕으로 새로운 StartableExecution을 생성한다.
		StartableExecution<?> execution = null;
		try {
			execution = createExecution(reqMsg);
		}
		catch ( Exception e ) {
			return RpcResponseMessage.failed(null, e);
		}

		AsyncRpcSession session = allocateSession(execution);
		final Holder<AsyncRpcSession> sessionHolder = Holder.of(null);
		try {
			// 연산이 시작되면 해당 비동기 연산 세션 객체를 생성하고 등록한다.
			execution.whenStarted(() -> {
				sessionHolder.set(session);
				m_guard.runChecked(() -> {
					if ( m_sessions.containsKey(session.getSessionEndpoint()) ) {
						throw new IllegalStateException("session already exists: " + session.getSessionEndpoint());
					}
					m_sessions.put(session.getSessionEndpoint(), session);
				});
			});
			// 연산이 종료되면 자동으로 세션을 해제하도록 한다.
			execution.whenFinished(result -> {
				releaseSession(session);

				Duration retainTimeout = Nullables.orElse(getSessionRetainTimeout(),
															DEFAULT_SESSION_RETAIN_TIMEOUT);
    			StartableExecution<Void> delayedSessionClose
						    			= AsyncExecutions.delay(() -> removeSession(session.getSessionEndpoint()),
						    									retainTimeout);
    			delayedSessionClose.start();
			});
			
			// 연산시작을 요청하고 완전히 시작될 때까지 대기한다.
			execution.start();
			try {
				Duration startTimeout = Nullables.orElse(getStartTimeout(), DEFAULT_START_TIMEOUT);
				execution.waitWhileStarting(startTimeout.toMillis(), TimeUnit.MILLISECONDS);
			}
			catch ( TimeoutException | InterruptedException e ) {
				// 연산 시작이 제한 시간 내에 완료되지 못하면 취소시킨다.
				execution.cancel(true);
				return RpcResponseMessage.failed(null, e);
			}

			// 연산의 시작 여부에 따라 응답 메시지를 생성한다.
			// 시작되었으면 이미 종료되었더라도 RUNNING으로 응답하고, 결과는 status()로 회수하게 한다.
			AsyncRpcSession hold = sessionHolder.get();
			if ( hold != null ) {
				return RpcResponseMessage.running(hold.getSessionEndpoint());
			}
			else {
				// 연산 시작을 요청했지만 실패한 경우.
				Preconditions.checkState(execution.isFailed(),
										"unexpected state: %s, expected=Failed", execution.poll().getState());
				return RpcResponseMessage.failed(null, execution.poll().getFailureCause());
			}
		}
		catch ( Exception e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			String sessionEndpoint = (session != null) ? session.getSessionEndpoint() : null;
			return RpcResponseMessage.failed(sessionEndpoint, cause);
		}
	}
	
	/**
	 * 주어진 세션의 현재 수행 상태를 조회하여 응답 메시지로 반환한다.
	 * <p>
	 * 대기 없이 즉시 현재 상태를 조회한다.
	 *
	 * @param sessionEndpoint	조회할 세션의 endpoint. ({@code null} 불가)
	 * @return	연산의 현재 상태를 담은 응답 메시지.
	 * 			{@code sessionEndpoint}에 해당하는 세션이 존재하지 않으면 {@code null}을 반환한다.
	 * @throws IllegalArgumentException	{@code sessionEndpoint}이 {@code null}인 경우.
	 */
	@Nullable public RpcResponseMessage status(String sessionEndpoint) {
		Preconditions.checkNotNullArgument(sessionEndpoint, "sessionEndpoint is null");
		
		AsyncRpcSession session = getSession(sessionEndpoint);
		if ( session == null ) {
			return null;
		}
		
		return buildResponseMessage(session);
	}

	/**
	 * 주어진 세션의 연산을 취소한다.
	 * <p>
	 * 세션의 현재 상태에 따라 다음과 같이 처리한다. 어느 경우에도 세션은 제거하지 않으므로, 종료된 연산의 결과는 이후
	 * {@link #status}로 회수할 수 있다.
	 * <ul>
	 *   <li>수행 중: {@link utils.async.Execution#cancel(boolean) cancel(true)}로 취소를 요청한다. 받아들여지면
	 *       {@link #getCancelWait()}(기본 2초)까지 종료를 기다린다. 받아들여지지 않았는데 아직 수행 중이면 거부로 본다
	 *       ({@code accepted=false}).</li>
	 *   <li>이미 취소 중({@code CANCELLING}): 앞선 취소가 받아들여진 것이므로 다시 취소를 요청하지 않고
	 *       {@link #getCancelWait()}까지만 종료를 기다린다.</li>
	 *   <li>이미 종료: 그 상태를 돌려준다. {@code CANCELLED}이면 받아들여진 것으로, {@code COMPLETED}/{@code FAILED}이면
	 *       받아들여지지 않은 것으로 본다.</li>
	 * </ul>
	 * 기다리는 동안 끝나지 않았으면 응답 상태는 {@code RUNNING}(멈추는 중)이다. 취소를 받았지만 그 전에 완료·실패로
	 * 끝난 경우에는 그 종료 상태가 돌려진다.
	 *
	 * @param sessionEndpoint	취소할 세션의 endpoint. ({@code null} 불가)
	 * @return	취소 결과. {@code sessionEndpoint}에 해당하는 세션이 없으면 {@code null}.
	 * @throws IllegalArgumentException	{@code sessionEndpoint}이 {@code null}인 경우.
	 */
	@Nullable public RpcCancelResult cancel(String sessionEndpoint) {
		Preconditions.checkNotNullArgument(sessionEndpoint, "sessionEndpoint is null");

		AsyncRpcSession session = getSession(sessionEndpoint);
		if ( session == null ) {
			return null;
		}

		var execution = session.getExecution();
		AsyncState state = execution.getState();
		if ( state == AsyncState.CANCELLING ) {
			// 앞선 취소가 받아들여져 멈추는 중이다. cancel(true)를 다시 부르면 상태가 바뀔 때까지 cancelTimeout만큼
			// 묶이므로 부르지 않는다.
			waitForFinishedAfterCancel(session);
			return new RpcCancelResult(buildResponseMessage(session), true);
		}
		if ( state == AsyncState.COMPLETED || state == AsyncState.FAILED || state == AsyncState.CANCELLED ) {
			RpcResponseMessage resp = buildResponseMessage(session);
			return new RpcCancelResult(resp, resp.getState() == RpcState.CANCELLED);
		}

		boolean accepted;
		try {
			accepted = execution.cancel(true) || execution.getState() == AsyncState.CANCELLING;
		}
		catch ( Throwable e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			return new RpcCancelResult(RpcResponseMessage.failed(sessionEndpoint, cause), false);
		}
		if ( accepted ) {
			waitForFinishedAfterCancel(session);
		}
		else {
			getLogger().info("cancel refused: session={}, state={}", sessionEndpoint, execution.getState());
		}
		RpcResponseMessage resp = buildResponseMessage(session);
		// 이미 취소로 끝났으면 (그 사이 다른 요청으로 취소된 경우 포함) 받아들여진 것이다.
		return new RpcCancelResult(resp, accepted || resp.getState() == RpcState.CANCELLED);
	}

	/**
	 * 주어진 endpoint에 등록된 세션을 반환한다.
	 *
	 * @param sessionEndpoint	조회할 세션의 endpoint.
	 * @return	등록된 세션. 해당 endpoint의 세션이 없으면 {@code null}.
	 */
	@Nullable public AsyncRpcSession getSession(String sessionEndpoint) {
		return m_guard.get(() -> m_sessions.get(sessionEndpoint));
	}
	
	/**
	 * 등록된 세션이 하나라도 있는지 반환한다.
	 * <p>
	 * 수행 중인 세션과, 종료되었지만 아직 결과가 회수되지 않고 보존 기간 안에 있는 세션을 모두 포함한다.
	 * 이 서버를 다른 서버로 교체할 때 기존 세션이 모두 정리되었는지 확인하는 데 쓴다.
	 *
	 * @return	세션이 있으면 {@code true}.
	 */
	public boolean hasSessions() {
		return m_guard.get(() -> !m_sessions.isEmpty());
	}
	
	/**
	 * 수행 중인 모든 세션의 연산을 취소하고, 주어진 시간까지 종료되기를 기다린다.
	 * <p>
	 * 서버를 종료할 때 쓴다. 취소된 연산은 종료 처리({@link #releaseSession})를 거치므로, 하위 클래스가
	 * 연산에 묶인 자원(외부 프로세스, 세션 디렉토리 등)을 정리할 수 있다. 제한 시간 안에 끝나지 않은 연산은
	 * 경고만 남기고 기다리지 않는다. 이미 종료된 세션은 건드리지 않는다.
	 *
	 * @param timeout	모든 연산의 종료를 기다릴 최대 시간.
	 */
	public void cancelAllSessions(Duration timeout) {
		Preconditions.checkNotNullArgument(timeout, "timeout is null");
		
		List<AsyncRpcSession> sessions = m_guard.get(() -> List.copyOf(m_sessions.values()));
		List<AsyncRpcSession> running = sessions.stream()
												.filter(s -> !s.getExecution().isDone())
												.toList();
		for ( AsyncRpcSession session: running ) {
			session.getExecution().cancel(true);
		}
		
		long due = System.currentTimeMillis() + timeout.toMillis();
		for ( AsyncRpcSession session: running ) {
			long remains = Math.max(due - System.currentTimeMillis(), 0);
			try {
				session.getExecution().waitForFinished(remains, TimeUnit.MILLISECONDS);
			}
			catch ( TimeoutException e ) {
				getLogger().warn("session did not finish after cancel: session={}", session.getSessionEndpoint());
			}
			catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
	
	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	@Override
	public Logger getLogger() {
		return Nullables.orElse(m_logger, s_logger);
	}
	
	private AsyncRpcSession removeSession(String sessionEndpooint) {
		return m_guard.get(() -> m_sessions.remove(sessionEndpooint));
	}
	
	/**
	 * 취소가 받아들여진 연산이 끝나기를 {@link #getCancelWait()}까지 기다린다. 끝나지 않으면 기다리지 않고 돌아온다.
	 */
	private void waitForFinishedAfterCancel(AsyncRpcSession session) {
		Duration wait = Nullables.orElse(getCancelWait(), DEFAULT_CANCEL_WAIT);
		try {
			session.getExecution().waitForFinished(wait.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch ( TimeoutException e ) {
			getLogger().info("cancel accepted, operation is still stopping: session={}", session.getSessionEndpoint());
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
		}
	}

	private RpcResponseMessage buildResponseMessage(AsyncRpcSession session) {
		return buildResponseMessage(session.getSessionEndpoint(), session.getExecution());
	}
	
	/**
	 * 세션의 현재 연산 상태({@link AsyncResult})를 대응하는 {@link RpcResponseMessage}로 변환한다.
	 * <p>
	 * 모든 응답에는 세션 endpoint가 포함되며, {@code COMPLETED}는 연산 출력({@link #getExecutionOutput})을,
	 * {@code FAILED}는 실패 원인을 함께 담는다. 세션은 제거하지 않는다. 종료된 세션은 연산이 끝나고
	 * {@link #getSessionRetainTimeout()}이 지나면 제거된다.
	 *
	 * @param session	변환할 대상 세션.
	 * @return	상태에 대응하는 응답 메시지.
	 * @throws IllegalStateException	연산 상태가 알 수 없는 값인 경우.
	 */
	private RpcResponseMessage buildResponseMessage(String sessionEndpoint, StartableExecution<?> execution) {
		var result = execution.poll();
		
		RpcResponseMessage resp = null;
		switch ( result.getState() ) {
			case RUNNING:
				resp = RpcResponseMessage.running(sessionEndpoint);
				break;
			case COMPLETED:
				var outputs = getExecutionOutput(execution);
				resp = RpcResponseMessage.completed(sessionEndpoint, outputs);
				break;
			case FAILED:
				resp = RpcResponseMessage.failed(sessionEndpoint, result.getFailureCause());
				break;
			case CANCELLED:
				resp = RpcResponseMessage.cancelled(sessionEndpoint);
				break;
			default:
				throw new IllegalStateException("unexpected state: " + result.getState());
		}

		return resp;
	}
}
