package utils.async;

import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import utils.async.Execution.FinishListener;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ExecutionProgressReport<T> implements ObservableOnSubscribe<ExecutionProgress<T>> {
	private final Execution<T> m_exec;
	private final boolean m_cancelOnDispose;
	
	ExecutionProgressReport(Execution<T> exec, boolean cancelOnDispose) {
		m_exec = exec;
		m_cancelOnDispose = cancelOnDispose;
	}

	@Override
	public void subscribe(ObservableEmitter<ExecutionProgress<T>> emitter) throws Exception {
		if ( m_cancelOnDispose ) {
			emitter.setCancellable(() -> m_exec.cancel(true));
		}
		
		m_exec.whenStarted(() -> {
			if ( !emitter.isDisposed() ) {
				emitter.onNext(new ExecutionProgress.Started<>());
			}
		});
		m_exec.whenFinished(r -> {
			if ( !emitter.isDisposed() ) {
				if ( r.isCompleted() ) {
					emitter.onNext(new ExecutionProgress.Completed<T>(r.getOrNull()));
					emitter.onComplete();
				}
				else if ( r.isFailed() ) {
					emitter.onNext(new ExecutionProgress.Failed<>(r.getCause()));
					emitter.onError(r.getCause());
				}
				else if ( r.isCancelled() ) {
					emitter.onNext(new ExecutionProgress.Cancelled<>());
					emitter.onComplete();
				}
			}
		});
	}
}
