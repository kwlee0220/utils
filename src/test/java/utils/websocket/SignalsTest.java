package utils.websocket;

import static org.mockito.Mockito.mock;

import java.net.http.WebSocket;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link WebSocketSignal} 및 {@link Signals}의 데이터 캐리어 동작을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SignalsTest {
	private final WebSocket m_ws = mock(WebSocket.class);

	// ---- WebSocketSignal ----

	@Test
	public void webSocketSignal_null_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new WebSocketSignal(null);
		});
	}

	@Test
	public void webSocketSignal_returns_provided_socket() {
		WebSocketSignal sig = new WebSocketSignal(m_ws);
		Assertions.assertSame(m_ws, sig.getWebSocket());
	}

	// ---- Signals.Connected ----

	@Test
	public void connected_carries_websocket_and_toString() {
		Signals.Connected sig = new Signals.Connected(m_ws);
		Assertions.assertSame(m_ws, sig.getWebSocket());
		Assertions.assertEquals("Connected", sig.toString());
	}

	@Test
	public void connected_null_websocket_rejected_via_super() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new Signals.Connected(null);
		});
	}

	// ---- Signals.ConnectionFailed ----

	@Test
	public void connectionFailed_carries_cause_and_toString() {
		Throwable cause = new RuntimeException("boom");
		Signals.ConnectionFailed sig = new Signals.ConnectionFailed(cause);

		Assertions.assertSame(cause, sig.getCause());
		Assertions.assertTrue(sig.toString().contains("ConnectionFailed"));
	}

	@Test
	public void connectionFailed_accepts_null_cause() {
		// ConnectionFailed는 WebSocketSignal이 아니므로 cause null을 막지 않음.
		Signals.ConnectionFailed sig = new Signals.ConnectionFailed(null);
		Assertions.assertNull(sig.getCause());
	}

	// ---- Signals.TextMessage ----

	@Test
	public void textMessage_carries_data() {
		Signals.TextMessage sig = new Signals.TextMessage(m_ws, "hello");

		Assertions.assertSame(m_ws, sig.getWebSocket());
		Assertions.assertEquals("hello", sig.getMessage());
		Assertions.assertTrue(sig.toString().contains("hello"));
	}

	// ---- Signals.BinaryMessage ----

	@Test
	public void binaryMessage_carries_data_and_last_flag() {
		byte[] data = { 1, 2, 3 };
		Signals.BinaryMessage sig = new Signals.BinaryMessage(m_ws, data, true);

		Assertions.assertSame(m_ws, sig.getWebSocket());
		Assertions.assertSame(data, sig.getBytes());
		Assertions.assertTrue(sig.isLast());
		Assertions.assertTrue(sig.toString().contains("size=3"));
		Assertions.assertTrue(sig.toString().contains("last=true"));
	}

	@Test
	public void binaryMessage_last_false_preserved() {
		Signals.BinaryMessage sig = new Signals.BinaryMessage(m_ws, new byte[0], false);
		Assertions.assertFalse(sig.isLast());
	}

	// ---- Signals.ErrorMessage ----

	@Test
	public void errorMessage_carries_error() {
		Throwable error = new RuntimeException("x");
		Signals.ErrorMessage sig = new Signals.ErrorMessage(m_ws, error);

		Assertions.assertSame(m_ws, sig.getWebSocket());
		Assertions.assertSame(error, sig.getError());
		Assertions.assertTrue(sig.toString().contains("ErrorMessage"));
	}

	// ---- Signals.ConnectionClosed ----

	@Test
	public void connectionClosed_carries_status_and_reason() {
		Signals.ConnectionClosed sig = new Signals.ConnectionClosed(m_ws, 1000, "normal");

		Assertions.assertSame(m_ws, sig.getWebSocket());
		Assertions.assertEquals(1000, sig.getStatusCode());
		Assertions.assertEquals("normal", sig.getReason());
		Assertions.assertTrue(sig.toString().contains("1000"));
		Assertions.assertTrue(sig.toString().contains("normal"));
	}
}
