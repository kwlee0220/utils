package utils.react;

import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import utils.async.Execution;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Observables {
	static final Logger s_logger = LoggerFactory.getLogger(Observables.class);
	
	private Observables() {
		throw new AssertionError("Should not be called: class=" + Observables.class);
	}
	
	public static <T> Observable<T> fromFStream(FStream<T> stream) {
		return Observable.create(emitter -> {
			try {
				stream.forEachAE(emitter::onNext);
				emitter.onComplete();
			}
			catch ( Throwable e ) {
				emitter.onError(e);
			}
		});
	}
	
	public static <T> Observable<T> fromStream(Stream<T> stream) {
		return Observable.create(emitter -> {
			try {
				stream.forEach(emitter::onNext);
				emitter.onComplete();
			}
			catch ( Throwable e ) {
				emitter.onError(e);
			}
		});
	}
	
	public static <T> Observable<T> fromExecution(Execution<T> exec) {
		return Observable.create(emitter -> {
			exec.whenDone(r -> {
				if ( r.isCompleted() ) {
					emitter.onNext(r.getOrNull());
					emitter.onComplete();
				}
				else if ( r.isFailed() ) {
					emitter.onError(r.getCause());
				}
				else if ( r.isCancelled() ) {
					emitter.onError(new CancellationException());
				}
			});
		});
	}
	
	public static <T> Single<T> fromNonCancellable(Execution<T> exec) {
		return Single.create(emitter -> {
			exec.whenDone(r -> {
				if ( r.isCompleted() ) {
					emitter.onSuccess(r.getOrNull());
				}
				else if ( r.isFailed() ) {
					emitter.onError(r.getCause());
				}
				else if ( r.isCancelled() ) {
					emitter.onError(new CancellationException());
				}
			});
		});
	}
	
	public static Completable fromVoidExecution(Execution<Void> exec) {
		return Completable.create(emitter -> {
			exec.whenDone(r -> {
				if ( r.isCompleted() ) {
					emitter.onComplete();
				}
				else if ( r.isFailed() ) {
					emitter.onError(r.getCause());
				}
				else if ( r.isCancelled() ) {
					emitter.onError(new CancellationException());
				}
			});
		});
	}
}
