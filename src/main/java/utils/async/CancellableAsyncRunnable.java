package utils.async;

import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CancellableAsyncRunnable<X extends Cancellable<Void> & Runnable>
														extends AbstractAsyncExecution<Void> {
	private final X m_task;
	private CompletableFuture<Void> m_future;
	
	public CancellableAsyncRunnable(X task) {
		m_task = task;
	}
	
	public X getTask() {
		return m_task;
	}
	
	@Override
	public boolean start() {
		return super.start(() -> {
			m_future = CompletableFuture.runAsync(m_task);
			m_future.whenComplete((v,e) -> {
				if ( m_task.isCompleted() ) {
					onCompleted(null);
				}
				else if ( m_task.isFailed() ) {
					onFailed(m_task.getResult().getCause());
				}
				else if ( m_task.isCancelled() ) {
					onCancelled();
				}
			});
		});
	}

	@Override
	public boolean cancel() {
		return cancel(() -> {
			m_task.cancel();
			m_future.cancel(true);
		});
	}
}
