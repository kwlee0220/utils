package utils.websocket;

import utils.statechart.StateContext;
import utils.statechart.StateChart;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketContext implements StateContext {
	private final String m_serverUrl;
	private WebSocketStateChart<WebSocketContext> m_machine;

	public WebSocketContext(String serverUrl) {
		m_serverUrl = serverUrl;
	}

	public String getServerUrl() {
		return m_serverUrl;
	}

	public WebSocketStateChart<WebSocketContext> getStateMachine() {
		return m_machine;
	}

	@Override
	public void setStateMachine(StateChart<? extends StateContext> machine) {
		@SuppressWarnings("unchecked")
		WebSocketStateChart<WebSocketContext> wsMachine
							= (WebSocketStateChart<WebSocketContext>)machine;
		m_machine = wsMachine;
	}
}
