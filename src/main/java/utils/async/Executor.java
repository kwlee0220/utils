package utils.async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Executor<T> {
	public Execution<T> submit(ExecutableWork<T> job);
	public void submit(ExecutableHandle<T> handle);
	
	public void shutdown();
}
