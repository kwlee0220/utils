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
	public void start() {
		super.start(() -> {
			m_future = CompletableFuture.runAsync(m_task);
			m_future.whenComplete((v,e) -> {
				if ( m_task.isCompleted() ) {
					notifyCompleted(null);
				}
				else if ( m_task.isFailed() ) {
					notifyFailed(m_task.getResult().getCause());
				}
				else if ( m_task.isCancelled() ) {
					// cancel()할 때 이미 notifyCancelled()를 호출하지만
					// cancel() 호출 없이 m_task가 cancel될 수 있기 때문에
					// 'notifyCancelled()'를 호출한다.
					notifyCancelled();
				}
			});
		});
	}

	@Override
	public void cancel() {
		cancel(() -> {
			m_task.cancel();
			m_future.cancel(true);
		});
	}
}
