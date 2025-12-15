package utils.websocket;

import java.net.http.WebSocket;

import lombok.experimental.UtilityClass;

import utils.statechart.Signal;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@UtilityClass
public class Signals {
	public static class Connected extends WebSocketSignal {
		public Connected(WebSocket webSocket) {
			super(webSocket);
		}
		
		@Override
		public String toString() {
			return "Connected";
		}
	}
	public static class ConnectionFailed implements Signal {
		private final Throwable m_cause;
		
		public ConnectionFailed(Throwable cause) {
			m_cause = cause;
		}
		
		@Override
		public String toString() {
			return "ConnectionFailed: cause=" + m_cause;
		}
	}
	
	public static class TextMessage extends WebSocketSignal {
		private final String m_message;

		public TextMessage(WebSocket webSocket, String message) {
			super(webSocket);
			
			m_message = message;
		}

		public String getMessage() {
			return m_message;
		}
		
		@Override
		public String toString() {
			return String.format("TextMessage[message=%s]", m_message);
		}
	}
	
	public static class BinaryMessage extends WebSocketSignal {
		private final byte[] m_bytes;
		private final boolean m_last;

		public BinaryMessage(WebSocket webSocket, byte[] bytes, boolean last) {
			super(webSocket);
			
			m_bytes = bytes;
			m_last = last;
		}

		public byte[] getBytes() {
			return m_bytes;
		}
		
		public boolean isLast() {
			return m_last;
		}
		
		@Override
		public String toString() {
			return String.format("BinaryMessage[size=%d, last=%s]", m_bytes.length, m_last);
		}
	}
	
	public static class ErrorMessage extends WebSocketSignal {
		private final Throwable m_error;

		public ErrorMessage(WebSocket webSocket, Throwable error) {
			super(webSocket);
			
			m_error = error;
		}

		public Throwable getError() {
			return m_error;
		}
		
		@Override
		public String toString() {
			return String.format("ErrorMessage[error=%s]", "" + m_error);
		}
	}
	
	public static class ConnectionClosed extends WebSocketSignal {
		private final int m_statusCode;
		private final String m_reason;

		public ConnectionClosed(WebSocket webSocket, int statusCode, String reason) {
			super(webSocket);
			
			m_statusCode = statusCode;
			m_reason = reason;
		}

		public int getStatusCode() {
			return m_statusCode;
		}

		public String getReason() {
			return m_reason;
		}
		
		@Override
		public String toString() {
			return String.format("ConnectionClosed[statusCode=%d, reason=%s]", m_statusCode, m_reason);
		}
	}
}
