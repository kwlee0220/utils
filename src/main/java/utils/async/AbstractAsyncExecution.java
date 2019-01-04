package utils.async;

import java.util.concurrent.Executor;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractAsyncExecution<T> extends EventDrivenExecution<T>
												implements AsyncExecution<T> {
	private Executor m_exector;

	@Override
	public Executor getExecutor() {
		return m_exector;
	}

	@Override
	public void setExecutor(Executor executor) {
		m_exector = executor;
	}
}
