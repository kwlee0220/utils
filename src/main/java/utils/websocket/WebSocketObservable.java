package utils.websocket;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.base.Preconditions;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;

import utils.websocket.WebSocketObservable.Message;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class WebSocketObservable extends Observable<Message> {
	private final URI m_uri;
	private final HttpClient m_client;
	private final boolean m_collectTextFragments;
	private final boolean m_collectBinaryFragments;

	private WebSocketObservable(Builder builder) {
		Preconditions.checkNotNull(builder);
		
		m_uri = builder.m_uri;
		m_client = builder.m_client;
		m_collectTextFragments = builder.m_collectTextFragments;
		m_collectBinaryFragments = builder.m_collectBinaryFragments;
	}

	@Override
	protected void subscribeActual(Observer<? super Message> downstream) {
		// 구독마다 새로운 WebSocket 연결
		WebSocketDisposable disposable = new WebSocketDisposable(downstream);
		downstream.onSubscribe(disposable);

		m_client.newWebSocketBuilder()
				.buildAsync(m_uri, new RxWebSocketListener(disposable))
				.whenComplete((ws, error) -> {
			disposable.setWebSocket(ws);
			if ( error != null ) {
				// 연결 자체가 실패한 경우
				disposable.onError(error);
			}
		});
	}
	
	public static Builder builder() {
		return new Builder();
	}
	public static class Builder {
		private URI m_uri;
		private HttpClient m_client = HttpClient.newHttpClient();
		private boolean m_collectTextFragments = true;
		private boolean m_collectBinaryFragments = true;

		public Builder uri(String url) {
			m_uri = URI.create(url);
			return this;
		}

		public Builder httpClient(HttpClient client) {
			m_client = client;
			return this;
		}
		
		public Builder collectTextFragments(boolean collect) {
			m_collectTextFragments = collect;
			return this;
		}
		
		public Builder collectBinaryFragments(boolean collect) {
			m_collectBinaryFragments = collect;
			return this;
		}

		public WebSocketObservable build() {
			Objects.requireNonNull(m_uri, "uri should be set");
			return new WebSocketObservable(this);
		}
	}
	
	public static interface Message { };

	public static class TextMessage implements Message {
		private final String m_text;
		private final boolean m_last;
		
		public TextMessage(String text, boolean last) {
			m_text = text;
			m_last = last;
		}
		
		public String getText() {
			return m_text;
		}
		
		public boolean isLast() {
			return m_last;
		}
	};
	
	public static class BinaryMessage implements Message {
		private final ByteBuffer m_data;
		private final boolean m_last;

		public BinaryMessage(ByteBuffer data, boolean last) {
			m_data = data;
			m_last = last;
		}

		public ByteBuffer getData() {
			return m_data;
		}
		
		public byte[] getBytes() {
			byte[] bytes = new byte[m_data.remaining()];
			m_data.get(bytes);
			return bytes;
		}

		public boolean isLast() {
			return m_last;
		}
	};

	/**
	 * WebSocket 연결 및 dispose 를 관리하는 Disposable.
	 */
	private static final class WebSocketDisposable implements Disposable {
		private final Observer<? super Message> m_downstream;
		private final AtomicBoolean m_disposed = new AtomicBoolean(false);

		// 나중에 buildAsync 완료 시 세팅
		private volatile WebSocket m_webSocket;

		private WebSocketDisposable(Observer<? super Message> downstream) {
			this.m_downstream = downstream;
		}

		void setWebSocket(WebSocket webSocket) {
			this.m_webSocket = webSocket;
		}

		void onMessage(Message message) {
			if ( !m_disposed.get() ) {
				m_downstream.onNext(message);
			}
		}

		void onError(Throwable t) {
			if ( m_disposed.compareAndSet(false, true) ) {
				m_downstream.onError(t);
				closeQuietly(WebSocket.NORMAL_CLOSURE, "error");
			}
		}

		void onComplete(int statusCode, String reason) {
			if ( m_disposed.compareAndSet(false, true) ) {
				m_downstream.onComplete();
				closeQuietly(statusCode, reason);
			}
		}

		@Override
		public void dispose() {
			if ( m_disposed.compareAndSet(false, true) ) {
				closeQuietly(WebSocket.NORMAL_CLOSURE, "disposed");
			}
		}

		@Override
		public boolean isDisposed() {
			return m_disposed.get();
		}

		private void closeQuietly(int statusCode, String reason) {
			WebSocket ws = m_webSocket;
			if ( ws != null ) {
				try {
					ws.sendClose(statusCode, reason);
				}
				catch ( Exception ignored ) {
				}
			}
		}
	}

	/**
	 * java.net.http.WebSocket.Listener 구현체. 콜백에서 Disposable을 통해 Rx Observer로 이벤트
	 * 전달.
	 */
	private final class RxWebSocketListener implements Listener {
		private final WebSocketDisposable m_disposable;
		private final StringBuilder m_textMessageBuilder = new StringBuilder();
		private final ByteArrayOutputStream m_binaryMessageCollector = new ByteArrayOutputStream();

		RxWebSocketListener(WebSocketDisposable disposable) {
			this.m_disposable = disposable;
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			// 처음에 요청할 메시지 개수 지정 (backpressure 유사)
			webSocket.request(1);
			Listener.super.onOpen(webSocket);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			if ( !m_disposable.isDisposed() && last ) {
				String str = data.toString();
				if ( m_collectTextFragments ) {
					m_textMessageBuilder.append(str);
					
					if ( last ) {
						str = m_textMessageBuilder.toString();
						m_textMessageBuilder.setLength(0);
						m_disposable.onMessage(new TextMessage(str, last));
					}
				}
				else {
					m_disposable.onMessage(new TextMessage(str, last));
				}
			}
			
			// 다음 메시지 1개 더 요청
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
			if ( !m_disposable.isDisposed() && last ) {
				if ( m_collectBinaryFragments ) {
					byte[] bytes = new byte[data.remaining()];
					data.get(bytes);
					try {
						m_binaryMessageCollector.write(bytes);
					}
					catch ( Exception e ) {
						onError(webSocket, e);
						return CompletableFuture.completedFuture(null);
					}

					if ( last ) {
						ByteBuffer fullData = ByteBuffer.wrap(m_binaryMessageCollector.toByteArray());
						m_binaryMessageCollector.reset();
						m_disposable.onMessage(new BinaryMessage(fullData, last));
					}
				}
				else {
					m_disposable.onMessage(new BinaryMessage(data, last));
				}
			}
			
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			m_disposable.onComplete(statusCode, reason);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			m_disposable.onError(error);
		}

		@Override
		public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
			webSocket.sendPong(message);
	        return WebSocket.Listener.super.onPing(webSocket, message);
	    }
	}
}
