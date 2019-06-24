package utils.async;

import java.util.function.Function;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Executions {
	private Executions() {
		throw new AssertionError("Should not be called: class=" + getClass());
	}
	
	static class FlatMapCompleteChainExecution<T,S> extends EventDrivenExecution<S> {
		FlatMapCompleteChainExecution(EventDrivenExecution<? extends T> leader,
									Function<? super T,Execution<? extends S>> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret ->
				ret.ifCompleted(v -> {
						Execution<S> follower = Execution.narrow(chain.apply(v));
						follower.whenStarted(this::notifyStarted)
								.whenFinished(ret2 -> ret2.ifCompleted(this::notifyCompleted)
														.ifFailed(this::notifyFailed)
														.ifCancelled(this::notifyCancelled));
						if ( !follower.isStarted() && follower instanceof StartableExecution ) {
							((StartableExecution<S>)follower).start();
						}
					})
					.ifFailed(this::notifyFailed)
					.ifCancelled(this::notifyCancelled)
			);
		}
	}
	
	static class FlatMapChainExecution<T,S> extends EventDrivenExecution<S> {
		FlatMapChainExecution(EventDrivenExecution<? extends T> leader,
							Function<Result<? extends T>,Execution<? extends S>> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret -> {
				Execution<S> follower = Execution.narrow(chain.apply(ret));
				follower.whenStarted(this::notifyStarted)
						.whenFinished(ret2 -> ret2.ifCompleted(this::notifyCompleted)
													.ifFailed(this::notifyFailed)
													.ifCancelled(this::notifyCancelled));
				if ( !follower.isStarted() && follower instanceof StartableExecution ) {
					((StartableExecution<S>)follower).start();
				}
			});
		}
	}
	
	static class MapChainExecution<T,S> extends EventDrivenExecution<S> {
		MapChainExecution(EventDrivenExecution<? extends T> leader,
							Function<Result<? extends T>,? extends S> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret -> {
				try {
					notifyCompleted(chain.apply(ret));
				}
				catch ( Throwable e ) {
					notifyFailed(e);
				}
			});
		}
	}
	
	static class MapCompleteChainExecution<T,S> extends EventDrivenExecution<S> {
		MapCompleteChainExecution(EventDrivenExecution<? extends T> leader,
									Function<? super T,? extends S> chain) {
			leader.whenStarted(this::notifyStarted);
			leader.whenFinished(ret ->
				ret.ifCompleted(v -> {
						try {
							notifyCompleted(chain.apply(v));
						}
						catch ( Throwable e ) {
							notifyFailed(e);
						}
					})
					.ifFailed(this::notifyFailed)
					.ifCancelled(this::notifyCancelled)
		);
		}
	}
}
