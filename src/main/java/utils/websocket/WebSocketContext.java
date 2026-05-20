package utils.websocket;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.statechart.StateChart;
import utils.statechart.StateContext;


/**
 * WebSocket 상태 차트의 도메인 컨텍스트.
 * <p>
 * {@link WebSocketStateChart}와 짝을 이루며 연결 대상 서버 URL을 보유한다.
 * self-bounded CRTP({@code <C extends WebSocketContext<C>>})를 채택하여 서브클래스가
 * 자기 타입을 명시함으로써 도메인별 추가 데이터를 갖는 WebSocket 컨텍스트를 정의할 수 있다
 * (예: {@code class MyWsContext extends WebSocketContext<MyWsContext>}).
 * <p>
 * 본 컨텍스트는 {@link WebSocketStateChart}의 생성자에서 정확히 한 번
 * {@link #setStateChart(StateChart)}로 등록되며, 이후 {@link #getStateChart()}로 조회된다.
 *
 * @param <C>	서브클래스의 자기 타입 (CRTP self-bound)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketContext<C extends WebSocketContext<C>> implements StateContext<C> {
	@NotNull private final String m_serverUrl;
	private @Nullable WebSocketStateChart<C> m_machine;

	/**
	 * 주어진 서버 URL로 컨텍스트를 생성한다.
	 * <p>
	 * URL 문자열의 형식 검증은 수행하지 않으며, 잘못된 URL은 실제 연결을 시도하는
	 * {@code OpenWebSocket.enter()}에서 {@link java.net.URI#create(String)} 호출 시점에
	 * {@link IllegalArgumentException}으로 감지된다.
	 *
	 * @param serverUrl		연결할 WebSocket 서버 URL (non-null)
	 * @throws IllegalArgumentException {@code serverUrl}이 {@code null}인 경우
	 */
	public WebSocketContext(String serverUrl) {
		Preconditions.checkNotNullArgument(serverUrl, "serverUrl is null");

		m_serverUrl = serverUrl;
	}

	/**
	 * 본 컨텍스트가 보유한 WebSocket 서버 URL을 반환한다.
	 *
	 * @return	서버 URL (non-null)
	 */
	public @NotNull String getServerUrl() {
		return m_serverUrl;
	}

	/**
	 * 본 컨텍스트가 속한 {@link WebSocketStateChart}를 반환한다.
	 * <p>
	 * 반환 타입은 {@link StateContext#getStateChart()}의 covariant override로 좁혀져 있어
	 * 호출자는 캐스팅 없이 {@link WebSocketStateChart} 고유 메소드(예: {@code getWebSocket()},
	 * {@code sendText(...)})에 접근할 수 있다.
	 *
	 * @return	등록된 {@link WebSocketStateChart}. {@link #setStateChart(StateChart)} 호출
	 * 			전이면 {@code null}.
	 */
	@Override
	public @Nullable WebSocketStateChart<C> getStateChart() {
		return m_machine;
	}

	/**
	 * 본 컨텍스트에 {@link WebSocketStateChart}를 등록한다.
	 * <p>
	 * 본 메소드는 {@link WebSocketStateChart} 생성자에서 정확히 한 번 호출되는
	 * 프레임워크 내부용 콜백이다. 외부 코드에서 직접 호출하지 않는다.
	 * <p>
	 * 인자가 {@link WebSocketStateChart}의 인스턴스가 아니거나 이미 다른 차트가
	 * 등록된 컨텍스트에 다시 호출되는 경우 예외를 던져 거부한다.
	 *
	 * @param machine	등록할 {@link StateChart}. 실제 런타임 타입은 반드시
	 *					{@link WebSocketStateChart}여야 한다.
	 * @throws IllegalArgumentException {@code machine}이 {@code null}이거나
	 *					{@link WebSocketStateChart}의 인스턴스가 아닌 경우
	 * @throws IllegalStateException	이미 {@link StateChart}가 등록된 경우
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void setStateChart(StateChart<C> machine) {
		Preconditions.checkNotNullArgument(machine, "machine is null");
		Preconditions.checkState(m_machine == null, "StateChart is already set on this context");
		Preconditions.checkArgument(machine instanceof WebSocketStateChart<?>,
								"machine is not a WebSocketStateChart: %s", machine);

		WebSocketStateChart<C> wsMachine = (WebSocketStateChart<C>)machine;
		m_machine = wsMachine;
	}
}
