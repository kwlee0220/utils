package utils.async;

import rx.Observable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ProgressReporter<P> {
	public Observable<P> getObservable();
}
