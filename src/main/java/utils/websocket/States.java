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
	public static class OpenWebSocket<C extends WebSocketContext> extends AbstractState<C> implements LoggerSettable {
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
	
	public static class CompletedState<C extends WebSocketContext> extends SinkState<C> {
		public CompletedState(String path, C context) {
			super(path, context);
		}
		
		@Override
		public void enter() {
			WebSocket wsock = getContext().getStateMachine().getWebSocket();
			wsock.sendClose(WebSocket.NORMAL_CLOSURE, "normal closure").join();
		}
	}
	
	public static class CancelledState<C extends WebSocketContext> extends SinkState<C> {
		public CancelledState(String path, C context) {
			super(path, context);
		}
		
		@Override
		public void enter() {
			WebSocket wsock = getContext().getStateMachine().getWebSocket();
			wsock.sendClose(WebSocket.NORMAL_CLOSURE, "normal closure").join();
		}
	}
	
	public static class ErrorState<C extends WebSocketContext> extends ExceptionState<C> {
		public ErrorState(String path, C context) {
			super(path, context);
		}
		
		@Override
		public void enter() {
			WebSocket wsock = getContext().getStateMachine().getWebSocket();
			wsock.sendClose(WebSocket.NORMAL_CLOSURE, "closure due to a error").join();
		}
	}
}
