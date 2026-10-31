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
	public void connectionFailed_rejects_null_cause() {
		// WebSocketError와 같이 원인 없는 실패를 막는다(2026-10-31 이전에는 받아들였다).
		Assertions.assertThrows(IllegalArgumentException.class, () -> new Signals.ConnectionFailed(null));
	}

	// ---- Signals.TextMessage ----

	@Test
	public void textMessage_carries_data() {
		Signals.TextMessage sig = new Signals.TextMessage(m_ws, "hello");

		Assertions.assertSame(m_ws, sig.getWebSocket());
		Assertions.assertEquals("hello", sig.getText());
		Assertions.assertTrue(sig.toString().contains("hello"));
	}

	@Test
	public void textMessage_toString_truncates_long_text() {
		String text = "x".repeat(1000);
		String str = new Signals.TextMessage(m_ws, text).toString();

		Assertions.assertTrue(str.length() < 300, str);
		Assertions.assertTrue(str.contains("length=1000"), str);
		Assertions.assertEquals(text, new Signals.TextMessage(m_ws, text).getText());
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

	// ---- Signals.WebSocketError ----

	@Test
	public void errorMessage_carries_error() {
		Throwable error = new RuntimeException("x");
		Signals.WebSocketError sig = new Signals.WebSocketError(m_ws, error);

		Assertions.assertSame(m_ws, sig.getWebSocket());
		Assertions.assertSame(error, sig.getError());
		Assertions.assertTrue(sig.toString().contains("WebSocketError"));
	}

	@Test
	public void errorMessage_null_error_rejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new Signals.WebSocketError(m_ws, null));
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

	@Test
	public void message_payloads_reject_null() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new Signals.TextMessage(m_ws, null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> new Signals.BinaryMessage(m_ws, null, true));
	}

	@Test
	public void abbreviate_keeps_short_text_and_cuts_long_text() {
		Assertions.assertEquals("short", Signals.TextMessage.abbreviate("short"));
		String cut = Signals.TextMessage.abbreviate("y".repeat(500));
		Assertions.assertTrue(cut.startsWith("y".repeat(200) + "..."), cut);
		Assertions.assertTrue(cut.endsWith("length=500"), cut);
	}
}
