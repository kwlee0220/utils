package utils.websocket;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.reactivex.rxjava3.observers.TestObserver;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import utils.websocket.WebSocketObservable.Message;


/**
 * {@link WebSocketObservable}이 상대의 close 상태 코드를 downstream 종료로 옮기는 방식을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketObservableTest {
	private TestObserver<Message> subscribeAndCloseFromServer(int code, String reason) throws Exception {
		try ( MockWebServer server = new MockWebServer() ) {
			server.enqueue(new MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener() {
				@Override
				public void onOpen(okhttp3.WebSocket ws, okhttp3.Response response) {
					ws.close(code, reason);
				}
			}));
			server.start();

			String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
			TestObserver<Message> observer = WebSocketObservable.builder().uri(url).build().test();
			Assertions.assertTrue(observer.await(5, TimeUnit.SECONDS), "observable did not terminate");
			return observer;
		}
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void normal_close_completes() throws Exception {
		subscribeAndCloseFromServer(1000, "bye").assertComplete().assertNoErrors();
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void abnormal_close_signals_error_with_status_code() throws Exception {
		TestObserver<Message> observer = subscribeAndCloseFromServer(1011, "internal error");
		observer.assertNotComplete();
		observer.assertError(e -> e instanceof WebSocketClosedException closed
									&& closed.getStatusCode() == 1011
									&& "internal error".equals(closed.getReason()));
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void receives_messages_and_dispose_closes_connection() throws Exception {
		java.util.concurrent.CountDownLatch closing = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicInteger closeCode = new java.util.concurrent.atomic.AtomicInteger();
		try ( MockWebServer server = new MockWebServer() ) {
			server.enqueue(new MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener() {
				@Override
				public void onOpen(okhttp3.WebSocket ws, okhttp3.Response response) {
					ws.send("hello");
					ws.send(okio.ByteString.of((byte)1, (byte)2, (byte)3));
				}
				@Override
				public void onClosing(okhttp3.WebSocket ws, int code, String reason) {
					closeCode.set(code);
					ws.close(code, null);
					closing.countDown();
				}
			}));
			server.start();

			String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
			TestObserver<Message> observer = WebSocketObservable.builder().uri(url).build().test();
			observer.awaitCount(2);
			observer.assertValueCount(2);
			Assertions.assertEquals("hello", ((WebSocketObservable.TextMessage)observer.values().get(0)).getText());
			Assertions.assertArrayEquals(new byte[] { 1, 2, 3 },
										((WebSocketObservable.BinaryMessage)observer.values().get(1)).getBytes());

			// 구독을 해제하면 연결을 정상 종료 코드로 닫는다.
			observer.dispose();
			Assertions.assertTrue(closing.await(5, TimeUnit.SECONDS), "connection was not closed on dispose");
			Assertions.assertEquals(1000, closeCode.get());
			observer.assertNotComplete();
			observer.assertNoErrors();
		}
	}

	// ---- 크기 상한과 빌더 검증 ----

	private TestObserver<Message> subscribe(okhttp3.WebSocketListener serverListener,
											java.util.function.UnaryOperator<WebSocketObservable.Builder> config)
		throws Exception {
		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse().withWebSocketUpgrade(serverListener));
		server.start();
		String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
		TestObserver<Message> observer = config.apply(WebSocketObservable.builder().uri(url)).build().test();
		Assertions.assertTrue(observer.await(5, TimeUnit.SECONDS), "observable did not terminate");
		server.close();
		return observer;
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void text_over_max_length_signals_error() throws Exception {
		TestObserver<Message> observer = subscribe(new okhttp3.WebSocketListener() {
			@Override
			public void onOpen(okhttp3.WebSocket ws, okhttp3.Response response) {
				ws.send("hello");
			}
			@Override
			public void onClosing(okhttp3.WebSocket ws, int code, String reason) {
				ws.close(code, null);
			}
		}, b -> b.maxTextMessageLength(4));

		observer.assertNoValues();
		observer.assertError(e -> e instanceof IllegalStateException && e.getMessage().contains("maximum length"));
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void binary_over_max_length_signals_error() throws Exception {
		TestObserver<Message> observer = subscribe(new okhttp3.WebSocketListener() {
			@Override
			public void onOpen(okhttp3.WebSocket ws, okhttp3.Response response) {
				ws.send(okio.ByteString.of((byte)1, (byte)2, (byte)3));
			}
			@Override
			public void onClosing(okhttp3.WebSocket ws, int code, String reason) {
				ws.close(code, null);
			}
		}, b -> b.maxBinaryMessageLength(2));

		observer.assertNoValues();
		observer.assertError(e -> e instanceof IllegalStateException && e.getMessage().contains("maximum length"));
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void message_within_max_length_is_delivered() throws Exception {
		TestObserver<Message> observer = subscribe(new okhttp3.WebSocketListener() {
			@Override
			public void onOpen(okhttp3.WebSocket ws, okhttp3.Response response) {
				ws.send("abcd");
				ws.close(1000, "bye");
			}
		}, b -> b.maxTextMessageLength(4));

		observer.assertValueCount(1).assertComplete();
	}

	@Test
	public void builder_rejects_invalid_arguments() {
		WebSocketObservable.Builder b = WebSocketObservable.builder();
		Assertions.assertThrows(IllegalArgumentException.class, () -> b.httpClient(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> b.connectTimeout(java.time.Duration.ZERO));
		Assertions.assertThrows(IllegalArgumentException.class, () -> b.closeTimeout(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> b.closeTimeout(java.time.Duration.ZERO));
		Assertions.assertThrows(IllegalArgumentException.class, () -> b.maxTextMessageLength(0));
		Assertions.assertThrows(IllegalArgumentException.class, () -> b.maxBinaryMessageLength(-1));
		Assertions.assertDoesNotThrow(() -> b.connectTimeout(null));
	}

	// ---- 조각 프레임, 접속 시간 제한, 빌더 설정 ----

	private static final RawWebSocketServer.Handler FRAGMENTS = out -> {
		RawWebSocketServer.writeFrame(out, false, RawWebSocketServer.OP_TEXT, "hel".getBytes());
		RawWebSocketServer.writeFrame(out, true, RawWebSocketServer.OP_CONTINUATION, "lo".getBytes());
		RawWebSocketServer.writeFrame(out, false, RawWebSocketServer.OP_BINARY, new byte[] { 1, 2 });
		RawWebSocketServer.writeFrame(out, true, RawWebSocketServer.OP_CONTINUATION, new byte[] { 3 });
	};

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void fragments_are_collected_by_default() throws Exception {
		try ( RawWebSocketServer server = new RawWebSocketServer(true, FRAGMENTS) ) {
			TestObserver<Message> observer = WebSocketObservable.builder().uri(server.url())
												.closeTimeout(java.time.Duration.ofMillis(200)).build().test();
			observer.awaitCount(2);
			observer.assertValueCount(2);
			WebSocketObservable.TextMessage text = (WebSocketObservable.TextMessage)observer.values().get(0);
			Assertions.assertEquals("hello", text.getText());
			Assertions.assertTrue(text.isLast());
			Assertions.assertArrayEquals(new byte[] { 1, 2, 3 },
									((WebSocketObservable.BinaryMessage)observer.values().get(1)).getBytes());
			observer.dispose();
		}
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void fragments_are_emitted_one_by_one_when_not_collected() throws Exception {
		try ( RawWebSocketServer server = new RawWebSocketServer(true, FRAGMENTS) ) {
			TestObserver<Message> observer = WebSocketObservable.builder().uri(server.url())
												.collectTextFragments(false).collectBinaryFragments(false)
												.closeTimeout(java.time.Duration.ofMillis(200)).build().test();
			observer.awaitCount(4);
			observer.assertValueCount(4);
			List<Message> values = observer.values();
			Assertions.assertEquals("hel", ((WebSocketObservable.TextMessage)values.get(0)).getText());
			Assertions.assertFalse(((WebSocketObservable.TextMessage)values.get(0)).isLast());
			Assertions.assertEquals("lo", ((WebSocketObservable.TextMessage)values.get(1)).getText());
			Assertions.assertTrue(((WebSocketObservable.TextMessage)values.get(1)).isLast());
			Assertions.assertFalse(((WebSocketObservable.BinaryMessage)values.get(2)).isLast());
			Assertions.assertTrue(((WebSocketObservable.BinaryMessage)values.get(3)).isLast());
			observer.dispose();
		}
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void connect_timeout_signals_error() throws Exception {
		// 업그레이드 요청에 응답하지 않는 서버
		try ( RawWebSocketServer server = new RawWebSocketServer(false, null) ) {
			TestObserver<Message> observer = WebSocketObservable.builder().uri(server.url())
												.connectTimeout(java.time.Duration.ofMillis(300)).build().test();
			Assertions.assertTrue(observer.await(5, TimeUnit.SECONDS), "observable did not terminate");
			observer.assertError(e -> e instanceof java.net.http.HttpTimeoutException);
		}
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void builder_customizer_adds_handshake_header() throws Exception {
		try ( MockWebServer server = new MockWebServer() ) {
			server.enqueue(new MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener() {
				@Override
				public void onOpen(okhttp3.WebSocket ws, okhttp3.Response response) {
					ws.close(1000, "bye");
				}
				@Override
				public void onClosing(okhttp3.WebSocket ws, int code, String reason) {
					ws.close(code, null);
				}
			}));
			server.start();
			String url = "ws://" + server.getHostName() + ":" + server.getPort() + "/ws";
			TestObserver<Message> observer = WebSocketObservable.builder().uri(url)
												.webSocketBuilderCustomizer(b -> b.header("X-Token", "secret"))
												.build().test();
			Assertions.assertTrue(observer.await(5, TimeUnit.SECONDS));

			Assertions.assertEquals("secret", server.takeRequest(1, TimeUnit.SECONDS).getHeader("X-Token"));
		}
	}

	@Test
	public void closeTimeout_rejects_sub_millisecond() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> WebSocketObservable.builder().closeTimeout(java.time.Duration.ofNanos(1)));
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void exception_while_building_connection_is_signalled_to_subscriber() throws Exception {
		// onSubscribe 뒤의 예외는 구독자에게 onError로 가야 한다. 그러지 않으면 구독이 끝나지 않는다.
		IllegalArgumentException failure = new IllegalArgumentException("bad header");
		TestObserver<Message> observer = WebSocketObservable.builder().uri("ws://127.0.0.1:1/ws")
											.webSocketBuilderCustomizer(b -> { throw failure; })
											.build().test();

		Assertions.assertTrue(observer.await(2, TimeUnit.SECONDS), "observable did not terminate");
		observer.assertError(failure);
	}
}
