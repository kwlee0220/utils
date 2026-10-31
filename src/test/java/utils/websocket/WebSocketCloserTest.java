package utils.websocket;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * {@link WebSocketCloser}가 어느 경우에도 결국 연결을 정리(abort)하는지 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
public class WebSocketCloserTest {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketCloserTest.class);
	private static final Duration TIMEOUT = Duration.ofMillis(100);

	private WebSocket mockSocket(CompletableFuture<WebSocket> sendResult) {
		WebSocket ws = mock(WebSocket.class);
		when(ws.sendClose(anyInt(), anyString())).thenReturn(sendResult);
		return ws;
	}

	@Test
	public void aborts_when_close_frame_is_not_sent_in_time() throws Exception {
		WebSocket ws = mockSocket(new CompletableFuture<>());

		WebSocketCloser.Closing closing = WebSocketCloser.close(ws, 1000, "bye", TIMEOUT,
															new CompletableFuture<Void>(), s_logger);

		closing.closed().get(2, TimeUnit.SECONDS);
		Assertions.assertTrue(closing.frameSent().isDone());
		verify(ws).abort();
	}

	@Test
	public void aborts_when_peer_does_not_reply_in_time() throws Exception {
		WebSocket ws = mockSocket(CompletableFuture.completedFuture(null));

		WebSocketCloser.Closing closing = WebSocketCloser.close(ws, 1000, "bye", TIMEOUT,
															new CompletableFuture<Void>(), s_logger);

		Assertions.assertTrue(closing.frameSent().isDone());
		closing.closed().get(2, TimeUnit.SECONDS);
		verify(ws).abort();
	}

	@Test
	public void does_not_abort_when_peer_replies() throws Exception {
		WebSocket ws = mockSocket(CompletableFuture.completedFuture(null));
		CompletableFuture<Void> peerClosed = new CompletableFuture<>();

		WebSocketCloser.Closing closing = WebSocketCloser.close(ws, 1000, "bye", TIMEOUT, peerClosed, s_logger);
		Assertions.assertFalse(closing.closed().isDone());
		peerClosed.complete(null);

		closing.closed().get(1, TimeUnit.SECONDS);
		Thread.sleep(TIMEOUT.toMillis() * 2);
		verify(ws, never()).abort();
	}

	@Test
	public void aborts_right_after_sending_when_no_reply_is_expected() throws Exception {
		WebSocket ws = mockSocket(CompletableFuture.completedFuture(null));

		WebSocketCloser.Closing closing = WebSocketCloser.close(ws, 1000, "bye", Duration.ofSeconds(5), null, s_logger);

		closing.closed().get(1, TimeUnit.SECONDS);
		verify(ws).abort();
	}

	@Test
	public void aborts_immediately_when_sending_fails() throws Exception {
		WebSocket ws = mockSocket(CompletableFuture.failedFuture(new IOException("broken")));

		WebSocketCloser.Closing closing = WebSocketCloser.close(ws, 1000, "bye", Duration.ofSeconds(5),
															new CompletableFuture<Void>(), s_logger);

		closing.closed().get(1, TimeUnit.SECONDS);
		verify(ws).abort();
	}

	@Test
	public void waits_for_reply_when_close_was_already_sent() throws Exception {
		// 먼저 보낸 close 때문에 출력이 닫혀 이번 전송이 실패한 경우는 응답을 기다린다.
		WebSocket ws = mockSocket(CompletableFuture.failedFuture(new IOException("output closed")));
		when(ws.isOutputClosed()).thenReturn(true);
		CompletableFuture<Void> peerClosed = new CompletableFuture<>();

		WebSocketCloser.Closing closing = WebSocketCloser.close(ws, 1000, "bye", Duration.ofSeconds(5),
															peerClosed, s_logger);
		Assertions.assertFalse(closing.closed().isDone());
		peerClosed.complete(null);

		closing.closed().get(1, TimeUnit.SECONDS);
		verify(ws, never()).abort();
	}

	@Test
	public void sendClose_throwing_synchronously_is_handled() throws Exception {
		WebSocket ws = mock(WebSocket.class);
		when(ws.sendClose(anyInt(), anyString())).thenThrow(new IllegalArgumentException("illegal code"));

		WebSocketCloser.Closing closing = WebSocketCloser.close(ws, 1005, "bye", Duration.ofSeconds(5),
															new CompletableFuture<Void>(), s_logger);

		closing.closed().get(1, TimeUnit.SECONDS);
		verify(ws, timeout(1000)).abort();
	}
}
