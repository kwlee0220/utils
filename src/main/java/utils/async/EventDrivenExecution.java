package utils.async;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import io.vavr.control.Option;
import net.jcip.annotations.GuardedBy;
import utils.Guard;
import utils.LoggerSettable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class EventDrivenExecution<T> implements Execution<T>, LoggerSettable {
	private long m_timeoutMillis = TimeUnit.SECONDS.toMillis(3);	// 3 seconds
	protected final ReentrantLock m_aopLock = new ReentrantLock();
	protected final Condition m_aopCond = m_aopLock.newCondition();
	protected final Guard m_aopGuard = Guard.by(m_aopLock, m_aopCond);
	@GuardedBy("m_aopLock") private State m_aopState = State.NOT_STARTED;
	@GuardedBy("m_aopLock") private Result<T> m_result;		// may null
	@GuardedBy("m_aopLock") private final List<Runnable> m_startListeners = Lists.newCopyOnWriteArrayList();
	@GuardedBy("m_aopLock") private final List<Runnable> m_finishListeners = Lists.newCopyOnWriteArrayList();
	
	private Logger m_logger = LoggerFactory.getLogger(EventDrivenExecution.class);

	@Override
	public State getState() {
		return m_aopGuard.get(() -> m_aopState);
	}
	
	public void setTimeout(long timeout, TimeUnit unit) {
		m_timeoutMillis = unit.toMillis(timeout);
	}
	
	public void complate(T result) {
		notifyCompleted(result);
	}

	@Override
	public boolean cancel() {
		return notifyCancelled();
	}

	@Override
	public T get() throws InterruptedException, ExecutionException, CancellationException {
		return waitForResult().get();
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
													TimeoutException, CancellationException {
		return waitForResult(timeout, unit).get();
	}
	
	@Override
	public void waitForStarted() throws InterruptedException {
		m_aopGuard.awaitUntil(() -> m_aopState.ordinal() >= State.RUNNING.ordinal());
	}
	
	@Override
	public boolean waitForStarted(long timeout, TimeUnit unit) throws InterruptedException {
		return m_aopGuard.awaitUntil(() -> m_aopState.ordinal() >= State.RUNNING.ordinal(), timeout, unit);
	}

	@Override
	public void waitForDone() throws InterruptedException {
		m_aopGuard.awaitUntil(this::isDoneInGuard);
	}

	@Override
	public boolean waitForDone(long timeout, TimeUnit unit) throws InterruptedException {
		return m_aopGuard.awaitUntil(this::isDoneInGuard, timeout, unit);
	}

	@Override
	public Option<Result<T>> pollResult() {
		return m_aopGuard.get(() -> isDoneInGuard() ? Option.some(m_result) : Option.none());
	}

	@Override
	public Result<T> waitForResult() throws InterruptedException {
		return m_aopGuard.awaitUntilAndGet(this::isDoneInGuard, () -> m_result);
	}

	@Override
	public Result<T> waitForResult(long timeout, TimeUnit unit) throws InterruptedException,
																	TimeoutException {
		return m_aopGuard.awaitUntilAndGet(this::isDoneInGuard, () -> m_result, timeout, unit);
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
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_aopState = State.STARTING;
					m_aopCond.signalAll();
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
												m_aopState, State.STARTING);
					throw new IllegalStateException(msg);
			}
			
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	public boolean notifyStarted() {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
				case STARTING:
					m_aopState = State.RUNNING;
					m_aopCond.signalAll();
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
												m_aopState, State.RUNNING);
					throw new IllegalStateException(msg);
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	public boolean notifyCompleted(T result) {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case RUNNING:
				case CANCELLING:
					m_result = Result.completed(result);
					m_aopState = State.COMPLETED;
					m_aopCond.signalAll();
					getLogger().debug("completed: {}, result={}", this, result);
					
			    	notifyFinishListeners();
			    	return true;
				case CANCELLED:
				case FAILED:
				case COMPLETED:
					return false;
				case NOT_STARTED:
				case STARTING:
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, State.COMPLETED);
					throw new IllegalStateException(msg);
			}
			
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	public boolean notifyFailed(Throwable cause) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case STARTING:		// start 과정에서 오류가 발생된 경우.
				case RUNNING:
				case CANCELLING:
					m_result = Result.failed(cause);
			    	m_aopState = State.FAILED;
			    	m_aopCond.signalAll();
					getLogger().info("failed: {}, cause={}", this, cause.toString());
					
			    	notifyFinishListeners();
			    	return true;
				case FAILED:
				case COMPLETED:
				case CANCELLED:
					return false;
				case NOT_STARTED:
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, State.FAILED);
					throw new IllegalStateException(msg);
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
    }
	
	public boolean notifyCancelling() {
    	m_aopLock.lock();
    	try {
    		// 시작 중인 상태이면, cancel 작업이 inconsistent한 상태를 볼 수 있기 때문에,
    		// 일단 start작업이 완료될 때까지 대기한다.
    		Date due = new Date(System.currentTimeMillis() + m_timeoutMillis);
			while ( m_aopState == State.STARTING ) {
				try {
					if ( !m_aopCond.awaitUntil(due) ) {
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
					m_aopState = State.CANCELLING;
					m_aopCond.signalAll();
				case CANCELLING:
				case CANCELLED:
					return true;
				case COMPLETED:
				case FAILED:
					return false;
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, State.CANCELLING);
					throw new IllegalStateException(msg);
			}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
    }
	
	public boolean notifyCancelled() {
    	m_aopLock.lock();
    	try {
    		// 시작 중인 상태이면, cancel 작업이 inconsistent한 상태를 볼 수 있기 때문에,
    		// 일단 start작업이 완료될 때까지 대기한다.
    		Date due = new Date(System.currentTimeMillis() + m_timeoutMillis);
    		try {
				while ( m_aopState == State.STARTING ) {
					if ( !m_aopCond.awaitUntil(due) ) {
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
					m_result = Result.cancelled();
					m_aopState = State.CANCELLED;
					m_aopCond.signalAll();
					getLogger().info("cancelled: {}", this);
					
			    	notifyFinishListeners();
				case CANCELLED:
			    	return true;
				case COMPLETED:
				case FAILED:
					return false;
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, State.CANCELLED);
					throw new IllegalStateException(msg);
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
				case STARTING:
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
    		if ( isDoneInGuard() ) {
				CompletableFuture.runAsync(() -> listener.run());
    		}
    		else {
				m_finishListeners.add(() -> listener.run());
    		}
    	}
    	finally {
    		m_aopLock.unlock();
    	}
	}
	
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), m_aopState);
	}
	
	private void notifyStartListeners() {
		CompletableFuture.runAsync(() -> m_startListeners.forEach(Runnable::run));
	}
	
	private void notifyFinishListeners() {
		CompletableFuture.runAsync(() -> m_finishListeners.forEach(Runnable::run));
	}
	
	private static boolean isDone(State state) {
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
