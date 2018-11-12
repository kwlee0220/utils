package utils.async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Executor<T> {
	public void submit(AbstractExecution<T> handle);
	
	public void shutdown();
}
