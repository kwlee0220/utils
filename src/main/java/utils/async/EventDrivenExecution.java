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
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class EventDrivenExecution<T> implements Execution<T>, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(EventDrivenExecution.class);
	
	private long m_timeoutMillis = TimeUnit.SECONDS.toMillis(3);	// 3 seconds
	protected final Guard m_aopGuard = Guard.create();
	@GuardedBy("m_aopGuard") private AsyncState m_aopState = AsyncState.NOT_STARTED;
	@GuardedBy("m_aopGuard") private AsyncResult<T> m_result;		// may null
	@GuardedBy("m_aopGuard") private final List<Runnable> m_startListeners
													= Lists.newCopyOnWriteArrayList();
	@GuardedBy("m_aopGuard") private final List<Consumer<AsyncResult<T>>> m_finishListeners
													= Lists.newCopyOnWriteArrayList();
	
	private Logger m_logger = s_logger;

	@Override
	public AsyncState getState() {
		return m_aopGuard.get(() -> m_aopState);
	}
	
	public void setTimeout(long timeout, TimeUnit unit) {
		m_timeoutMillis = unit.toMillis(timeout);
	}
	
	public void complete(T result) {
		notifyCompleted(result);
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		Date due = new Date(System.currentTimeMillis() + m_timeoutMillis);
		
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
						if ( !m_aopGuard.awaitUntil(due) ) {
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
		return waitForResult().get();
	}

	@Override
	public T get(Date due) throws InterruptedException, ExecutionException,
													TimeoutException, CancellationException {
		return waitForResult(due).get();
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
	public void waitForDone() throws InterruptedException {
		m_aopGuard.awaitUntil(this::isDoneInGuard);
	}

	@Override
	public boolean waitForDone(Date due) throws InterruptedException {
		return m_aopGuard.awaitUntil(this::isDoneInGuard, due);
	}

	@Override
	public FOption<AsyncResult<T>> pollResult() {
		return m_aopGuard.get(() -> isDoneInGuard() ? FOption.of(m_result) : FOption.empty());
	}

	@Override
	public AsyncResult<T> waitForResult() throws InterruptedException {
		return m_aopGuard.awaitUntilAndGet(this::isDoneInGuard, () -> m_result);
	}

	@Override
	public AsyncResult<T> waitForResult(Date due) throws InterruptedException,
																	TimeoutException {
		return m_aopGuard.awaitUntilAndGet(this::isDoneInGuard, () -> m_result, due);
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
					m_aopGuard.signalAll();
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
					m_aopGuard.signalAll();
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
					m_result = AsyncResult.completed(result);
					m_aopState = AsyncState.COMPLETED;
					m_aopGuard.signalAll();
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
					m_result = AsyncResult.failed(cause);
			    	m_aopState = AsyncState.FAILED;
			    	m_aopGuard.signalAll();
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
    		Date due = new Date(System.currentTimeMillis() + m_timeoutMillis);
			while ( m_aopState == AsyncState.STARTING ) {
				try {
					if ( !m_aopGuard.awaitUntil(due) ) {
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
					m_aopGuard.signalAll();
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
    		Date due = new Date(System.currentTimeMillis() + m_timeoutMillis);
    		try {
				while ( m_aopState == AsyncState.STARTING ) {
					if ( !m_aopGuard.awaitUntil(due) ) {
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
					m_result = AsyncResult.cancelled();
					m_aopState = AsyncState.CANCELLED;
					m_aopGuard.signalAll();
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

	@Override
	public Execution<T> whenStarted(Runnable listener) {
		Utilities.checkNotNullArgument(listener);
		
		m_aopGuard.lock();
    	try {
			switch ( m_aopState ) {
				case NOT_STARTED:
				case STARTING:
					m_startListeners.add(listener);
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

	@Override
	public Execution<T> whenFinished(Consumer<AsyncResult<T>> resultConsumer) {
		Utilities.checkNotNullArgument(resultConsumer);
		
		m_aopGuard.lock();
    	try {
    		// 이미 종료된 경우에는 바로 수행시킨다.
    		if ( isDoneInGuard() ) {
    			CompletableFuture.runAsync(() -> resultConsumer.accept(m_result));
    		}
    		else {
				m_finishListeners.add(resultConsumer);
    		}
			
			return this;
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
	}
	
	public void dependsOn(Execution<?> exec, T result) {
		exec.whenStarted(this::notifyStarted);
		exec.whenCompleted(r -> this.notifyCompleted(result));
		exec.whenFailed(this::notifyFailed);
		exec.whenCancelled(this::notifyCancelled);
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
	
	public <S> EventDrivenExecution<S> map(Function<AsyncResult<? extends T>,? extends S> mapper) {
		return new Executions.MapChainExecution<>(this, mapper);
	}
	
	public <S> EventDrivenExecution<S> mapOnCompleted(Function<? super T,? extends S> mapper) {
		return new Executions.MapCompleteChainExecution<>(this, mapper);
	}
	
	public <S> EventDrivenExecution<S> flatMap(
									Function<AsyncResult<? extends T>,Execution<? extends S>> mapper) {
		return new Executions.FlatMapChainExecution<>(this, mapper);
	}
	
	public <S> EventDrivenExecution<S> flatMapOnCompleted(
									Function<? super T,Execution<? extends S>> mapper) {
		return new Executions.FlatMapCompleteChainExecution<>(this, mapper);
	}
	
	private void notifyStartListeners() {
		m_aopGuard.run(() -> {
			if ( m_startListeners.size() > 0 ) {
				CompletableFuture.runAsync(() -> m_startListeners.forEach(Runnable::run));
			}
		});
	}
	
	private void notifyFinishListenersInGuard() {
		List<Consumer<AsyncResult<T>>> listeners = Lists.newArrayList(m_finishListeners);
		CompletableFuture.runAsync(() -> listeners.forEach(c -> c.accept(m_result)));
	}
	
	private static boolean isDone(AsyncState state) {
		switch ( state ) {
			case COMPLETED: case FAILED: case CANCELLED:
				return true;
			default:
				return false;
		}
	}
	
	private boolean isDoneInGuard() {
		return isDone(m_aopState);
	}
}
