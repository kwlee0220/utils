package utils.exception;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedRunnable {
	public void run() throws Throwable;
}
