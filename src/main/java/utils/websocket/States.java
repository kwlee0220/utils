package utils.websocket;

import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Preconditions;
import utils.Throwables;
import utils.statechart.AbstractState;
import utils.statechart.FailureState;
import utils.statechart.Signal;
import utils.statechart.TerminalState;
import utils.statechart.Transition;
import utils.statechart.Transitions;


/**
 * {@link WebSocketStateChart}를 구성할 때 공통적으로 사용하는 상태 구현들의 모음.
 * <p>
 * WebSocket 연결의 생명주기 중 도메인과 무관한 부분, 즉 연결 수립과 종료 시의 연결 정리를 담당하는
 * 상태들을 제공한다. 수신한 메시지를 해석하고 처리하는 상태는 응용에서 정의하고, 본 클래스가 제공하는
 * 상태들과 엮어 하나의 차트를 구성한다.
 * <ul>
 *   <li>{@link WebSocketConnectingState} - 서버 접속을 수행하는 상태. 보통 차트의 초기 상태로 사용한다.</li>
 *   <li>{@link CompletedState} / {@link StoppedState} / {@link FailedState} - 각각 정상 완료, 중지, 실패로 끝나는
 *       종료 상태. 모두 진입 시 WebSocket 연결을 닫는다.</li>
 * </ul>
 * (2026-10-31에 이름을 바꾸었다: {@code OpenWebSocket} → {@link WebSocketConnectingState},
 * {@code CancelledState} → {@link StoppedState}, {@code ErrorState} → {@link FailedState}.)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class States {
	/** 본 클래스는 상태 구현들을 담는 모음일 뿐이므로 인스턴스를 생성하지 않는다. */
	private States() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}

	/**
	 * 종료 상태 진입 시 WebSocket 연결을 닫는다.
	 * <p>
	 * 실제 종료는 {@link WebSocketStateChart#closeConnection(int, String)}에 위임하며, 연결이 없거나 close에
	 * 실패하는 경우를 모두 무시하는 best-effort 시맨틱도 그대로 따른다. 종료 상태 진입 도중 발생한
	 * 예외가 상태 전이를 실패로 뒤집지 않도록 하기 위함이다.
	 *
	 * @param context	종료 상태의 컨텍스트
	 * @param reason	close 사유 문자열
	 */
	private static void sendCloseQuietly(WebSocketContext<?> context, String reason) {
		context.getStateChart().closeConnection(WebSocket.NORMAL_CLOSURE, reason);
	}

	/**
	 * WebSocket 연결을 수립하는 상태. ({@code OpenWebSocket}이었다가 2026-10-31에 이름을 바꾸었다.)
	 * <p>
	 * 상태 차트의 초기 상태로 사용하며, 접속할 서버 URL은 컨텍스트
	 * ({@link WebSocketContext#getServerUrl()})에서 얻는다. {@link #enter()}는 접속을 <b>비동기로</b>
	 * 시작하고 즉시 반환하며, 접속 결과는 나중에 신호로 차트에 전달되어 다음 상태를 결정한다.
	 * <ul>
	 *   <li>접속 성공 - 수립된 {@link WebSocket}을 차트에 등록한 뒤 {@link Signals.Connected}를 전달하고,
	 *       이 신호를 받아 {@code connectedStatePath} 상태로 전이한다. 그 처리가 끝난 뒤에 수신을 시작하므로,
	 *       서버가 접속 직후 보낸 메시지도 {@code connectedStatePath} 상태가 받는다.</li>
	 *   <li>접속 실패 - {@link Signals.ConnectionFailed}를 전달하고, 이 신호를 받아
	 *       {@code failedStatePath} 상태로 전이한다. 그 상태가 {@link FailureState}(예: {@link FailedState})이면
	 *       접속 실패 원인이 그 상태의 실패 원인으로 설정된다.</li>
	 * </ul>
	 * 접속 결과는 패키지가 함께 쓰는 전용 스레드 풀에서 처리한다. JDK는 접속 future를 공용 풀
	 * ({@code ForkJoinPool.commonPool()})에서 완료시키는데, 결과 처리가 차트의 신호 처리(사용자 상태 코드)까지
	 * 실행하므로 공용 풀을 막지 않게 하기 위해서다. 따라서 위 신호들은 {@link #enter()}를 호출한 스레드가 아닌
	 * 스레드에서 차트에 전달된다.
	 * <p>
	 * 접속이 끝났을 때 차트가 이미 끝났거나 본 상태를 떠났으면(예: {@code on(...)} 전이로 다른 상태로 감) 그 연결은
	 * 차트에 등록하지 않고 닫으며 신호도 보내지 않는다. 그 연결의 listener 콜백(접속 직후의 오류 등)도 차트에
	 * 전달하지 않는다. 접속 시도마다 별도의 listener({@code WebSocketListener.forAttempt})를 쓰기 때문이다.
	 * <p>
	 * 단, 서버 URL의 형식이 잘못된 경우는 접속 시도 이전에 걸러지기 때문에
	 * {@link Signals.ConnectionFailed}가 아니라 {@link #enter()}에서 던져지는
	 * {@link IllegalArgumentException}으로 드러난다.
	 * <p>
	 * 본 상태는 {@link LoggerSettable}을 구현하기 때문에 접속 과정의 로그를 남길 로거를 교체할 수 있다.
	 * <p>
	 * 접속에 쓰는 차트(listener, {@code HttpClient}, 접속 시간 제한)와 접속 결과 신호를 받을 차트는 컨텍스트의
	 * {@link WebSocketContext#getStateChart()}다. 상태는 그 컨텍스트를 가진 차트에만 등록할 수 있으므로
	 * ({@code StateChart.addState()}가 검사) 항상 본 상태가 등록된 차트다.
	 *
	 * @param <C>	상태 차트의 컨텍스트 타입. (CRTP self-bound)
	 */
	public static class WebSocketConnectingState<C extends WebSocketContext<C>> extends AbstractState<C>
																			implements LoggerSettable {
		/** 로거가 따로 설정되지 않은 경우에 사용할 기본 로거. */
		private final static Logger s_logger = LoggerFactory.getLogger(WebSocketConnectingState.class);

		/** 차트가 끝난 뒤 완료된 연결을 닫을 때의 close 사유. */
		static final String ABANDONED_CLOSE_REASON = "chart finished before connected";
		/** 본 상태를 떠난 뒤 완료된 연결을 닫을 때의 close 사유. */
		static final String STATE_LEFT_CLOSE_REASON = "connecting state left before connected";
		/** 재연결을 시도해 생긴 연결을 닫을 때의 close 사유. */
		static final String RECONNECTION_CLOSE_REASON = "reconnection not supported";

		/** {@link Signals.Connected} 수신 시, 즉 접속에 성공한 경우 전이할 상태의 경로. */
		private final String m_connectedStatePath;
		/** {@link Signals.ConnectionFailed} 수신 시, 즉 접속에 실패한 경우 전이할 상태의 경로. */
		private final String m_failedStatePath;
		/**
		 * 접속 시도 번호. {@link #enter()}와 {@link #exit()}마다 증가한다. 접속 결과 콜백은 자기 시도 번호가 현재 값과
		 * 같을 때만(= 본 상태에 머무는 동안 시작한 접속일 때만) 결과를 차트에 반영한다.
		 */
		private final AtomicLong m_attempt = new AtomicLong();
		/** {@link #setLogger(Logger)}로 설정된 로거. 설정된 적이 없으면 {@code null}. */
		private volatile Logger m_logger;

		/**
		 * WebSocket 연결 상태를 생성한다.
		 *
		 * @param path					본 상태의 경로
		 * @param context				상태 차트의 컨텍스트. 접속할 서버 URL과 상태 차트를 제공한다.
		 * @param connectedStatePath	접속에 성공한 경우 전이할 상태의 경로
		 * @param failedStatePath		접속에 실패한 경우 전이할 상태의 경로
		 * @throws IllegalArgumentException	{@code connectedStatePath}나 {@code failedStatePath}가 {@code null}인 경우
		 */
		public WebSocketConnectingState(String path, C context, String connectedStatePath, String failedStatePath) {
			super(path, context);
			Preconditions.checkNotNullArgument(connectedStatePath, "connectedStatePath is null");
			Preconditions.checkNotNullArgument(failedStatePath, "failedStatePath is null");

			m_connectedStatePath = connectedStatePath;
			m_failedStatePath = failedStatePath;
		}

		/**
		 * 컨텍스트에 설정된 서버로의 WebSocket 접속을 비동기로 시작한다.
		 * <p>
		 * 접속 완료를 기다리지 않고 즉시 반환하며, 접속 결과는 {@link Signals.Connected} 또는
		 * {@link Signals.ConnectionFailed} 신호로 차트에 전달된다.
		 *
		 * @throws IllegalArgumentException	컨텍스트의 서버 URL이 올바른 URI 형식이 아닌 경우
		 */
		@Override
		public void enter() {
			long attempt = m_attempt.incrementAndGet();

			// 접속마다 HttpClient를 만들지 않고 차트가 정한 클라이언트(기본은 차트들이 함께 쓰는 클라이언트)를 쓴다.
			WebSocketStateChart<C> chart = chart();
			WebSocket.Builder builder = chart.getHttpClient().newWebSocketBuilder();
			Duration connectTimeout = chart.getConnectTimeout();
			if ( connectTimeout != null ) {
				builder.connectTimeout(connectTimeout);
			}
			// 접속 헤더, 서브프로토콜 등 차트가 따로 제공하지 않는 설정.
			Consumer<WebSocket.Builder> customizer = chart.getWebSocketBuilderCustomizer();
			if ( customizer != null ) {
				customizer.accept(builder);
			}
			CompletableFuture<WebSocket> future
							= builder.buildAsync(URI.create(getContext().getServerUrl()),
												chart.listener().forAttempt(() -> isCurrent(attempt)));
			future.whenCompleteAsync((ws, err) -> {
				try {
					if ( err != null ) {
						onConnectFailed(err, attempt);
					}
					else {
						onConnected(ws, attempt);
					}
				}
				catch ( Throwable e ) {
					// 이 콜백의 예외는 받는 곳이 없어 그대로 사라진다. 로그로 남기고, 차트에 등록하지 못한 새 소켓은 닫고,
					// 차트를 실패시켜 접속 상태에 조용히 머무르지 않게 한다.
					getLogger().error("failed to handle WebSocket connection result: {}", getContext().getServerUrl(), e);
					if ( ws != null && chart.getWebSocket() != ws ) {
						closeAbandoned(ws, ABANDONED_CLOSE_REASON);
					}
					failQuietly(e);
				}
			}, SharedResources.callbackExecutor());
		}

		/**
		 * 본 상태를 떠난다. 진행 중인 접속의 결과는 더 이상 차트에 반영하지 않는다(연결이 완료되면 닫는다).
		 */
		@Override
		public void exit() {
			m_attempt.incrementAndGet();
		}

		private boolean isCurrent(long attempt) {
			return m_attempt.get() == attempt;
		}

		private void onConnectFailed(Throwable err, long attempt) {
			// CompletionException 등의 포장을 벗겨 실제 원인(예: ConnectException)을 전달한다.
			Throwable cause = Throwables.unwrapThrowable(err);
			if ( chart().isDone() || !isCurrent(attempt) ) {
				// 이미 끝났거나 떠난 상태의 접속 실패는 다른 상태에 보낼 이유가 없다.
				getLogger().info("ignored WebSocket connection failure after leaving the connecting state: {}",
								""+cause);
				return;
			}
			getLogger().error("failed to open WebSocket: {}", ""+cause);
			chart().handleSignal(new Signals.ConnectionFailed(cause));
		}

		private void onConnected(WebSocket ws, long attempt) {
			WebSocketStateChart<C> chart = chart();

			// 접속을 기다리는 동안 차트가 끝났거나(cancel/complete/fail) 본 상태를 떠났으면 이 소켓을 쓸 곳이 없다.
			// 차트가 끝났으면 종료 훅의 closeConnection()도 이미 실행되어 이 소켓을 닫을 곳이 없다. 등록 전후로 확인해 여기서 닫는다.
			// 등록 직후 차트가 끝나 종료 훅도 이 소켓을 닫는 경우가 있지만, closeConnection()은 여러 번 불러도 안전하다.
			if ( chart.isDone() ) {
				getLogger().info("chart finished before WebSocket connected; closing the new connection: {}",
								getContext().getServerUrl());
				closeAbandoned(ws, ABANDONED_CLOSE_REASON);
				return;
			}
			if ( !isCurrent(attempt) ) {
				getLogger().info("connecting state left before WebSocket connected; closing the new connection: {}",
								getContext().getServerUrl());
				closeAbandoned(ws, STATE_LEFT_CLOSE_REASON);
				return;
			}
			if ( chart.getWebSocket() != null ) {
				// 접속 상태에 다시 진입해 두 번째로 접속한 경우. 재연결은 지원하지 않으므로 새 소켓을 닫고 차트를 실패시킨다.
				getLogger().error("reconnection is not supported; closing the new connection: {}",
									getContext().getServerUrl());
				closeAbandoned(ws, RECONNECTION_CLOSE_REASON);
				failQuietly(new IllegalStateException("reconnection is not supported: "
														+ "a WebSocket is already set on this chart"));
				return;
			}
			chart.setWebSocket(ws);
			if ( chart.isDone() || !isCurrent(attempt) ) {
				// 등록하는 사이에 차트가 끝났거나 본 상태를 떠났다. 등록은 되돌릴 수 없으므로 연결을 버린 것으로 표시해
				// 이후 콜백을 차트에 전달하지 않게 하고 닫는다. 차트의 closeConnection()으로 닫으면 상대의 close 응답이
				// ConnectionClosed로 차트에 가서, 실행 중인 차트(상태를 떠난 경우)를 대체 종료로 취소하게 된다.
				// 등록된 연결은 남으므로 이후의 송신은 실패한다.
				getLogger().info("chart finished or connecting state left while registering WebSocket; "
								+ "closing the connection: {}", getContext().getServerUrl());
				chart.listener().abandon(ws);
				closeAbandoned(ws, chart.isDone() ? ABANDONED_CLOSE_REASON : STATE_LEFT_CLOSE_REASON);
				return;
			}
			getLogger().info("WebSocket connected: {}", getContext().getServerUrl());
			chart.handleSignal(new Signals.Connected(ws));

			// Connected 처리(다음 상태 진입)가 끝난 뒤에 수신을 시작한다. 그 전에 도착한 메시지는 JDK가 보관해 두었다가
			// 이 요청 뒤에 전달하므로, 서버가 접속 직후 보낸 메시지도 다음 상태가 받는다.
			chart.listener().startReceiving(ws);
		}

		/**
		 * 차트가 아직 끝나지 않았으면 주어진 원인으로 실패시킨다. 그 사이 다른 경로로 끝났으면 아무 일도 하지 않는다.
		 */
		private void failQuietly(Throwable cause) {
			if ( chart().isDone() ) {
				return;
			}
			try {
				chart().fail(Throwables.unwrapThrowable(cause));
			}
			catch ( IllegalStateException alreadyFinished ) {
				getLogger().debug("chart already finished: {}", ""+alreadyFinished);
			}
		}

		/**
		 * 접속 결과 신호에 따라 다음 전이를 결정한다.
		 * <p>
		 * {@code failedStatePath}의 상태가 {@link FailureState}이면, 전이하면서 접속 실패 원인을 그 상태의
		 * 실패 원인으로 설정한다. 따라서 차트는 실제 접속 실패 원인으로 실패한다.
		 * <p>
		 * 접속 결과가 아닌 신호는 {@link #on(Signal, Transition)} 등으로 추가 등록한 전이 표에 맡긴다.
		 * 예를 들어 접속 중에 받을 취소 신호를 등록해 둘 수 있다.
		 *
		 * @param signal	수신된 신호
		 * @return	{@link Signals.Connected}인 경우는 {@code connectedStatePath}로,
		 * 			{@link Signals.ConnectionFailed}인 경우는 {@code failedStatePath}로 전이하는 transition.
		 * 			그 외의 신호는 전이 표에서 고른 전이. 등록한 것이 없으면 {@link Optional#empty()}.
		 */
		@Override
		public Optional<Transition<C>> selectTransition(Signal signal) {
			if ( signal instanceof Signals.Connected ) {
				return Optional.of(Transitions.to(m_connectedStatePath));
			}
			else if ( signal instanceof Signals.ConnectionFailed failed ) {
				return Optional.of(Transitions.to(m_failedStatePath,
														(ctx, sig) -> setFailureCause(failed.getCause())));
			}

			// 접속 결과가 아닌 신호는 on(...)으로 추가 등록한 전이 표에 맡긴다.
			return super.selectTransition(signal);
		}

		/**
		 * 차트에 등록하지 않아 아무도 쓰지 않는 연결을 닫는다(차트가 끝난 뒤나 본 상태를 떠난 뒤 완료된 연결, 재연결
		 * 시도로 생긴 연결). 차트에 등록하지 않았으므로 차트의 {@code closeConnection()}을 쓸 수 없다. 수신을 요청하지 않은
		 * 연결이라 상대의 close 응답을 받을 수 없으므로, close 프레임을 보낸 뒤 바로 입력 쪽까지 끊는다(abort).
		 * 전송이 차트의 close 시간 제한 안에 끝나지 않아도 끊는다. 실패는 무시한다(best-effort).
		 */
		private void closeAbandoned(WebSocket ws, String reason) {
			WebSocketCloser.close(ws, WebSocket.NORMAL_CLOSURE, reason, chart().getCloseTimeout(), null, getLogger());
		}

		/**
		 * 접속 실패 시 전이할 상태가 {@link FailureState}이면 그 상태의 실패 원인을 설정한다.
		 *
		 * @param cause	접속 실패 원인. {@code null}이면 아무 일도 하지 않는다.
		 */
		private void setFailureCause(Throwable cause) {
			if ( cause != null
				&& chart().findState(m_failedStatePath).orElse(null) instanceof FailureState<?> failedState ) {
				failedState.setFailureCause(cause);
			}
		}

		/**
		 * 본 상태가 속한 차트, 즉 컨텍스트의 차트를 반환한다.
		 *
		 * @throws IllegalStateException	컨텍스트가 아직 차트에 연결되지 않은 경우
		 */
		private WebSocketStateChart<C> chart() {
			WebSocketStateChart<C> chart = getContext().getStateChart();
			Preconditions.checkState(chart != null, "context is not attached to a WebSocketStateChart: state=%s", getPath());
			return chart;
		}

		/**
		 * 접속 과정의 로그를 남길 로거를 반환한다.
		 *
		 * @return	{@link #setLogger(Logger)}로 설정된 로거. 설정된 적이 없으면 본 클래스의 기본 로거.
		 */
		@Override
		public Logger getLogger() {
			Logger logger = m_logger;
			return (logger != null) ? logger : s_logger;
		}

		@Override
		public void setLogger(Logger logger) {
			m_logger = logger;
		}
	}

	/**
	 * 시나리오가 정상적으로 완료되어 도달하는 종료 상태.
	 * <p>
	 * {@link TerminalState}를 확장하기 때문에 진입 이후로는 어떤 신호를 받아도 전이하지 않는다.
	 * 진입 시 WebSocket 연결을 정상 종료(normal closure)하되, 연결이 아직 수립되지 않았거나 이미
	 * 닫혀 있어 close 전송이 실패하는 경우는 무시한다. 연결 정리 과정에서 발생한 문제가 정상 완료를
	 * 실패로 뒤집지 않도록 하기 위함이다.
	 *
	 * @param <C>	상태 차트의 컨텍스트 타입. (CRTP self-bound)
	 */
	public static class CompletedState<C extends WebSocketContext<C>> extends TerminalState<C> {
		/**
		 * 정상 완료 종료 상태를 생성한다.
		 *
		 * @param path		본 상태의 경로
		 * @param context	상태 차트의 컨텍스트
		 */
		public CompletedState(String path, C context) {
			super(path, context);
		}

		/**
		 * WebSocket 연결을 정상 종료한다. 종료에 실패하더라도 예외를 던지지 않는다.
		 */
		@Override
		public void enter() {
			sendCloseQuietly(getContext(), "normal closure");
		}
	}

	/**
	 * 사용자의 요청 등으로 시나리오를 도중에 멈추고 도달하는 종료 상태.
	 * <p>
	 * <b>차트의 실행 결과는 {@code COMPLETED}다</b>({@link TerminalState}이므로). "중지"는 시나리오(도메인) 차원의
	 * 결과이고, 차트 실행 자체는 계획한 종료 상태에 도달해 정상 완료된 것이다. 실행 결과를 {@code CANCELLED}로
	 * 남기려면 이 상태로 전이하지 말고 차트의 {@code cancel(true)}를 쓴다. (2026-10-31 이전 이름은
	 * {@code CancelledState}였는데, 이름과 달리 결과가 {@code CANCELLED}가 아니어서 바꾸었다.)
	 * <p>
	 * 연결 정리 동작은 {@link CompletedState}와 동일하다. 중지 역시 프로토콜 상으로는 정상적인 연결 종료이기 때문에
	 * normal closure로 닫는다.
	 *
	 * @param <C>	상태 차트의 컨텍스트 타입. (CRTP self-bound)
	 */
	public static class StoppedState<C extends WebSocketContext<C>> extends TerminalState<C> {
		/**
		 * 중지 종료 상태를 생성한다.
		 *
		 * @param path		본 상태의 경로
		 * @param context	상태 차트의 컨텍스트
		 */
		public StoppedState(String path, C context) {
			super(path, context);
		}

		/**
		 * WebSocket 연결을 정상 종료한다. 종료에 실패하더라도 예외를 던지지 않는다.
		 */
		@Override
		public void enter() {
			sendCloseQuietly(getContext(), "normal closure");
		}
	}

	/**
	 * 오류로 인해 시나리오가 중단되어 도달하는 종료 상태. 차트는 실패로 끝난다.
	 * ({@code ErrorState}였다가 2026-10-31에 이름을 바꾸었다.)
	 * <p>
	 * {@link FailureState}를 확장하여 실패 원인을 보유하며, 진입 시 WebSocket 연결을 닫는다.
	 * 연결 자체는 정상 종료 코드로 닫고 사유 문자열로만 오류였음을 알린다.
	 *
	 * @param <C>	상태 차트의 컨텍스트 타입. (CRTP self-bound)
	 */
	public static class FailedState<C extends WebSocketContext<C>> extends FailureState<C> {
		/**
		 * 실패 종료 상태를 생성한다.
		 *
		 * @param path		본 상태의 경로
		 * @param context	상태 차트의 컨텍스트
		 */
		public FailedState(String path, C context) {
			super(path, context);
		}

		/**
		 * WebSocket 연결을 종료한다. 종료에 실패하더라도 예외를 던지지 않는다.
		 */
		@Override
		public void enter() {
			sendCloseQuietly(getContext(), "closure due to an error");
		}
	}
}
