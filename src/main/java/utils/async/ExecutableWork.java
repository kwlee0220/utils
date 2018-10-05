package utils.async;

import java.util.concurrent.CancellationException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ExecutableWork<T> {
	public T executeWork() throws CancellationException, Throwable;
}
