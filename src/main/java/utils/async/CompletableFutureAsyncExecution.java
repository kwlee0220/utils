package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CompletableFutureAsyncExecution<T> extends EventDrivenExecution<T>
												implements CancellableWork {
	private volatile CompletableFuture<? extends T> m_future;
	
	public static <T> CompletableFutureAsyncExecution<T> from(Supplier<T> suppl) {
		CompletableFuture<T> cfuture = CompletableFuture.supplyAsync(suppl);
		return new CompletableFutureAsyncExecution<>(cfuture);
	}
	
	private CompletableFutureAsyncExecution(CompletableFuture<? extends T> future) {
		m_future = future.whenComplete((r, ex) -> {
				if ( ex != null ) {
					if ( ex instanceof CancellationException ) {
						this.notifyCancelled();
					}
					else {
						this.notifyFailed(ex);
					}
				}
				else {
					this.notifyCompleted(r);
				}
			});
		super.notifyStarted();
	}

	@Override
	public boolean cancelWork() {
		if ( m_future != null ) {
			return m_future.cancel(true);
		}
		else {
			return true;
		}
	}
}