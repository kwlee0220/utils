package utils.statechart;

import java.util.List;

import com.google.common.base.Preconditions;

import utils.CSV;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractState<C extends StateContext> implements State<C> {
	private final String m_path;
	private final List<String> m_pathSegments;
	private final C m_context;
	
	protected AbstractState(String path, C context) {
		Preconditions.checkNotNull(path, "path must not be null");

		m_path = path;
		m_pathSegments = CSV.parseCsv(path, '.').toList();
		m_context = context;
	}
	
	@Override
	public String getPath() {
		return m_path;
	}
	
	public List<String> getPathSegments() {
		return m_pathSegments;
	}

	@Override
	public C getContext() {
		return m_context;
	}
	
	@Override
	public void enter() { }

	@Override
	public void exit() { }
	
	@Override
	public String toString() {
		return String.format("State[%s]", m_path);
	}
}
