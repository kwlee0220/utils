package utils.rx;

import javax.annotation.Nonnull;

import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;

import utils.async.Execution;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ExecutionProgressReport<T> implements ObservableOnSubscribe<ExecutionProgress<T>> {
	private final Execution<? extends T> m_exec;
	private final boolean m_cancelOnDispose;
	
	ExecutionProgressReport(Execution<? extends T> exec, boolean cancelOnDispose) {
		m_exec = exec;
		m_cancelOnDispose = cancelOnDispose;
	}

	@Override
	public void subscribe(@Nonnull ObservableEmitter<ExecutionProgress<T>> emitter) throws Exception {
		if ( m_cancelOnDispose ) {
			emitter.setCancellable(() -> m_exec.cancel(true));
		}
		
		m_exec.whenStartedAsync(() -> {
			if ( !emitter.isDisposed() ) {
				emitter.onNext(new ExecutionProgress.Started<>());
			}
		});
		m_exec.whenFinishedAsync(r -> {
			if ( !emitter.isDisposed() ) {
				if ( r.isSuccessful() ) {
					emitter.onNext(new ExecutionProgress.Completed<T>(r.getOrNull()));
					emitter.onComplete();
				}
				else if ( r.isFailed() ) {
					emitter.onNext(new ExecutionProgress.Failed<>(r.getCause()));
					emitter.onError(r.getCause());
				}
				else if ( r.isNone() ) {
					emitter.onNext(new ExecutionProgress.Cancelled<>());
					emitter.onComplete();
				}
			}
		});
	}
}
