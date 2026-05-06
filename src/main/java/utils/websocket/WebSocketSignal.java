package utils.websocket;

import java.net.http.WebSocket;

import utils.Utilities;
import utils.statechart.Signal;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketSignal implements Signal {
	private final WebSocket m_webSocket;
	
	public WebSocketSignal(WebSocket webSocket) {
		Utilities.checkNotNullArgument(webSocket, "webSocket is null");
		
		m_webSocket = webSocket;
	}
	
	public WebSocket getWebSocket() {
		return m_webSocket;
	}
}
