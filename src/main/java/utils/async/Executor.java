package utils.async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Executor<T> {
	public void submit(ExecutableExecution<T> handle);
	
	public void shutdown();
}
