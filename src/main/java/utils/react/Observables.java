package utils.react;

import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Completable;
import io.reactivex.Observable;
import utils.async.Execution;
import utils.func.Unchecked;
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
	
	public static <T> Observable<T> from(FStream<T> stream) {
		return Observable.create(emitter -> {
			try {
				stream.takeWhile(v -> !emitter.isDisposed())
						.forEach(Unchecked.liftIE(emitter::onNext));
				if ( !emitter.isDisposed() ) {
					emitter.onComplete();
				}
			}
			catch ( Throwable e ) {
				emitter.onError(e);
			}
		});
	}
	
	public static Completable fromVoidExecution(Execution<Void> exec) {
		return Completable.create(emitter -> {
			exec.whenFinished(r -> {
				if ( !emitter.isDisposed() ) {
					if ( r.isCompleted() ) {
						emitter.onComplete();
					}
					else if ( r.isFailed() ) {
						emitter.onError(r.getCause());
					}
					else if ( r.isCancelled() ) {
						emitter.onError(new CancellationException());
					}
				}
			});
		});
	}
}
