package utils.async;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import io.vavr.CheckedRunnable;
import io.vavr.control.Option;
import io.vavr.control.Try;
import net.jcip.annotations.GuardedBy;
import utils.Guard;
import utils.LoggerSettable;
import utils.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractExecution<T> implements Execution<T>, LoggerSettable, Runnable {
	protected final ReentrantLock m_aopLock = new ReentrantLock();
	protected final Condition m_aopCond = m_aopLock.newCondition();
	protected final Guard m_aopGuard = Guard.by(m_aopLock, m_aopCond);
	@GuardedBy("m_aopLock") protected State m_aopState = State.NOT_STARTED;
	@GuardedBy("m_aopLock") private Result<T> m_result;		// may null
	@GuardedBy("m_aopLock") private final List<Runnable> m_startListeners = Lists.newCopyOnWriteArrayList();
	@GuardedBy("m_aopLock") private final List<Runnable> m_finishListeners = Lists.newCopyOnWriteArrayList();
	@GuardedBy("m_aopLock") private volatile Option<CheckedRunnable> m_startHook = Option.none();
	@GuardedBy("m_aopLock") private volatile Option<Runnable> m_completeHook = Option.none();
	@GuardedBy("m_aopLock") private volatile Option<Runnable> m_cancelHook = Option.none();
	@GuardedBy("m_aopLock") private volatile Option<Consumer<Throwable>> m_failureHook = Option.none();

	@GuardedBy("m_aopLock") protected Thread m_thread = null;
	private Logger m_logger = LoggerFactory.getLogger(AbstractExecution.class);
	
	public abstract T executeWork() throws CancellationException, Throwable;
	
	@Override
	public void waitForStarted() throws InterruptedException {
		m_aopGuard.awaitWhile(() -> m_aopState == State.NOT_STARTED);
	}
	
	@Override
	public boolean waitForStarted(long timeout, TimeUnit unit) throws InterruptedException {
		return m_aopGuard.awaitWhile(() -> m_aopState == State.NOT_STARTED, timeout, unit);
	}

	@Override
	public void waitForDone() throws InterruptedException {
		m_aopGuard.awaitWhile(() -> {
			switch ( m_aopState ) {
				case NOT_STARTED:
				case RUNNING:
				case CANCELLING:
					return true;
				default:
					return false;
			}
		});
	}

	@Override
	public boolean waitForDone(long timeout, TimeUnit unit) throws InterruptedException {
		return m_aopGuard.awaitWhile(() -> {
			switch ( m_aopState ) {
				case NOT_STARTED:
				case RUNNING:
				case CANCELLING:
					return true;
				default:
					return false;
			}
		}, timeout, unit);
	}
	
	public Option<Result<T>> pollResult() {
		return m_aopGuard.get(() -> {
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
				case CANCELLED:
					return Option.some(m_result);
				default:
					return Option.none();
			}
		});
	}

	@Override
	public Result<T> waitForResult() throws InterruptedException {
		waitForDone();
		return m_aopGuard.get(() -> m_result);
	}

	@Override
	public Result<T> waitForResult(long timeout, TimeUnit unit) throws InterruptedException,
																	TimeoutException {
		if ( !waitForDone(timeout, unit) ) {
			throw new TimeoutException();
		}
		
		return m_aopGuard.get(() -> m_result);
	}

	@Override
	public State getState() {
		return m_aopGuard.get(() -> m_aopState);
	}

	@Override
	public T get() throws InterruptedException, ExecutionException, CancellationException {
		return waitForResult().get();
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
													TimeoutException, CancellationException {
		if ( !waitForDone(timeout, unit) ) {
			throw new TimeoutException("time=" + timeout + unit);
		}
		
		return m_result.get();
	}
	
	@Override
	public boolean cancel() {
		m_aopLock.lock();
		try {
			while ( m_aopState == State.NOT_STARTED ) {
				m_aopCond.await();
			}
			
			switch ( m_aopState ) {
				case RUNNING:
					// 작업이 cancellable인 경우는 명시적으로 'cancel()'을 호출하고,
					// 그렇지 않은 경우는 수행 쓰레드의 interrupt를 시도한다.
					if ( this instanceof CancellableWork ) {
						if ( !((CancellableWork)this).cancelWork() ) {
							return false;
						}
					}
					else if ( m_thread != null ) {
						m_thread.interrupt();
					}
					m_aopState = State.CANCELLING;
					m_aopCond.signalAll();
				case CANCELLING:
				case CANCELLED:
					return true;
				default:
					return false;
			}
		}
		catch ( InterruptedException e ) {
			return false;
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if ( cancel() ) {
			Try.run(this::waitForDone);
		}
		
		return isCancelled();
	}

	protected boolean notifyStarted(Thread thread) {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_thread = thread;
					m_aopState = State.RUNNING;
					if ( m_startHook.isDefined() ) {
						m_startHook.get().run();
					}
					
					m_aopCond.signalAll();
					notifyStartListeners();
			    	return true;
				case CANCELLING:
					notifyStartListeners();
					notifyCancelled();
					return false;
				case CANCELLED:
					return false;
				default:
					throw new IllegalStateException("unexpected state: " + m_aopState);
			}
			
		}
		catch ( Throwable e ) {
			notifyFailed(e);
			return false;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	protected boolean notifyCompleted(T result) {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case RUNNING:
				case CANCELLING:
					m_result = Result.completed(result);
					m_aopState = State.COMPLETED;
					m_completeHook.forEach(Runnable::run);
					m_aopCond.signalAll();
			    	notifyFinishListeners();
			    	return true;
				case COMPLETED:
				case CANCELLED:
				case FAILED:
					return false;
				default:
					throw new IllegalStateException("unexpected state: " + m_aopState);
			}
			
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	protected boolean notifyFailed(Throwable cause) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case NOT_STARTED:	// start 과정에서 오류가 발생된 경우.
				case RUNNING:
				case CANCELLING:
					m_result = Result.failed(cause);
			    	m_aopState = State.FAILED;
			    	m_failureHook.forEach(act -> act.accept(cause));
			    	m_aopCond.signalAll();
			    	notifyFinishListeners();
			    	
			    	return true;
				case COMPLETED:
				case FAILED:
				case CANCELLED:
					return false;
				default:
					throw new IllegalStateException("unexpected state: " + m_aopState);
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
    }
	
	protected boolean notifyCancelled() {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
					return false;
				case RUNNING:
				case CANCELLING:
					m_result = Result.cancelled();
					m_aopState = State.CANCELLED;
					m_cancelHook.forEach(Runnable::run);
					m_aopCond.signalAll();
			    	notifyFinishListeners();
				case CANCELLED:
					return true;
				default:
					throw new IllegalStateException("unexpected state: " + m_aopState);
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
    }

	@Override
	public void whenStarted(Runnable listener) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_startListeners.add(listener);
					break;
				default:
					CompletableFuture.runAsync(listener::run);
					break;
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
	}

	@Override
	public void whenDone(Runnable listener) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
				case CANCELLED:
					CompletableFuture.runAsync(listener::run);
					break;
				default:
					m_finishListeners.add(listener);
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
	}

	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	@Override
	public String toString() {
		switch ( m_aopState ) {
			case FAILED:
				return String.format("%s[%s, cause=%s]", getClass().getSimpleName(), m_aopState,
															m_result.getCause());
			default:
				return String.format("%s[%s]", getClass().getSimpleName(), m_aopState);
		}
	}
	
	protected final ReentrantLock getLock() {
		return m_aopLock;
	}
	
	protected final Condition getCondition() {
		return m_aopCond;
	}
	
	protected void setStartHook(CheckedRunnable action) {
		m_startHook = Option.of(action);
	}
	
	protected void setCompleteHook(Runnable action) {
		m_completeHook = Option.of(action);
	}
	
	protected void setCancelHook(Runnable action) {
		m_cancelHook = Option.of(action);
	}
	
	protected void setFailureHook(Consumer<Throwable> action) {
		m_failureHook = Option.of(action);
	}
	
	protected boolean isCancelRequested() {
		return m_aopGuard.get(() -> {
			switch ( m_aopState ) {
				case CANCELLING:
				case CANCELLED:
					return true;
				default:
					return false;
			}
		});
	}
	
	private void notifyStartListeners() {
		CompletableFuture.runAsync(() -> m_startListeners.forEach(Runnable::run));
	}
	
	private void notifyFinishListeners() {
		CompletableFuture.runAsync(() -> m_finishListeners.forEach(Runnable::run));
	}
	
	@Override
	public void run() {
		if ( !notifyStarted(Thread.currentThread()) ) {
			// 작업 시작에 실패한 경우 (주로 이미 cancel된 경우)는 바로 return한다.
			return;
		}
		
		// 해당 작업이 수행 작업으로 선정된 것을 알린다.
		Executors.s_logger.debug("started: {}", this);
		
		// 작업을 수행한다.
		try {
			T result = executeWork();
			
			switch ( getState() ) {
				case RUNNING:
					// 작업 종료 후에 작업이 종료되었음을 알린다.
					notifyCompleted(result);
					Executors.s_logger.debug("completed: {}, result={}", this, result);
					break;
				case CANCELLING:
					notifyCancelled();
					getLogger().info("cancelled: {}", this);
					break;
				case FAILED:
					Executors.s_logger.info(this.toString());
					break;
				default:
			}
		}
		catch ( InterruptedException | CancellationException e ) {
			notifyCancelled();
			getLogger().info("cancelled: {}", this);
		}
		catch ( Throwable e ) {
			if ( isCancelRequested() ) {
				notifyCancelled();
				getLogger().info("cancelled: {}", this);
			}
			else {
				notifyFailed(e);
				Executors.s_logger.info("failed: {}, cause={}", this, Throwables.unwrapThrowable(e));
			}
		}
	}
	
	public void start() {
		Executors.start(this);
	}
}
