package utils.async;

import rx.Observer;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ProgressReporter<P> {
	public P getInitialValue();
	public void setProgressListener(Observer<P> listener);
}
