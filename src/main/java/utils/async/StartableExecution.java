package utils.async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface StartableExecution<T> extends Execution<T> {
	public void start();
}
