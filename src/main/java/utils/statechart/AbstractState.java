package utils.statechart;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import utils.CSV;
import utils.Utilities;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractState<C extends StateContext<C>> implements State<C> {
	private @NotNull final String m_path;
	private final List<String> m_pathSegments;
	private @NotNull final C m_context;

	protected AbstractState(String path, C context) {
		Utilities.checkNotNullArgument(path, "path");
		Utilities.checkNotNullArgument(context, "context");

		m_path = path;
		m_pathSegments = CSV.parseCsv(path, '.').toList();
		m_context = context;
	}

	@Override
	public @NotNull String getPath() {
		return m_path;
	}

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
