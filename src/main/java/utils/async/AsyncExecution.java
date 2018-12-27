package utils.async;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncExecution<T> extends Execution<T> {
	public void start();
}
