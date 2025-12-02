package utils.websocket;

import java.net.http.WebSocket;

import utils.statechart.ExceptionState;
import utils.statechart.SinkState;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class States {
	public static class CompletedState<C extends WebSocketContext> extends SinkState<C> {
		public CompletedState(C context) {
			super("Completed", context);
		}
		
		@Override
		public void enter() {
			WebSocket wsock = getContext().getWebSocket();
			wsock.sendClose(WebSocket.NORMAL_CLOSURE, "normal closure").join();
		}
	}
	
	public static class CancelledState<C extends WebSocketContext> extends SinkState<C> {
		public CancelledState(C context) {
			super("Cancelled", context);
		}
		
		@Override
		public void enter() {
			WebSocket wsock = getContext().getWebSocket();
			wsock.sendClose(WebSocket.NORMAL_CLOSURE, "normal closure").join();
		}
	}
	
	public static class ErrorState<C extends WebSocketContext> extends ExceptionState<C> {
		public ErrorState(C context) {
			super("Error", context);
		}
		
		@Override
		public void enter() {
			WebSocket wsock = getContext().getWebSocket();
			wsock.sendClose(WebSocket.NORMAL_CLOSURE, "closure due to a error").join();
		}
	}
}
