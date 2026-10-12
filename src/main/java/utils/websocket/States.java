package utils.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.statechart.AbstractState;
import utils.statechart.ExceptionState;
import utils.statechart.Signal;
import utils.statechart.SinkState;
import utils.statechart.Transition;
import utils.statechart.Transitions;


/**
 * {@link WebSocketStateChart}를 구성할 때 공통적으로 사용하는 상태 구현들의 모음.
 * <p>
 * WebSocket 연결의 생명주기 중 도메인과 무관한 부분, 즉 연결 수립과 종료 시의 연결 정리를 담당하는
 * 상태들을 제공한다. 수신한 메시지를 해석하고 처리하는 상태는 응용에서 정의하고, 본 클래스가 제공하는
 * 상태들과 엮어 하나의 차트를 구성한다.
 * <ul>
 *   <li>{@link OpenWebSocket} - 서버 접속을 수행하는 상태. 보통 차트의 초기 상태로 사용한다.</li>
 *   <li>{@link CompletedState} / {@link CancelledState} / {@link ErrorState} - 각각 정상 완료, 취소,
 *       오류로 끝나는 종료 상태. 모두 진입 시 WebSocket 연결을 정상 종료한다.</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class States {
	/** 본 클래스는 상태 구현들을 담는 모음일 뿐이므로 인스턴스를 생성하지 않는다. */
	private States() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}

	/**
	 * 종료 상태 진입 시 WebSocket 연결을 정상 종료(close)한다.
	 * <p>
	 * 실제 종료는 {@link WebSocketStateChart#close(int, String)}에 위임하며, 연결이 없거나 close에
	 * 실패하는 경우를 모두 무시하는 best-effort 시맨틱도 그대로 따른다. 종료 상태 진입 도중 발생한
	 * 예외가 상태 전이를 실패로 뒤집지 않도록 하기 위함이다.
	 *
	 * @param context	종료 상태의 컨텍스트
	 * @param reason	close 사유 문자열
	 */
	private static void sendCloseQuietly(WebSocketContext<?> context, String reason) {
		context.getStateChart().close(WebSocket.NORMAL_CLOSURE, reason);
	}

	/**
	 * WebSocket 연결을 수립하는 상태.
	 * <p>
	 * 상태 차트의 초기 상태로 사용하며, 접속할 서버 URL은 컨텍스트
	 * ({@link WebSocketContext#getServerUrl()})에서 얻는다. {@link #enter()}는 접속을 <b>비동기로</b>
	 * 시작하고 즉시 반환하며, 접속 결과는 나중에 신호로 차트에 전달되어 다음 상태를 결정한다.
	 * <ul>
	 *   <li>접속 성공 - 수립된 {@link WebSocket}을 차트에 등록한 뒤 {@link Signals.Connected}를 전달하고,
	 *       이 신호를 받아 {@code connectedStatePath} 상태로 전이한다.</li>
	 *   <li>접속 실패 - {@link Signals.ConnectionFailed}를 전달하고, 이 신호를 받아
	 *       {@code failedStatePath} 상태로 전이한다.</li>
	 * </ul>
	 * 접속 결과 처리는 {@code whenCompleteAsync}로 등록되기 때문에, 위 신호들은 {@link #enter()}를
	 * 호출한 스레드가 아니라 별도의 스레드에서 차트에 전달된다.
	 * <p>
	 * 단, 서버 URL의 형식이 잘못된 경우는 접속 시도 이전에 걸러지기 때문에
	 * {@link Signals.ConnectionFailed}가 아니라 {@link #enter()}에서 던져지는
	 * {@link IllegalArgumentException}으로 드러난다.
	 * <p>
	 * 본 상태는 {@link LoggerSettable}을 구현하기 때문에 접속 과정의 로그를 남길 로거를 교체할 수 있다.
	 *
	 * @param <C>	상태 차트의 컨텍스트 타입. (CRTP self-bound)
	 */
	public static class OpenWebSocket<C extends WebSocketContext<C>> extends AbstractState<C> implements LoggerSettable {
		/** 로거가 따로 설정되지 않은 경우에 사용할 기본 로거. */
		private final static Logger s_logger = LoggerFactory.getLogger(OpenWebSocket.class);

		/** 본 상태가 속한 상태 차트. 접속에 사용할 listener를 제공하고 접속 결과 신호를 전달받는다. */
		private final WebSocketStateChart<C> m_machine;
		/** {@link Signals.Connected} 수신 시, 즉 접속에 성공한 경우 전이할 상태의 경로. */
		private final String m_connectedStatePath;
		/** {@link Signals.ConnectionFailed} 수신 시, 즉 접속에 실패한 경우 전이할 상태의 경로. */
		private final String m_failedStatePath;
		/** {@link #setLogger(Logger)}로 설정된 로거. 설정된 적이 없으면 {@code null}. */
		private Logger m_logger;

		/**
		 * WebSocket 연결 상태를 생성한다.
		 *
		 * @param path					본 상태의 경로
		 * @param context				상태 차트의 컨텍스트. 접속할 서버 URL을 제공한다.
		 * @param machine				본 상태가 속한 WebSocket 상태 차트. 접속에 사용할 listener를 제공하고
		 * 								접속 결과 신호를 전달받는다.
		 * @param connectedStatePath	접속에 성공한 경우 전이할 상태의 경로
		 * @param failedStatePath		접속에 실패한 경우 전이할 상태의 경로
		 */
		public OpenWebSocket(String path, C context, WebSocketStateChart<C> machine,
							String connectedStatePath, String failedStatePath) {
			super(path, context);

			m_machine = machine;
			m_connectedStatePath = connectedStatePath;
			m_failedStatePath = failedStatePath;
		}

		/**
		 * 컨텍스트에 설정된 서버로의 WebSocket 접속을 비동기로 시작한다.
		 * <p>
		 * 접속 완료를 기다리지 않고 즉시 반환하며, 접속 결과는 {@link Signals.Connected} 또는
		 * {@link Signals.ConnectionFailed} 신호로 차트에 전달된다.
		 *
		 * @throws IllegalArgumentException	컨텍스트의 서버 URL이 올바른 URI 형식이 아닌 경우
		 */
		@Override
		public void enter() {
			CompletableFuture<WebSocket> future
							= HttpClient.newHttpClient()
										.newWebSocketBuilder()
										.buildAsync(URI.create(getContext().getServerUrl()),
													m_machine.getWebSocketListener());
			future.whenCompleteAsync((ws, err) -> {
				if ( err != null ) {
					getLogger().error("failed to open WebSocket: {}", ""+err);
					m_machine.handleSignal(new Signals.ConnectionFailed(err));
				}
				else {
					getLogger().info("WebSocket connected: {}", getContext().getServerUrl());
					m_machine.setWebSocket(ws);
					m_machine.handleSignal(new Signals.Connected(ws));
				}
			});
		}

		/**
		 * 접속 결과 신호에 따라 다음 전이를 결정한다.
		 *
		 * @param signal	수신된 신호
		 * @return	{@link Signals.Connected}인 경우는 {@code connectedStatePath}로,
		 * 			{@link Signals.ConnectionFailed}인 경우는 {@code failedStatePath}로 전이하는 transition.
		 * 			그 외의 신호는 본 상태에서 처리하지 않기 때문에 {@link Optional#empty()}.
		 */
		@Override
		public Optional<Transition<C>> selectTransition(Signal signal) {
			if ( signal instanceof Signals.Connected ) {
				return Optional.of(Transitions.noop(m_connectedStatePath));
			}
			else if ( signal instanceof Signals.ConnectionFailed ) {
				return Optional.of(Transitions.noop(m_failedStatePath));
			}

			return Optional.empty();
		}

		/**
		 * 접속 과정의 로그를 남길 로거를 반환한다.
		 *
		 * @return	{@link #setLogger(Logger)}로 설정된 로거. 설정된 적이 없으면 본 클래스의 기본 로거.
		 */
		@Override
		public Logger getLogger() {
			return (m_logger != null) ? m_logger : s_logger;
		}

		@Override
		public void setLogger(Logger logger) {
			m_logger = logger;
		}
	}

	/**
	 * 시나리오가 정상적으로 완료되어 도달하는 종료 상태.
	 * <p>
	 * {@link SinkState}를 확장하기 때문에 진입 이후로는 어떤 신호를 받아도 전이하지 않는다.
	 * 진입 시 WebSocket 연결을 정상 종료(normal closure)하되, 연결이 아직 수립되지 않았거나 이미
	 * 닫혀 있어 close 전송이 실패하는 경우는 무시한다. 연결 정리 과정에서 발생한 문제가 정상 완료를
	 * 실패로 뒤집지 않도록 하기 위함이다.
	 *
	 * @param <C>	상태 차트의 컨텍스트 타입. (CRTP self-bound)
	 */
	public static class CompletedState<C extends WebSocketContext<C>> extends SinkState<C> {
		/**
		 * 정상 완료 종료 상태를 생성한다.
		 *
		 * @param path		본 상태의 경로
		 * @param context	상태 차트의 컨텍스트
		 */
		public CompletedState(String path, C context) {
			super(path, context);
		}

		/**
		 * WebSocket 연결을 정상 종료한다. 종료에 실패하더라도 예외를 던지지 않는다.
		 */
		@Override
		public void enter() {
			sendCloseQuietly(getContext(), "normal closure");
		}
	}

	/**
	 * 사용자의 취소 요청 등으로 시나리오가 중단되어 도달하는 종료 상태.
	 * <p>
	 * 종료 사유만 다를 뿐 연결 정리 동작은 {@link CompletedState}와 동일하다. 취소 역시 프로토콜 상으로는
	 * 정상적인 연결 종료이기 때문에 normal closure로 닫는다.
	 *
	 * @param <C>	상태 차트의 컨텍스트 타입. (CRTP self-bound)
	 */
	public static class CancelledState<C extends WebSocketContext<C>> extends SinkState<C> {
		/**
		 * 취소 종료 상태를 생성한다.
		 *
		 * @param path		본 상태의 경로
		 * @param context	상태 차트의 컨텍스트
		 */
		public CancelledState(String path, C context) {
			super(path, context);
		}

		/**
		 * WebSocket 연결을 정상 종료한다. 종료에 실패하더라도 예외를 던지지 않는다.
		 */
		@Override
		public void enter() {
			sendCloseQuietly(getContext(), "normal closure");
		}
	}

	/**
	 * 오류로 인해 시나리오가 중단되어 도달하는 종료 상태.
	 * <p>
	 * {@link ExceptionState}를 확장하여 실패 원인을 보유하며, 진입 시 WebSocket 연결을 닫는다.
	 * 연결 자체는 정상 종료 코드로 닫고 사유 문자열로만 오류였음을 알린다.
	 *
	 * @param <C>	상태 차트의 컨텍스트 타입. (CRTP self-bound)
	 */
	public static class ErrorState<C extends WebSocketContext<C>> extends ExceptionState<C> {
		/**
		 * 오류 종료 상태를 생성한다.
		 *
		 * @param path		본 상태의 경로
		 * @param context	상태 차트의 컨텍스트
		 */
		public ErrorState(String path, C context) {
			super(path, context);
		}

		/**
		 * WebSocket 연결을 종료한다. 종료에 실패하더라도 예외를 던지지 않는다.
		 */
		@Override
		public void enter() {
			sendCloseQuietly(getContext(), "closure due to a error");
		}
	}
}
