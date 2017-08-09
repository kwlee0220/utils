package utils.async;

import java.util.concurrent.CompletableFuture;

import rx.Observable;
import rx.Observer;
import rx.subjects.BehaviorSubject;
import utils.io.IOUtils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Progress<P,T> extends CompletableFuture<T> {
	private final BehaviorSubject<P> m_subject;
	
	public Progress(P init) {
		m_subject = BehaviorSubject.create(init);
	}
	
	public void addCloseables(AutoCloseable... closeables) {
		m_subject.subscribe(v->{},
							e->closeCloseables(closeables),
							()->closeCloseables(closeables));
	}
	
	@Override
	public synchronized boolean complete(T value) {
		if ( super.complete(value) ) {
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
	
	public Observable<P> progressNotifier() {
		return m_subject;
	}
	
	public Observer<P> progressListener() {
		return m_subject;
	}
	
	private void closeCloseables(AutoCloseable... closeables) {
		IOUtils.closeQuietly(closeables);
	}
}
