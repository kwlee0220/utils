package utils.statechart;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import utils.Preconditions;


/**
 * {@link StateContext}의 기본 구현.
 * <p>
 * 차트에 대한 역참조를 보관하고, {@link #setStateChart(StateChart)}의 계약(한 번만 설정할 수 있고, 다시 설정하면
 * {@link IllegalStateException})을 구현한다. 컨텍스트마다 같은 코드를 반복하지 않도록, 도메인 컨텍스트는 이 클래스를
 * 상속하고 도메인 데이터만 더한다.
 * <pre>{@code
 * public class JobContext extends AbstractStateContext<JobContext> {
 *     private int m_progress;
 *     ...
 * }
 * StateChart<JobContext> chart = new StateChart<>(new JobContext());
 * }</pre>
 * 차트의 구체 타입으로 좁혀 반환하거나 연결할 차트의 타입을 검사해야 하면 {@link #getStateChart()}와
 * {@link #setStateChart(StateChart)}를 재정의하고, 재정의한 메소드에서 상위 메소드를 호출한다.
 *
 * @param <C>	자기 자신의 타입 (CRTP self-bound). 예: {@code class Foo extends AbstractStateContext<Foo>}
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractStateContext<C extends StateContext<C>> implements StateContext<C> {
	// 차트 생성자에서 설정되고 다른 스레드(신호를 보내는 스레드 등)에서 읽히므로 volatile로 둔다.
	private volatile @Nullable StateChart<C> m_chart;

	@Override
	public @Nullable StateChart<C> getStateChart() {
		return m_chart;
	}

	/**
	 * 이 컨텍스트가 속할 차트를 등록한다.
	 * <p>
	 * {@link StateChart} 생성자가 한 번 호출하는 프레임워크 내부용 콜백이다. 외부 코드는 호출하지 않는다.
	 *
	 * @param chart	소속될 차트 (non-null)
	 * @throws IllegalArgumentException	{@code chart}가 {@code null}인 경우.
	 * @throws IllegalStateException	이미 차트가 등록된 경우.
	 */
	@Override
	public synchronized void setStateChart(@NotNull StateChart<C> chart) {
		Preconditions.checkNotNullArgument(chart, "chart is null");
		Preconditions.checkState(m_chart == null, "StateChart is already set on this context: %s", m_chart);

		m_chart = chart;
	}
}
