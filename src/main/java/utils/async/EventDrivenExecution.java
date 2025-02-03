package utils.async;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import utils.LoggerSettable;
import utils.Utilities;
import utils.func.Result;
import utils.func.Tuple;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class EventDrivenExecution<T> implements Execution<T>, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(EventDrivenExecution.class);
	
	private long m_cancelTimeoutMillis = TimeUnit.SECONDS.toMillis(3);	// 3 seconds
	protected final Guard m_aopGuard = Guard.create();
	@GuardedBy("m_aopGuard") private AsyncState m_aopState = AsyncState.NOT_STARTED;
	@GuardedBy("m_aopGuard") private AsyncResult<T> m_asyncResult;		// may null
	@GuardedBy("m_aopGuard") private final List<Tuple<Runnable,Boolean>> m_startListeners
													= Lists.newCopyOnWriteArrayList();
	@GuardedBy("m_aopGuard") private final List<Tuple<Consumer<Result<T>>,Boolean>> m_finishListeners
													= Lists.newCopyOnWriteArrayList();
	
	private Logger m_logger = s_logger;

	@Override
	public AsyncState getState() {
		return m_aopGuard.get(() -> m_aopState);
	}
	
    public void setTimeout(long timeout, TimeUnit unit) {
    	Executions.getTimer().setTimer(this, timeout, unit);
    }
	
	public void setCancelTimeout(long timeout, TimeUnit unit) {
		m_cancelTimeoutMillis = unit.toMillis(timeout);
	}
	
	public void complete(T result) {
		notifyCompleted(result);
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		Date due = new Date(System.currentTimeMillis() + m_cancelTimeoutMillis);
		
		m_aopGuard.lock();
		try {
			boolean inTransitionState = true;
			while ( inTransitionState ) {
				switch ( m_aopState ) {
					case NOT_STARTED:
						// 시작되지도 않은 상태이면 바로 cancel된 것으로 세팅하고 바로 반환한다.
						notifyCancelled();
						return true;
					case STARTING:
					case CANCELLING:
						// 'STARTING'/'CANCELLING' 상태인 경우에는 상태가 바뀔때까지
						// 제한시간동안 대기한다.
						if ( !m_aopGuard.awaitInGuardUntil(due) ) {
							return false;
						}
						break;
					case CANCELLED:
						return true;
					case COMPLETED:
					case FAILED:
						return false;
					default:
						inTransitionState = false;
						break;
				}
			}
			
			// 여기서 상태는 'RUNNING' 밖에 없음
			
			// mayInterruptIfRunning이 false인 경우거나 'CancellableWork'를 implement하지
			// Execution은 시작되지 않는 execution만 취소시킬 수 있기
			// 때문에 이미 RUNNING상태인 경우에는 false를 반환한다.
			if ( !mayInterruptIfRunning || !(this instanceof CancellableWork) ) {
				return false;
			}
			
			// 일단 상태를 'CANCELLING' 상태로 전이시켜 놓고 원래 작업을 중단시킬 준비를 함
			if ( !notifyCancelling() ) {
				return false;
			}
		}
		catch ( InterruptedException e ) {
			throw new ThreadInterruptedException();
		}
		finally {
			m_aopGuard.unlock();
		}
			
		//
		// 여기서부터는 수행 중인 execution을 명시적으로 cancel 시켜야 함.
		//
		try {
			((CancellableWork)this).cancelWork();
		}
		catch ( Exception e ) {
			getLogger().warn("fails to cancel work: {}, cause={}", this, e.toString());
			notifyFailed(e);
			
			return false;
		}
		
		return notifyCancelled();
	}

	@Override
	public T get() throws InterruptedException, ExecutionException, CancellationException {
		try {
			AsyncResult<T> result = waitForFinished();
			
			return result.get();
		}
		catch ( TimeoutException neverHappens ) {
			throw new AssertionError("Should not be here: " + getClass().getName() + "#get()");
		}
	}

	@Override
	public T get(Date due) throws InterruptedException, ExecutionException,
													TimeoutException, CancellationException {
		return waitForFinished(due).get();
	}
	
	@Override
	public void waitForStarted() throws InterruptedException {
		m_aopGuard.awaitUntil(() -> m_aopState.ordinal() >= AsyncState.RUNNING.ordinal());
	}
	
	@Override
	public boolean waitForStarted(Date due) throws InterruptedException {
		return m_aopGuard.awaitUntil(() -> m_aopState.ordinal() >= AsyncState.RUNNING.ordinal(), due);
	}

	@Override
	public AsyncResult<T> poll() {
		return m_aopGuard.get(() -> isDoneInGuard() ? m_asyncResult : AsyncResult.running());
	}

	@Override
	public AsyncResult<T> waitForFinished(Date due) throws InterruptedException {
		try {
			return m_aopGuard.awaitUntilAndGet(this::isDoneInGuard, () -> m_asyncResult, due);
		}
		catch ( TimeoutException e ) {
			return AsyncResult.running();
		}
	}

	@Override
	public AsyncResult<T> waitForFinished() throws InterruptedException {
		return m_aopGuard.awaitUntilAndGet(this::isDoneInGuard, () -> m_asyncResult);
	}

	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	public boolean notifyStarting() {
		m_aopGuard.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_aopState = AsyncState.STARTING;
					m_aopGuard.signalAllInGuard();
				case STARTING:
			    	return true;
				case RUNNING:
				case COMPLETED:
				case CANCELLED:
				case CANCELLING:
				case FAILED:
					return false;
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.STARTING);
					throw new IllegalStateException(msg);
			}
			
		}
		finally {
			m_aopGuard.unlock();
		}
	}
	
	public boolean notifyStarted() {
		m_aopGuard.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
				case STARTING:
					m_aopState = AsyncState.RUNNING;
					m_aopGuard.signalAllInGuard();
					getLogger().debug("started: {}", this);
					
					notifyStartListeners();
				case RUNNING:
			    	return true;
				case COMPLETED:
				case CANCELLED:
				case CANCELLING:
				case FAILED:
					return false;
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.RUNNING);
					throw new IllegalStateException(msg);
			}
		}
		finally {
			m_aopGuard.unlock();
		}
	}

	public boolean notifyCompleted(T result) {
		m_aopGuard.lock();
		try {
			switch ( m_aopState ) {
				case RUNNING:
				case CANCELLING:
					m_asyncResult = AsyncResult.completed(result);
					m_aopState = AsyncState.COMPLETED;
					m_aopGuard.signalAllInGuard();
					getLogger().debug("completed: {}, result={}", this, result);
					
					notifyFinishListenersInGuard();
			    	return true;
				case CANCELLED:
				case FAILED:
				case COMPLETED:
					return false;
				case NOT_STARTED:
				case STARTING:
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.COMPLETED);
					throw new IllegalStateException(msg);
			}
			
		}
		finally {
			m_aopGuard.unlock();
		}
	}
	
	public boolean notifyFailed(Throwable cause) {
		m_aopGuard.lock();
    	try {
			switch ( m_aopState ) {
				case STARTING:		// start 과정에서 오류가 발생된 경우.
				case RUNNING:
				case CANCELLING:
					m_asyncResult = AsyncResult.failed(cause);
			    	m_aopState = AsyncState.FAILED;
			    	m_aopGuard.signalAllInGuard();
					getLogger().info("failed: {}, cause={}", this, cause.toString());
					
					notifyFinishListenersInGuard();
			    	return true;
				case FAILED:
				case COMPLETED:
				case CANCELLED:
					return false;
				case NOT_STARTED:
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.FAILED);
					throw new IllegalStateException(msg);
			}
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
    }
	
	public boolean notifyCancelling() {
		m_aopGuard.lock();
    	try {
    		// 시작 중인 상태이면, cancel 작업이 inconsistent한 상태를 볼 수 있기 때문에,
    		// 일단 start작업이 완료될 때까지 대기한다.
    		Date due = new Date(System.currentTimeMillis() + m_cancelTimeoutMillis);
			while ( m_aopState == AsyncState.STARTING ) {
				try {
					if ( !m_aopGuard.awaitInGuardUntil(due) ) {
						return false;
					}
				}
				catch ( InterruptedException e ) {
					return false;
				}
			}
    		
			switch ( m_aopState ) {
				case RUNNING:
				case NOT_STARTED:
					m_aopState = AsyncState.CANCELLING;
					m_aopGuard.signalAllInGuard();
				case CANCELLING:
				case CANCELLED:
					return true;
				case COMPLETED:
				case FAILED:
					return false;
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.CANCELLING);
					throw new IllegalStateException(msg);
			}
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
    }
	
	public boolean notifyCancelled() {
		m_aopGuard.lock();
    	try {
    		// 시작 중인 상태이면, cancel 작업이 inconsistent한 상태를 볼 수 있기 때문에,
    		// 일단 start작업이 완료될 때까지 대기한다.
    		Date due = new Date(System.currentTimeMillis() + m_cancelTimeoutMillis);
    		try {
				while ( m_aopState == AsyncState.STARTING ) {
					if ( !m_aopGuard.awaitInGuardUntil(due) ) {
						return false;
					}
				}
			}
			catch ( InterruptedException e ) {
				return false;
			}
    		
			switch ( m_aopState ) {
				case CANCELLING:
				case RUNNING:
				case NOT_STARTED:
					m_asyncResult = AsyncResult.cancelled();
					m_aopState = AsyncState.CANCELLED;
					m_aopGuard.signalAllInGuard();
					getLogger().info("cancelled: {}", this);
					
					notifyFinishListenersInGuard();
				case CANCELLED:
			    	return true;
				case COMPLETED:
				case FAILED:
					return false;
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.CANCELLED);
					throw new IllegalStateException(msg);
			}
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
    }
	
	public Result<T> getResult() {
		switch ( m_asyncResult.getState() ) {
			case COMPLETED:
				return Result.success(m_asyncResult.getUnchecked());
			case FAILED:
				return Result.failure(m_asyncResult.getFailureCause());
			case CANCELLED:
				return Result.none();
			default:
				throw new IllegalStateException("invalid execution state: " + m_asyncResult.getState());
		}
	}

	@Override
	public Execution<T> whenStarted(Runnable listener) {
		return _whenStarted(listener, false);
	}
	
	@Override
	public Execution<T> whenStartedAsync(Runnable listener) {
		return _whenStarted(listener, true);
	}

	@Override
	public Execution<T> whenFinished(Consumer<Result<T>> handler) {
		return _whenFinished(handler, false);
	}
	
	@Override
	public Execution<T> whenFinishedAsync(Consumer<Result<T>> handler) {
		return _whenFinished(handler, true);
	}

	private Execution<T> _whenStarted(Runnable listener, boolean runAsync) {
		Utilities.checkNotNullArgument(listener);
		
		m_aopGuard.lock();
    	try {
			switch ( m_aopState ) {
				case NOT_STARTED:
				case STARTING:
					m_startListeners.add(Tuple.of(listener, runAsync));
					break;
				default:
					// 이미 시작된 경우에는 바로 listener를 구동시킨다.
					CompletableFuture.runAsync(listener::run);
					break;
			}
			
			return this;
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
	}

	private Execution<T> _whenFinished(Consumer<Result<T>> handler, boolean runAsync) {
		Utilities.checkNotNullArgument(handler);
		
		m_aopGuard.lock();
    	try {
    		// 이미 종료된 경우에는 바로 수행시킨다.
    		if ( isDoneInGuard() ) {
    			notifyFinishListenersInGuard();
    		}
    		else {
				m_finishListeners.add(Tuple.of(handler, runAsync));
    		}
			
			return this;
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
	}
	
	public void dependsOn(Execution<?> exec, T result) {
		exec.whenStartedAsync(this::notifyStarted);
		exec.whenFinishedAsync(ret -> {
			ret.ifSuccessful(r -> this.notifyCompleted(result))
				.ifFailed(this::notifyFailed)
				.ifNone(this::notifyCancelled);
		});
	}
	
	public final void checkCancelled() {
		AsyncState state = getState();
		if ( state == AsyncState.CANCELLING || state == AsyncState.CANCELLED ) {
			throw new CancellationException();
		}
	}
	
	public final boolean isCancelRequested() {
		return getState() == AsyncState.CANCELLING;
	}
	
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), m_aopState);
	}
	
	public void runInAsyncExecutionGuard(Runnable work) {
		m_aopGuard.run(work);
	}
	
	public <R> R getInAsyncExecutionGuard(Supplier<R> supplier) {
		return m_aopGuard.get(supplier);
	}
	
	public <S> EventDrivenExecution<S> flatMapOnCompleted(
									Function<? super T,Execution<? extends S>> mapper) {
		return new Executions.FlatMapCompleteChainExecution<>(this, mapper);
	}
	
	private void notifyStartListeners() {
		m_aopGuard.run(() -> {
			FStream.from(m_startListeners)
					.filterNot(t -> t._2)
					.forEach(t -> t._1.run());
			
			List<Runnable> asyncListeners = FStream.from(m_startListeners)
													.filter(t -> t._2)
													.map(t -> t._1)
													.toList();
			if ( asyncListeners.size() > 0 ) {
				CompletableFuture.runAsync(() -> asyncListeners.forEach(Runnable::run));
			}
		});
	}
	
	private void notifyFinishListenersInGuard() {
		final Result<T> result = getResult();
		
		FStream.from(m_finishListeners)
					.filterNot(t -> t._2)
					.forEach(t -> t._1.accept(result));
		
		List<Consumer<Result<T>>> asyncHandler = FStream.from(m_finishListeners)
														.filter(t -> t._2)
														.map(t -> t._1)
														.toList();
		if ( asyncHandler.size() > 0 ) {
			CompletableFuture.runAsync(() -> asyncHandler.forEach(c -> c.accept(result)));
		}
	}
	
	private static boolean isFinished(AsyncState state) {
		switch ( state ) {
			case COMPLETED: case FAILED: case CANCELLED:
				return true;
			default:
				return false;
		}
	}
	
	private boolean isDoneInGuard() {
		return isFinished(m_aopState);
	}
}
