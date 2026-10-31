package utils.websocket;

import java.net.http.WebSocket;

import utils.Preconditions;
import utils.statechart.Signal;


/**
 * WebSocket 상태 차트가 주고받는 신호들.
 * <p>
 * {@link Connected}와 {@link ConnectionFailed}는 {@link States.WebSocketConnectingState}가 접속 결과로 보내고, 나머지는
 * {@link WebSocketListener}가 JDK {@link WebSocket.Listener} 콜백을 바꿔 보낸다. 처리되지 않은
 * {@link WebSocketError}와 {@link ConnectionClosed}는 {@link WebSocketStateChart}의 대체 종료가 처리한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Signals {
	private Signals() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}
	
	/** 접속에 성공했음을 알리는 신호. 수립된 연결은 이미 차트에 등록되어 있다. */
	public static class Connected extends WebSocketSignal {
		/**
		 * 신호를 생성한다.
		 *
		 * @param webSocket	수립된 연결 (non-null)
		 */
		public Connected(WebSocket webSocket) {
			super(webSocket);
		}
		
		@Override
		public String toString() {
			return "Connected";
		}
	}
	/** 접속에 실패했음을 알리는 신호. 연결이 없으므로 {@link WebSocketSignal}이 아니다. */
	public static class ConnectionFailed implements Signal {
		private final Throwable m_cause;
		
		/**
		 * 신호를 생성한다.
		 *
		 * @param cause	접속 실패 원인. {@code CompletionException} 등의 포장을 벗긴 실제 예외 (non-null).
		 * @throws IllegalArgumentException	{@code cause}가 {@code null}인 경우.
		 */
		public ConnectionFailed(Throwable cause) {
			Preconditions.checkNotNullArgument(cause, "cause is null");
			m_cause = cause;
		}
		
		/**
		 * 접속 실패 원인을 반환한다.
		 *
		 * @return	실패 원인
		 */
		public Throwable getCause() {
			return m_cause;
		}
		
		@Override
		public String toString() {
			return "ConnectionFailed: cause=" + m_cause;
		}
	}
	
	/**
	 * 텍스트 메시지 수신 신호.
	 * <p>
	 * 조각(fragment)으로 나뉘어 온 메시지는 마지막 조각까지 모아 완성된 메시지 하나로 전달한다.
	 * 모으는 길이에는 상한이 있다({@link WebSocketStateChart#setMaxTextMessageLength(int)}).
	 */
	public static class TextMessage extends WebSocketSignal {
		/** {@link #toString()}에 보여 줄 본문의 최대 길이. 긴 메시지가 로그를 채우지 않게 한다. */
		private static final int TO_STRING_MAX_LENGTH = 200;

		private final String m_text;

		/**
		 * 신호를 생성한다.
		 *
		 * @param webSocket	메시지를 받은 연결 (non-null)
		 * @param text		완성된 텍스트 메시지 (non-null)
		 * @throws IllegalArgumentException	{@code text}가 {@code null}인 경우
		 */
		public TextMessage(WebSocket webSocket, String text) {
			super(webSocket);
			Preconditions.checkNotNullArgument(text, "text is null");
			
			m_text = text;
		}

		/**
		 * 받은 텍스트 메시지를 반환한다. ({@code getMessage()}였다가 2026-10-31에 바뀌었다.
		 * {@link WebSocketObservable.TextMessage#getText()}와 이름을 맞췄다.)
		 *
		 * @return	텍스트 메시지
		 */
		public String getText() {
			return m_text;
		}
		
		/**
		 * 본문은 앞의 200자까지만 보여 준다. 차트가 받은 신호를 로그로 남길 때 긴 메시지가 로그를 채우지 않게 하기 위해서다.
		 */
		@Override
		public String toString() {
			return String.format("TextMessage[text=%s]", abbreviate(m_text));
		}

		/**
		 * 로그용으로 텍스트의 앞부분만 남긴다. 200자를 넘으면 앞의 200자와 전체 길이를 보여 준다.
		 *
		 * @param text	텍스트
		 * @return	줄인 텍스트
		 */
		static String abbreviate(String text) {
			if ( text == null || text.length() <= TO_STRING_MAX_LENGTH ) {
				return text;
			}
			return text.substring(0, TO_STRING_MAX_LENGTH) + "..., length=" + text.length();
		}
	}
	
	/**
	 * 바이너리 데이터 수신 신호.
	 * <p>
	 * {@link TextMessage}와 달리 <b>조각(fragment)마다</b> 하나씩 전달하며 모으지 않는다. 메시지 전체가 필요하면
	 * 상태가 {@link #isLast()}가 {@code true}인 조각까지 직접 합친다. 대용량 바이너리를 메모리에 모두 모으지 않고
	 * 조각 단위로 흘려 처리할 수 있게 하려는 의도적인 선택이다(검토 결과 2026-10-31에 유지하기로 함).
	 * 리스너가 모으지 않으므로 누적 상한도 없다. 상태가 조각을 모은다면 상한은 그 상태가 정한다.
	 */
	public static class BinaryMessage extends WebSocketSignal {
		private final byte[] m_bytes;
		private final boolean m_last;

		/**
		 * 신호를 생성한다.
		 *
		 * @param webSocket	데이터를 받은 연결 (non-null)
		 * @param bytes		받은 조각의 바이트 (non-null)
		 * @param last		메시지의 마지막 조각인지 여부
		 * @throws IllegalArgumentException	{@code bytes}가 {@code null}인 경우
		 */
		public BinaryMessage(WebSocket webSocket, byte[] bytes, boolean last) {
			super(webSocket);
			Preconditions.checkNotNullArgument(bytes, "bytes is null");
			
			m_bytes = bytes;
			m_last = last;
		}

		/**
		 * 받은 조각의 바이트를 반환한다. 복사본이 아니라 내부 배열이다.
		 *
		 * @return	조각의 바이트
		 */
		public byte[] getBytes() {
			return m_bytes;
		}
		
		/**
		 * 메시지의 마지막 조각인지 여부를 반환한다.
		 *
		 * @return	마지막 조각이면 {@code true}
		 */
		public boolean isLast() {
			return m_last;
		}
		
		@Override
		public String toString() {
			return String.format("BinaryMessage[size=%d, last=%s]", m_bytes.length, m_last);
		}
	}
	
	/**
	 * 연결 오류 신호. JDK {@code onError}, pong 시간 초과, 텍스트 메시지 길이 초과 때 전달된다.
	 * 처리되지 않으면 차트가 그 오류로 실패한다. (수신 메시지가 아니라 오류를 나타내므로 2026-10-31에
	 * {@code ErrorMessage}에서 이름을 바꾸었다.)
	 */
	public static class WebSocketError extends WebSocketSignal {
		private final Throwable m_error;

		/**
		 * 오류 신호를 생성한다.
		 *
		 * @param webSocket	오류가 발생한 WebSocket.
		 * @param error		오류 원인. 처리되지 않으면 차트의 실패 원인이 되므로 {@code null}이면 안 된다.
		 * @throws IllegalArgumentException	{@code webSocket}이나 {@code error}가 {@code null}인 경우.
		 */
		public WebSocketError(WebSocket webSocket, Throwable error) {
			super(webSocket);
			Preconditions.checkNotNullArgument(error, "error is null");
			
			m_error = error;
		}

		/**
		 * 오류 원인을 반환한다.
		 *
		 * @return	오류 원인 (non-null)
		 */
		public Throwable getError() {
			return m_error;
		}
		
		@Override
		public String toString() {
			return String.format("WebSocketError[error=%s]", "" + m_error);
		}
	}
	
	/**
	 * 상대가 close 프레임을 보내 연결이 닫혔음을 알리는 신호. 처리되지 않으면 상태 코드에 따라 차트가
	 * 취소되거나 {@link WebSocketClosedException}으로 실패한다.
	 */
	public static class ConnectionClosed extends WebSocketSignal {
		private final int m_statusCode;
		private final String m_reason;

		/**
		 * 신호를 생성한다.
		 *
		 * @param webSocket		닫힌 연결 (non-null)
		 * @param statusCode	상대가 보낸 close 상태 코드
		 * @param reason		상대가 보낸 close 사유
		 */
		public ConnectionClosed(WebSocket webSocket, int statusCode, String reason) {
			super(webSocket);
			
			m_statusCode = statusCode;
			m_reason = reason;
		}

		/**
		 * close 상태 코드를 반환한다.
		 *
		 * @return	상태 코드
		 */
		public int getStatusCode() {
			return m_statusCode;
		}

		/**
		 * close 사유를 반환한다.
		 *
		 * @return	close 사유
		 */
		public String getReason() {
			return m_reason;
		}
		
		@Override
		public String toString() {
			return String.format("ConnectionClosed[statusCode=%d, reason=%s]", m_statusCode, m_reason);
		}
	}
}
