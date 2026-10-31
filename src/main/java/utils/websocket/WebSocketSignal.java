package utils.websocket;

import java.net.http.WebSocket;

import utils.Preconditions;
import utils.statechart.Signal;


/**
 * 특정 {@link WebSocket} 연결에서 발생한 사건을 나타내는 신호의 기반 클래스.
 * <p>
 * {@link WebSocketListener}가 JDK 콜백을 이 타입의 신호({@link Signals}의 하위 클래스)로 바꿔 차트에 전달한다.
 * 상태는 {@link #getWebSocket()}으로 신호가 온 연결을 얻어 응답을 보낼 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketSignal implements Signal {
	private final WebSocket m_webSocket;
	
	/**
	 * 신호를 생성한다.
	 *
	 * @param webSocket	신호가 발생한 연결 (non-null)
	 * @throws IllegalArgumentException	{@code webSocket}이 {@code null}인 경우
	 */
	public WebSocketSignal(WebSocket webSocket) {
		Preconditions.checkNotNullArgument(webSocket, "webSocket is null");
		
		m_webSocket = webSocket;
	}
	
	/**
	 * 신호가 발생한 연결을 반환한다.
	 *
	 * @return	WebSocket 연결
	 */
	public WebSocket getWebSocket() {
		return m_webSocket;
	}
}
