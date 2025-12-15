package utils.websocket;

import java.net.http.WebSocket;

import com.google.common.base.Preconditions;

import utils.statechart.Signal;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketSignal implements Signal {
	private final WebSocket m_webSocket;
	
	public WebSocketSignal(WebSocket webSocket) {
		Preconditions.checkNotNull(webSocket, "webSocket is null");
		
		m_webSocket = webSocket;
	}
	
	public WebSocket getWebSocket() {
		return m_webSocket;
	}
}
