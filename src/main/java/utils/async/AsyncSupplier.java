package utils.async;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncSupplier<T> extends AbstractAsyncExecution<T> {
	private final Supplier<T> m_task;
	private CompletableFuture<T> m_future;
	
	public AsyncSupplier(Supplier<T> task) {
		m_task = task;
	}

	@Override
	public void start() throws IllegalStateException {
		m_future = CompletableFuture.supplyAsync(m_task);
		m_future.whenComplete((v,e) -> {
			if ( e != null ) {
				super.notifyFailed(e);
			}
			else {
				super.notifyCompleted(v);
			}
		});
	}

	@Override
	public void cancel() throws IllegalStateException {
		m_future.cancel(true);
	}
}
