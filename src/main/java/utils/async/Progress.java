package utils.async;

import java.util.concurrent.CompletableFuture;

import io.reactivex.Observable;
import io.vavr.Lazy;
import utils.Throwables;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Progress<T,P> extends CompletableFuture<T> implements ProgressReporter<P> {
	private final CompletableFuture<T> m_promise;
	private final Observable<P> m_observable;
	
	public static <T,P> Progress<T,P> run(Lazy<CompletableFuture<T>> work,
												ProgressReporter<P> task) {
		return new Progress<>(work.get(), task);
	}
	
	public static <P> Progress<Void,P> runProgressAsync(ProgressReporter<P> task) {
		return new Progress<>(CompletableFuture.runAsync((Runnable)task), task);
	}
	
	public Progress(CompletableFuture<T> promise, Observable<P> observable) {
		m_promise = promise;
		m_observable = observable;
		m_promise.whenComplete((ret,error) -> {
			if ( error != null ) {
				super.completeExceptionally(Throwables.unwrapThrowable(error));
			}
			else if ( m_promise.isCancelled() ) {
				super.cancel(true);
			}
			else {
				super.complete(ret);
			}
		});
	}
	
	public Progress(CompletableFuture<T> promise, ProgressReporter<P> reporter) {
		this(promise, reporter.getObservable());
	}
	
	@Override
	public Observable<P> getObservable() {
		return m_observable;
	}
	
	@Override
	public boolean complete(T result) {
		if ( super.complete(result) ) {
			m_promise.complete(result);
			return true;
		}
		else {
			return false;
		}
	}
	
	@Override
	public boolean completeExceptionally(Throwable error) {
		if ( super.completeExceptionally(error) ) {
			m_promise.completeExceptionally(error);
			return true;
		}
		else {
			return false;
		}
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if ( super.cancel(mayInterruptIfRunning) ) {
			m_promise.cancel(mayInterruptIfRunning);
			return true;
		}
		else {
			m_promise.cancel(mayInterruptIfRunning);
			return false;
		}
	}
}
