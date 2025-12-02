package utils.websocket;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.statechart.StateMachine;
import utils.websocket.Signals.BinaryMessage;
import utils.websocket.Signals.ConnectionClosed;
import utils.websocket.Signals.ErrorMessage;
import utils.websocket.Signals.TextMessage;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
class WebSocketListener implements WebSocket.Listener {
	private static final Logger s_logger = LoggerFactory.getLogger(WebSocketListener.class);
	
	private final StateMachine<?> m_stateMachine;
	
	private Duration m_pingInterval;
	private Duration m_pongTimeout;
	private Timer m_pingTimer;
	private final AtomicLong m_lastPongTime = new AtomicLong(0);
	
	WebSocketListener(WebSocketStateMachine<?> machine) {
		m_stateMachine = machine;
	}
	
	public void setPingInterval(Duration interval) {
		m_pingInterval = interval;
	}
	
	public void setPongTimeout(Duration timeout) {
		m_pongTimeout = timeout;
	}
	
	@Override
	public void onOpen(WebSocket webSocket) {
		s_logger.info("connected: {}", webSocket.getSubprotocol());

		webSocket.request(1);
		if ( m_pingInterval != null && m_pongTimeout != null ) {
			s_logger.info("starting ping: interval={}, timeout={}", m_pingInterval, m_pongTimeout);
			startPing(webSocket);
		}
	}
	
	private void startPing(WebSocket webSocket) {
		m_lastPongTime.set(System.currentTimeMillis());
		
		m_pingTimer = new Timer("WebSocket-Ping-Timer", true);
		m_pingTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				long now = System.currentTimeMillis();
				if ( now - m_lastPongTime.get() > m_pongTimeout.toMillis() ) {
					s_logger.warn("pong timeout: lastPongTime={}", m_lastPongTime.get());
					m_stateMachine.handleSignal(new ErrorMessage(null, new RuntimeException("pong timeout")));
					cancel();
					return;
				}

				s_logger.debug("sending ping");
				webSocket.sendPing(ByteBuffer.wrap(new byte[] { 1, 2, 3, 4 }));
			}
		}, m_pingInterval.toMillis(), m_pingInterval.toMillis());
		
		m_stateMachine.whenFinished(result -> {
			m_pingTimer.cancel();
		});
	}
	
	private StringBuilder m_textMessageBuilder = new StringBuilder();
	
    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String message = data.toString();

    	m_textMessageBuilder.append(message);
        if ( last ) {
			message = m_textMessageBuilder.toString();
			m_textMessageBuilder.setLength(0);
			
	        m_stateMachine.handleSignal(new TextMessage(webSocket, message));
        }
		webSocket.request(1);
        
		return null;
    }

	@Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);
        
		m_stateMachine.handleSignal(new BinaryMessage(webSocket, bytes, last));

		webSocket.request(1);
		return null;
	}
	
	@Override
	public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
		m_lastPongTime.set(System.currentTimeMillis());
		s_logger.info("onPong: {}", message);

		webSocket.request(1);
		return null;
	}

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
		s_logger.info("onError: {}", "" + error);

		m_stateMachine.handleSignal(new ErrorMessage(webSocket, error));
        WebSocket.Listener.super.onError(webSocket, error);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		s_logger.info("closed: statusCode={}, reason={}", statusCode, reason);

		m_stateMachine.handleSignal(new ConnectionClosed(webSocket, statusCode, reason));

		return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }
}
