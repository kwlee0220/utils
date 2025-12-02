package utils.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import utils.func.FOption;
import utils.statechart.Signal;
import utils.statechart.StateMachine;
import utils.statechart.Transition;
import utils.websocket.Signals.ConnectionClosed;
import utils.websocket.Signals.ErrorMessage;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketStateMachine<C extends WebSocketContext> extends StateMachine<C> {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketStateMachine.class);
	
	private final WebSocketListener m_socketListener;
	
	public WebSocketStateMachine(C context) {
		super(context);
		
		setLogger(s_logger);
		m_socketListener = new WebSocketListener(this);
	}
	
	public void setPingInterval(Duration interval) {
		m_socketListener.setPingInterval(interval);
	}
	
	public void setPongTimeout(Duration timeout) {
		m_socketListener.setPongTimeout(timeout);
	}

	@Override
	public void start() {
		WebSocketListener driver = new WebSocketListener(this);
		WebSocket webSocket = HttpClient.newHttpClient()
										.newWebSocketBuilder()
										.buildAsync(URI.create(getContext().getServerUrl()), driver)
										.join();
		getContext().setWebSocket(webSocket);
		
		super.start();
	}
	
	public FOption<Transition<C>> handleSignal(Signal signal) {
		Preconditions.checkNotNull(signal, "signal is null");
		
		FOption<Transition<C>> otrans = super.handleSignal(signal);
		if ( otrans.isAbsent() && isRunning() ) {
			// WebSocket Listener에서 onClose, onError 이벤트가 발생한 경우,
			// 해당 이벤트를 처리되지 않은 경우에 대비하여 기본 처리를 수행함.
			// onError: 상태머신을 실패 상태로 전이시킴.
			// onClose: 상태머신을 취소 상태로 전이시킴.
			switch ( signal ) {
				case ErrorMessage errMsg:
					fail(errMsg.getError());
					break;
                case ConnectionClosed closed:
                	cancel(true);
                	break;
                default:
                	break;
			};
		}
		return otrans;
	}
}
