package utils.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.statechart.AbstractState;
import utils.statechart.ExceptionState;
import utils.statechart.Signal;
import utils.statechart.SinkState;
import utils.statechart.Transition;
import utils.statechart.Transitions;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class States {
	private static final Logger s_logger = LoggerFactory.getLogger(States.class);

	/**
	 * 종료 상태 진입 시 WebSocket 연결을 정상 종료(close)한다.
	 * <p>
	 * 연결이 수립되기 전(예: 연결 실패로 인한 종료)이라 {@code wsock}이 {@code null}이거나,
	 * 이미 닫혔거나 원격이 연결을 끊어 close 전송이 실패하는 경우는 모두 무시한다.
	 * 종료 상태 진입 도중 발생한 예외가 상태 전이를 실패로 뒤집지 않도록 하기 위함이다.
	 *
	 * @param wsock		종료할 WebSocket 연결. {@code null}이면 아무 일도 하지 않는다.
	 * @param reason	close 사유 문자열
	 */
	private static void sendCloseQuietly(WebSocket wsock, String reason) {
		if ( wsock == null ) {
			return;
		}
		try {
			wsock.sendClose(WebSocket.NORMAL_CLOSURE, reason).join();
		}
		catch ( Throwable e ) {
			s_logger.debug("ignored error while closing WebSocket on terminal state: {}", ""+e);
		}
	}

	public static class OpenWebSocket<C extends WebSocketContext<C>> extends AbstractState<C> implements LoggerSettable {
		private final static Logger s_logger = LoggerFactory.getLogger(OpenWebSocket.class);

		private final WebSocketStateChart<C> m_machine;
		private final String m_targetStatePath;
		private final String m_failStatePath;
		private Logger m_logger;

		public OpenWebSocket(String path, C context, WebSocketStateChart<C> machine,
							String targetStatePath, String failStatePath) {
			super(path, context);

			m_machine = machine;
			m_targetStatePath = targetStatePath;
			m_failStatePath = failStatePath;
		}

		@Override
		public void enter() {
			CompletableFuture<WebSocket> future
							= HttpClient.newHttpClient()
										.newWebSocketBuilder()
										.buildAsync(URI.create(getContext().getServerUrl()),
													m_machine.getWebSocketListener());
			future.whenCompleteAsync((ws, err) -> {
				if ( err != null ) {
					getLogger().error("failed to open WebSocket: {}", ""+err);
					m_machine.handleSignal(new Signals.ConnectionFailed(err));
				}
				else {
					getLogger().info("WebSocket connected: {}", getContext().getServerUrl());
					m_machine.setWebSocket(ws);
					m_machine.handleSignal(new Signals.Connected(ws));
				}
			});
		}

		@Override
		public Optional<Transition<C>> selectTransition(Signal signal) {
			if ( signal instanceof Signals.Connected ) {
				return Optional.of(Transitions.noop(m_targetStatePath));
			}
			else if ( signal instanceof Signals.ConnectionFailed ) {
				return Optional.of(Transitions.noop(m_failStatePath));
			}

			return Optional.empty();
		}

		@Override
		public Logger getLogger() {
			return (m_logger != null) ? m_logger : s_logger;
		}

		@Override
		public void setLogger(Logger logger) {
			m_logger = logger;
		}
	}

	public static class CompletedState<C extends WebSocketContext<C>> extends SinkState<C> {
		public CompletedState(String path, C context) {
			super(path, context);
		}

		@Override
		public void enter() {
			sendCloseQuietly(getContext().getStateChart().getWebSocket(), "normal closure");
		}
	}

	public static class CancelledState<C extends WebSocketContext<C>> extends SinkState<C> {
		public CancelledState(String path, C context) {
			super(path, context);
		}

		@Override
		public void enter() {
			sendCloseQuietly(getContext().getStateChart().getWebSocket(), "normal closure");
		}
	}

	public static class ErrorState<C extends WebSocketContext<C>> extends ExceptionState<C> {
		public ErrorState(String path, C context) {
			super(path, context);
		}

		@Override
		public void enter() {
			sendCloseQuietly(getContext().getStateChart().getWebSocket(), "closure due to a error");
		}
	}
}
