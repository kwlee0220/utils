package utils.async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ExecutableHandle<T> extends ExecutionHandle<T>
											implements ExecutableWork<T> {
	@Override
	public ExecutableWork<T> getExecutableWork() {
		return this;
	}
}