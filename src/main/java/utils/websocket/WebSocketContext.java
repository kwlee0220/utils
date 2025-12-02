package utils.websocket;

import java.net.http.WebSocket;

import utils.statechart.StateContext;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketContext implements StateContext {
	private final String m_serverUrl;
	private WebSocket m_webSocket;

	public WebSocketContext(String serverUrl) {
		m_serverUrl = serverUrl;
	}

	public String getServerUrl() {
		return m_serverUrl;
	}

	public WebSocket getWebSocket() {
		return m_webSocket;
	}

	public void setWebSocket(WebSocket webSocket) {
		m_webSocket = webSocket;
	}
}
