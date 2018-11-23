package utils.async;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface ExecutionAwareWork<T> {
	public T execute(EventDrivenExecution<T> handle) throws Exception;
}
