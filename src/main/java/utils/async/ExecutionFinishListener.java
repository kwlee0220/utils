package utils.async;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ExecutionFinishListener<T> {
	public void onCompleted(T result);
	public void onFailed(Throwable cause);
	public void onCancelled();
}
