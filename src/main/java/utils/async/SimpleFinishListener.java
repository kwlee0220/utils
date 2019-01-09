package utils.async;

import utils.async.Execution.FinishListener;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SimpleFinishListener<T> implements FinishListener<T> {
	@Override public void onCompleted(T result) { }
	@Override public void onFailed(Throwable cause) { }
	@Override public void onCancelled() { }
}
