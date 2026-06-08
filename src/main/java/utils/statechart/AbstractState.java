package utils.statechart;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import utils.CSV;
import utils.Preconditions;

/**
 * {@link State} 구현의 공통 기반 클래스.
 * <p>
 * 상태의 위치 경로({@code path})와 상태문맥({@code context})을 보관하며, 경로를 {@code '/'} 기준으로
 * 분리한 세그먼트 목록도 함께 제공한다. {@link #enter()} / {@link #exit()} / {@link #selectTransition(Signal)}는
 * 상속받은 구현체가 필요에 따라 override한다.
 *
 * @param <C>	상태문맥의 타입
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractState<C extends StateContext<C>> implements State<C> {
	private @NotNull final String m_path;
	private final List<String> m_pathSegments;
	private @NotNull final C m_context;

	/**
	 * 주어진 위치 경로와 상태문맥으로 상태를 생성한다.
	 * <p>
	 * 위치 경로는 {@code '/'}로 구분된 계층 경로(예: {@code "/Running/ReceivingVideo"})로 해석되며,
	 * 선두 {@code '/'}로 인해 생기는 빈 세그먼트는 제거된다.
	 *
	 * @param path		상태의 위치 경로. {@code null}이 아니어야 한다.
	 * @param context	상태가 속한 상태문맥. {@code null}이 아니어야 한다.
	 * @throws IllegalArgumentException	{@code path} 또는 {@code context}가 {@code null}인 경우.
	 */
	protected AbstractState(String path, C context) {
		Preconditions.checkNotNullArgument(path, "path");
		Preconditions.checkNotNullArgument(context, "context");

		m_path = path;
		m_pathSegments = CSV.parseCsv(path, '/').filter(seg -> !seg.isEmpty()).toList();
		m_context = context;
	}

	@Override
	public @NotNull String getPath() {
		return m_path;
	}

	/**
	 * 위치 경로를 {@code '/'} 기준으로 분리한 세그먼트 목록을 반환한다.
	 * <p>
	 * 선두 {@code '/'}로 인한 빈 세그먼트는 포함되지 않는다.
	 * 예를 들어 경로가 {@code "/Running/ReceivingVideo"}이면 {@code ["Running", "ReceivingVideo"]}를 반환한다.
	 *
	 * @return	경로 세그먼트 목록
	 */
	public @NotNull List<String> getPathSegments() {
		return m_pathSegments;
	}

	@Override
	public @NotNull C getContext() {
		return m_context;
	}

	@Override
	public String toString() {
		return String.format("State[%s]", m_path);
	}
}
