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

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ExecutionHandle<T> implements Execution<T>, LoggerSettable {
	protected enum ImplState { NOT_STARTED, STARTING, RUNNING, COMPLETED, FAILED,
								CANCELLING, CANCELLED };
	
	private ExecutableWork<T> m_work;							
								
	protected final ReentrantLock m_aopLock = new ReentrantLock();
	protected final Condition m_aopCond = m_aopLock.newCondition();
	protected final Guard m_guard = Guard.by(m_aopLock, m_aopCond);
	@GuardedBy("m_aopLock") protected ImplState m_aopState = ImplState.NOT_STARTED;
	@GuardedBy("m_aopLock") private Result<T> m_result;		// may null
	@GuardedBy("m_aopLock") private final List<Runnable> m_startListeners = Lists.newCopyOnWriteArrayList();
	@GuardedBy("m_aopLock") private final List<Runnable> m_finishListeners = Lists.newCopyOnWriteArrayList();
	@GuardedBy("m_aopLock") private Option<CheckedRunnable> m_startHook = Option.none();
	@GuardedBy("m_aopLock") private volatile Option<Runnable> m_completeHook = Option.none();
	@GuardedBy("m_aopLock") private volatile Option<Runnable> m_cancelHook = Option.none();
	@GuardedBy("m_aopLock") private volatile Option<Consumer<Throwable>> m_failureHook = Option.none();

	@GuardedBy("m_aopLock") protected Thread m_thread = null;
	private Logger m_logger = LoggerFactory.getLogger(ExecutionHandle.class);
	
	public ExecutionHandle(ExecutableWork<T> work) {
		m_work = work;
	}
	public ExecutionHandle() {
		m_work = null;
	}
	
	public ExecutableWork<T> getExecutableWork() {
		return m_work;
	}
	
	public void setExecutableWork(ExecutableWork<T> work) {
		m_work = work;
	}
	
	@Override
	public void waitForStarted() throws InterruptedException {
		m_aopLock.lock();
		try {
			while ( m_aopState == ImplState.NOT_STARTED ) {
				m_aopCond.await();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	@Override
	public boolean waitForStarted(long timeout, TimeUnit unit) throws InterruptedException {
		Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
		m_aopLock.lock();
		try {
			while ( m_aopState == ImplState.NOT_STARTED ) {
				if ( !m_aopCond.awaitUntil(due) ) {
					return false;
				}
			}
			
			return true;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public void waitForDone() throws InterruptedException {
		m_aopLock.lock();
		try {
			while ( true ) {
				switch ( m_aopState ) {
					case NOT_STARTED:
					case RUNNING:
					case CANCELLING:
						m_aopCond.await();
						break;
					default:
						return;
				}
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public boolean waitForDone(long timeout, TimeUnit unit) throws InterruptedException {
		Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
		
		m_aopLock.lock();
		try {
			while ( true ) {
				switch ( m_aopState ) {
					case NOT_STARTED:
					case RUNNING:
					case CANCELLING:
						if ( !m_aopCond.awaitUntil(due) ) {
							return false;
						}
						break;
					default:
						return true;
				}
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	public Option<Result<T>> pollResult() {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
				case CANCELLED:
					return Option.some(m_result);
				default:
					return Option.none();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public Result<T> waitForResult() throws InterruptedException {
		waitForDone();
		
		m_aopLock.lock();
		try {
			return m_result;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public Result<T> waitForResult(long timeout, TimeUnit unit) throws InterruptedException,
																	TimeoutException {
		waitForDone(timeout, unit);
		
		m_aopLock.lock();
		try {
			return m_result;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public State getState() {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					return State.NOT_STARTED;
				case RUNNING:
					return State.RUNNING;
				case COMPLETED:
					return State.COMPLETED;
				case FAILED:
					return State.FAILED;
				case CANCELLED:
					return State.CANCELLED;
				case CANCELLING:
					return State.CANCELLING;
				default:
					throw new AssertionError();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isStarted() {
		m_aopLock.lock();
		try {
			return m_aopState != ImplState.NOT_STARTED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isDone() {
		m_aopLock.lock();
		try {
			return m_aopState == ImplState.COMPLETED || m_aopState == ImplState.FAILED
					|| m_aopState == ImplState.CANCELLED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isCompleted() {
		m_aopLock.lock();
		try {
			return m_aopState == ImplState.COMPLETED;
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	@Override
	public final boolean isCancelled() {
		m_aopLock.lock();
		try {
			return m_aopState == ImplState.CANCELLED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final boolean isFailed() {
		m_aopLock.lock();
		try {
			return m_aopState == ImplState.FAILED;
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public T get() throws InterruptedException, ExecutionException, CancellationException {
		waitForDone();
		
		m_aopLock.lock();
		try {
			switch ( getState() ) {
				case COMPLETED:
					return m_result.get();
				case FAILED:
					throw new ExecutionException(m_result.getCause());
				case CANCELLED:
					throw new CancellationException();
				default:
					throw new AssertionError();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
													TimeoutException, CancellationException {
		if ( !waitForDone(timeout, unit) ) {
			throw new TimeoutException("time=" + timeout + unit);
		}
		
		m_aopLock.lock();
		try {
			switch ( getState() ) {
				case COMPLETED:
					return m_result.get();
				case FAILED:
					throw new ExecutionException(m_result.getCause());
				case CANCELLED:
					throw new CancellationException();
				default:
					throw new AssertionError();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	@Override
	public boolean cancel() {
		m_aopLock.lock();
		try {
			while ( m_aopState == ImplState.NOT_STARTED ) {
				m_aopCond.await();
			}
			
			switch ( m_aopState ) {
				case RUNNING:
					// 작업이 cancellable인 경우는 명시적으로 'cancel()'을 호출하고,
					// 그렇지 않은 경우는 수행 쓰레드의 interrupt를 시도한다.
					if ( m_work != null && m_work instanceof CancellableWork ) {
						if ( !((CancellableWork)m_work).cancelWork() ) {
							return false;
						}
					}
					else if ( m_thread != null ) {
						m_thread.interrupt();
					}
					m_aopState = ImplState.CANCELLING;
					m_aopCond.signalAll();
				case CANCELLING:
				case CANCELLED:
					return true;
				default:
					return false;
			}
		}
		catch ( InterruptedException e ) {
			throw new RuntimeException();
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		cancel();
		Try.run(this::waitForDone);
		
		return isCancelled();
	}

	public boolean notifyStarted(Thread thread) {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_thread = thread;
					m_aopState = ImplState.RUNNING;
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

	public boolean notifyCompleted(T result) {
		m_aopLock.lock();
		try {
			switch ( m_aopState ) {
				case RUNNING:
				case CANCELLING:
					m_result = Result.completed(result);
					m_aopState = ImplState.COMPLETED;
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
	
    public boolean notifyFailed(Throwable cause) {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case NOT_STARTED:	// start 과정에서 오류가 발생된 경우.
				case RUNNING:
				case CANCELLING:
					m_result = Result.failed(cause);
			    	m_aopState = ImplState.FAILED;
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
	
    public boolean notifyCancelled() {
    	m_aopLock.lock();
    	try {
			switch ( m_aopState ) {
				case COMPLETED:
				case FAILED:
					return false;
				case RUNNING:
				case CANCELLING:
					m_result = Result.cancelled();
					m_aopState = ImplState.CANCELLED;
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
		return String.format("%s[%s]", getClass().getSimpleName(), m_aopState);
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
		return m_guard.get(() -> {
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
}
