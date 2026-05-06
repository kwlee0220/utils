package utils.websocket;

import static org.mockito.Mockito.mock;

import java.net.http.WebSocket;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link WebSocketSignal} 및 {@link Signals}의 데이터 캐리어 동작을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SignalsTest {
	private final WebSocket m_ws = mock(WebSocket.class);

	// ---- WebSocketSignal ----

	@Test(expected = IllegalArgumentException.class)
	public void webSocketSignal_null_rejected() {
		new WebSocketSignal(null);
	}

	@Test
	public void webSocketSignal_returns_provided_socket() {
		WebSocketSignal sig = new WebSocketSignal(m_ws);
		Assert.assertSame(m_ws, sig.getWebSocket());
	}

	// ---- Signals.Connected ----

	@Test
	public void connected_carries_websocket_and_toString() {
		Signals.Connected sig = new Signals.Connected(m_ws);
		Assert.assertSame(m_ws, sig.getWebSocket());
		Assert.assertEquals("Connected", sig.toString());
	}

	@Test(expected = IllegalArgumentException.class)
	public void connected_null_websocket_rejected_via_super() {
		new Signals.Connected(null);
	}

	// ---- Signals.ConnectionFailed ----

	@Test
	public void connectionFailed_carries_cause_and_toString() {
		Throwable cause = new RuntimeException("boom");
		Signals.ConnectionFailed sig = new Signals.ConnectionFailed(cause);

		Assert.assertSame(cause, sig.getCause());
		Assert.assertTrue(sig.toString().contains("ConnectionFailed"));
	}

	@Test
	public void connectionFailed_accepts_null_cause() {
		// ConnectionFailed는 WebSocketSignal이 아니므로 cause null을 막지 않음.
		Signals.ConnectionFailed sig = new Signals.ConnectionFailed(null);
		Assert.assertNull(sig.getCause());
	}

	// ---- Signals.TextMessage ----

	@Test
	public void textMessage_carries_data() {
		Signals.TextMessage sig = new Signals.TextMessage(m_ws, "hello");

		Assert.assertSame(m_ws, sig.getWebSocket());
		Assert.assertEquals("hello", sig.getMessage());
		Assert.assertTrue(sig.toString().contains("hello"));
	}

	// ---- Signals.BinaryMessage ----

	@Test
	public void binaryMessage_carries_data_and_last_flag() {
		byte[] data = { 1, 2, 3 };
		Signals.BinaryMessage sig = new Signals.BinaryMessage(m_ws, data, true);

		Assert.assertSame(m_ws, sig.getWebSocket());
		Assert.assertSame(data, sig.getBytes());
		Assert.assertTrue(sig.isLast());
		Assert.assertTrue(sig.toString().contains("size=3"));
		Assert.assertTrue(sig.toString().contains("last=true"));
	}

	@Test
	public void binaryMessage_last_false_preserved() {
		Signals.BinaryMessage sig = new Signals.BinaryMessage(m_ws, new byte[0], false);
		Assert.assertFalse(sig.isLast());
	}

	// ---- Signals.ErrorMessage ----

	@Test
	public void errorMessage_carries_error() {
		Throwable error = new RuntimeException("x");
		Signals.ErrorMessage sig = new Signals.ErrorMessage(m_ws, error);

		Assert.assertSame(m_ws, sig.getWebSocket());
		Assert.assertSame(error, sig.getError());
		Assert.assertTrue(sig.toString().contains("ErrorMessage"));
	}

	// ---- Signals.ConnectionClosed ----

	@Test
	public void connectionClosed_carries_status_and_reason() {
		Signals.ConnectionClosed sig = new Signals.ConnectionClosed(m_ws, 1000, "normal");

		Assert.assertSame(m_ws, sig.getWebSocket());
		Assert.assertEquals(1000, sig.getStatusCode());
		Assert.assertEquals("normal", sig.getReason());
		Assert.assertTrue(sig.toString().contains("1000"));
		Assert.assertTrue(sig.toString().contains("normal"));
	}
}
