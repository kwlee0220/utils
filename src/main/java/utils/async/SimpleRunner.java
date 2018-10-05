package utils.async;

import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class SimpleRunner<T> extends ExecutionHandle<T> {
	private final ExecutableWork<T> m_work;
	
	SimpleRunner(ExecutableWork<T> work) {
		Objects.requireNonNull(work, "ExecutableWork is null");
		
		m_work = work;
	}

	@Override
	public ExecutableWork<T> getExecutableWork() {
		return m_work;
	}
}