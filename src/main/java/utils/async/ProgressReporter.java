package utils.async;

import rx.Observer;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ProgressReporter {
	public void setProgressListener(Observer<Float> lister);
}
