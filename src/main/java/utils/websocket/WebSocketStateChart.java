package utils.websocket;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import utils.statechart.Signal;
import utils.statechart.StateChart;
import utils.statechart.Transition;
import utils.websocket.Signals.ConnectionClosed;
import utils.websocket.Signals.ErrorMessage;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketStateChart<C extends WebSocketContext> extends StateChart<C> {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketStateChart.class);
	
	private final WebSocketListener m_socketListener;
	private WebSocket m_webSocket;
	
	public WebSocketStateChart(C context) {
		super(context);
		
		setLogger(s_logger);
		m_socketListener = new WebSocketListener(this);
	}
	
	public WebSocket getWebSocket() {
		return m_webSocket;
	}
	
	protected void setWebSocket(WebSocket ws) {
		m_webSocket = ws;
	}
	
	public WebSocketListener getWebSocketListener() {
		return m_socketListener;
	}
	
	public void setPingInterval(Duration interval) {
		m_socketListener.setPingInterval(interval);
	}
	
	public void setPongTimeout(Duration timeout) {
		m_socketListener.setPongTimeout(timeout);
	}
	
	public void sendText(String text, boolean last) {
		Preconditions.checkState(isRunning(), "WebSocketStateMachine is not running");
		Preconditions.checkNotNull(m_webSocket, "WebSocket is not connected");

		getLogger().info("send request: {}", text);
		m_webSocket.sendText(text, last);
	}
	
	public void sendBinary(byte[] binary, boolean last) {
		Preconditions.checkState(isRunning(), "WebSocketStateMachine is not running");
		Preconditions.checkNotNull(m_webSocket, "WebSocket is not connected");
		
		m_webSocket.sendBinary(ByteBuffer.wrap(binary), last);
	}
	
	public Optional<Transition<C>> handleSignal(Signal signal) {
		Preconditions.checkNotNull(signal, "signal is null");
		
		Optional<Transition<C>> otrans = super.handleSignal(signal);
		if ( otrans.isEmpty() && isRunning() ) {
			// WebSocket Listener에서 onClose, onError 이벤트가 발생되었지만,
			// 해당 이벤트가 처리되지 않은 경우에 대비하여 fall-back 처리를 수행함.
			// 	onError: 상태머신을 실패 상태로 전이시킴.
			// 	onClose: 상태머신을 취소 상태로 전이시킴.
			switch ( signal ) {
				case ErrorMessage errMsg:		// onError 메시지가 도착한 경우
					fail(errMsg.getError());
					break;
                case ConnectionClosed closed:	// onClose 메시지가 도착한 경우
                	cancel(true);
                	break;
                default:
                	break;
			};
		}
		return otrans;
	}
}
