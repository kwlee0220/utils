package utils.async;

import java.util.concurrent.CompletableFuture;

import rx.Observable;
import rx.Observer;
import rx.subjects.BehaviorSubject;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncOperation<T> extends CompletableFuture<T> {
	private final BehaviorSubject<Float> m_subject;
	
	public AsyncOperation() {
		m_subject = BehaviorSubject.create(0f);
	}
	
	@Override
	public synchronized boolean complete(T value) {
		if ( super.complete(value) ) {
			m_subject.onNext(1.0f);
			m_subject.onCompleted();
			return true;
		}
		else {
			return false;
		}
	}
	
	@Override
	public synchronized boolean completeExceptionally(Throwable cause) {
		if ( super.completeExceptionally(cause) ) {
			m_subject.onError(cause);
			return true;
		}
		else {
			return false;
		}
	}
	
	@Override
	public synchronized boolean cancel(boolean mayInterruptIfRunning) {
		if ( super.cancel(mayInterruptIfRunning) ) {
			m_subject.onCompleted();
			return true;
		}
		else {
			return false;
		}
	}
	
	public Observable<Float> progressNotifier() {
		return m_subject;
	}
	
	public Observer<Float> progressListener() {
		return m_subject;
	}
}
