package utils.async;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.function.Supplier;

import utils.Throwables;
import utils.func.CheckedSupplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Executions {
	private Executions() {
		throw new AssertionError("Should not be called: class=" + getClass());
	}
	
	public static AbstractThreadedExecution<Void> toExecution(Runnable task) {
		return new AbstractThreadedExecution<Void>() {
			@Override
			protected Void executeWork() throws InterruptedException, CancellationException, Exception {
				task.run();
				return null;
			}
		};
	}
	
	public static <T> AbstractThreadedExecution<T> toExecution(Supplier<T> task) {
		return new AbstractThreadedExecution<T>() {
			@Override
			protected T executeWork() throws InterruptedException, CancellationException, Exception {
				return task.get();
			}
		};
	}
	
	public static <T> AbstractThreadedExecution<T> toExecution(CheckedSupplier<T> task) {
		return new AbstractThreadedExecution<T>() {
			@Override
			protected T executeWork() throws InterruptedException, CancellationException, Exception {
				try {
					return task.get();
				}
				catch ( Throwable e ) {
					throw Throwables.toException(e);
				}
			}
		};
	}
	
	public static <T> AbstractThreadedExecution<T> toExecution(Callable<T> task) {
		return new AbstractThreadedExecution<T>() {
			@Override
			protected T executeWork() throws InterruptedException, CancellationException, Exception {
				return task.call();
			}
		};
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
	
/*
	private static class SupplyingExecution<T> implements StartableExecution<T> {
		private final Supplier<Execution<T>> m_fact;
		
		private final Guard m_guard = Guard.create();
		@GuardedBy("m_guard") private Execution<T> m_exec;
		@GuardedBy("m_guard") private boolean m_cancelled = false;
		@GuardedBy("m_guard") private List<Runnable> m_pendingStartListeners = new ArrayList<>();
		@GuardedBy("m_guard") private List<Consumer<Result<T>>> m_pendingFinishListeners = new ArrayList<>();
		
		private SupplyingExecution(Supplier<Execution<T>> fact) {
			m_fact = fact;
		}

		@Override
		public void start() {
			m_guard.lock();
			try {
				if ( m_exec != null ) {
					throw new IllegalStateException("already started: async=" + m_exec);
				}
				
				m_exec = m_fact.get();
			}
			finally {
				m_guard.unlock();
			}
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return _get(() -> m_cancelled = true,
						exec -> {
							boolean cancelled = exec.cancel(mayInterruptIfRunning);
							return m_guard.get(() -> m_cancelled = cancelled);
						});
		}

		@Override
		public State getState() {
			return _get(() -> (m_cancelled) ? State.CANCELLED : State.NOT_STARTED,
						Execution::getState);
		}

		@Override
		public T get() throws InterruptedException, ExecutionException, CancellationException {
			Execution<T> exec = m_guard.awaitUntilAndGet(() -> m_exec == null, () -> m_exec);
			return exec.get();
		}

		@Override
		public T get(Date due) throws InterruptedException, ExecutionException,
									TimeoutException, CancellationException {
			return m_guard.awaitUntilAndGet(() -> m_exec == null, () -> m_exec, due).get();
		}

		@Override
		public FOption<Result<T>> pollResult() {
			return _get(FOption::empty, Execution::pollResult);
		}

		@Override
		public Result<T> waitForResult() throws InterruptedException {
			return null;
		}

		@Override
		public Result<T> waitForResult(Date due) throws InterruptedException, TimeoutException {
			return null;
		}

		@Override
		public void waitForStarted() throws InterruptedException {
			m_guard.awaitUntilAndGet(() -> m_exec == null, () -> m_exec).waitForStarted();
		}

		@Override
		public boolean waitForStarted(Date due) throws InterruptedException {
			try {
				return m_guard.awaitUntilAndGet(() -> m_exec == null, () -> m_exec, due)
								.waitForStarted(due);
			}
			catch ( TimeoutException e ) {
				return false;
			}
		}

		@Override
		public void waitForDone() throws InterruptedException {
			waitForStarted();
			m_guard.get(() -> m_exec).waitForDone();
		}

		@Override
		public boolean waitForDone(Date due) throws InterruptedException {
			if ( !waitForStarted(due) ) {
				return false;
			}
			return m_guard.get(() -> m_exec).waitForDone(due);
		}

		@Override
		public Execution<T> whenStarted(Runnable listener) {
			return null;
		}

		@Override
		public Execution<T> whenFinished(Consumer<Result<T>> listener) {
			return null;
		}
		
		private <S> S _get(Supplier<S> notStartedSupplier,
							Function<Execution<T>,S> startedSupplier) {
			Execution<T> exec = null;
			m_guard.lock();
			try {
				if ( m_exec != null ) {
					exec = m_exec;
				}
				else {
					return notStartedSupplier.get();
				}
			}
			finally {
				m_guard.unlock();
			}
			
			return startedSupplier.apply(exec);
		}
	}
*/
}
