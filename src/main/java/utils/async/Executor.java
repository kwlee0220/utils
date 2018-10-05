package utils.async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Executor<T> {
	public Execution<T> submit(ExecutableWork<T> job);
	public void submit(ExecutableWork<T> job, ExecutionHandle<T> handle);
	
	public default void submit(ExecutableHandle<T> handle) {
		submit(handle, handle);
	}
	
	public void shutdown();
}
