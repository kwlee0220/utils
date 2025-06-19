package utils.async;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.base.Preconditions;

import utils.RuntimeExecutionException;
import utils.RuntimeTimeoutException;
import utils.Throwables;
import utils.func.CheckedConsumerX;
import utils.func.CheckedRunnable;
import utils.func.CheckedRunnableX;
import utils.func.CheckedSupplier;
import utils.func.CheckedSupplierX;
import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Guard implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private final Lock m_lock;
	private final Condition m_cond;
	
	public static Guard create() {
		Lock lock = new ReentrantLock();
		return new Guard(lock, lock.newCondition());
	}
	
	private Guard(Lock lock, Condition cond) {
		Preconditions.checkArgument(lock != null, "lock is null");
		Preconditions.checkArgument(cond != null, "Condition is null");
		
		m_lock = lock;
		m_cond = cond;
	}
	
	/**
	 * Acquires this lock for the guard. 
	 */
	public void lock() {
		m_lock.lock();
	}
	
	/**
	 * Releases this lock for the guard.
	 */
	public void unlock() {
		m_lock.unlock();
	}
	
	/**
	 * Signals all waiting threads.
	 * This method should be called while holding the lock.
	 */
	public void signalAll() {
		m_cond.signalAll();
	}

	/**
	 * 시그널을 받거나 interrupt될 때까지 대기한다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 */
	public void awaitSignal() throws InterruptedException {
		m_cond.await();
	}

	/**
	 * Guard가 signal을 받거나 주어진 제한 시각을 경과할 때까지 대기한다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 * 
	 * @param due	대기 제한 시각
	 * @return	대기 제한 시각이 경과하기 전에 signal을 받아 대기가 멈춘 경우는 {@code true},
	 * 			그렇지 않고 시간 제한으로 대기가 멈춘 경우는 {@code false}.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public boolean awaitSignal(Date due) throws InterruptedException {
		Preconditions.checkArgument(due != null, "due is null");
		
		return m_cond.awaitUntil(due);
	}
	
	/**
	 * Guard가 signal을 받거나 주어진 제한 시간이 도달할 때까지 대기한다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 * 
	 * @param dur	대기 제한 기간
	 * @return	대기 제한 시각이 경과하기 전에 signal을 받아 대기가 멈춘 경우는 {@code true},
	 * 			그렇지 않고 시간 제한으로 대기가 멈춘 경우는 {@code false}.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public boolean awaitSignal(Duration dur) throws InterruptedException {
		Instant due = Instant.now().plus(dur);
		return m_cond.awaitUntil(Date.from(due));
	}
	
	public PreAction preAction(Runnable action) {
		return new PreAction(this, action);
	}
	
	/**
	 * 주어진 조건이 만족할 때까지 지정된 시각까지 대기한다.
	 * 
	 * @param condition	대기 조건
	 */
	public AwaitCondition awaitCondition(Supplier<Boolean> condition) {
		return new AwaitCondition(this, null, condition);
	}
	
	/**
	 * 주어진 조건이 만족할 때까지 지정된 시각까지 대기한다.
	 * <p>
	 * 주어진 시각을 초과하는 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
	 *
	 * @param condition	대기 조건
	 * @param due	대기 제한 시각
	 * @return	{@link TimedAwaitCondition} 객체
	 */
	public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, Date due) {
		return new TimedAwaitCondition(this, null, condition, due);
	}
	
	public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, Duration timeout) {
		return new TimedAwaitCondition(this, null, condition, timeout);
	}
	
	public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, long timeout, TimeUnit unit) {
		return new TimedAwaitCondition(this, null, condition, timeout, unit);
	}
	
	/**
	 * 주어진 작업을 수행한다.
	 * <p>
	 * 본 메소드는 lock을 획득한 상태에서 주어진 작업을 수행한다.
	 * 작업이 완료되면 모든 대기 중인 쓰레드에게 signal을 보낸다.
	 * 
	 * @param work to run.
	 */
	public void run(Runnable work) {
		Preconditions.checkArgument(work != null, "work is null");
		
		m_lock.lock();
		try {
			work.run();
			m_cond.signalAll();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <X extends Throwable> void runChecked(CheckedRunnableX<X> work) throws X {
		Preconditions.checkArgument(work != null, "work is null");
		
		m_lock.lock();
		try {
			work.run();
			m_cond.signalAll();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> T get(Supplier<T> suppl) {
		m_lock.lock();
		try {
			T value = suppl.get();
			m_cond.signalAll();
			return value;
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T,X extends Throwable> T getChecked(CheckedSupplierX<T,X> suppl) throws X {
		m_lock.lock();
		try {
			T value = suppl.get();
			m_cond.signalAll();
			return value;
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> void accept(Consumer<T> consumer, T value) {
		m_lock.lock();
		try {
			consumer.accept(value);
			m_cond.signalAll();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T,X extends Throwable> void acceptChecked(CheckedConsumerX<T,X> consumer, T value) throws X {
		m_lock.lock();
		try {
			consumer.accept(value);
			m_cond.signalAll();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public static class PreAction implements Serializable {
		private static final long serialVersionUID = 1L;
		
		protected final Guard m_guard;
		private final Runnable m_action;
		
		PreAction(Guard guard, Runnable action) {
			Preconditions.checkArgument(guard != null, "Guard is null");
			Preconditions.checkArgument(action != null, "action is null");

	        m_guard = guard;
	        m_action = action;
		}
		
		/**
		 * Waits until the precondition is satisfied.
		 * 
		 * @param condition precondition
		 * @throws InterruptedException if the current thread is interrupted while waiting.
		 */
		public AwaitCondition awaitCondition(Supplier<Boolean> condition) {
			return new AwaitCondition(m_guard, m_action, condition);
		}
		
		public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, Date due) {
			return new TimedAwaitCondition(m_guard, m_action, condition, due);
		}
		
		public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, Duration timeout) {
			return new TimedAwaitCondition(m_guard, m_action, condition, timeout);
		}
		
		public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, long timeout, TimeUnit unit) {
			return new TimedAwaitCondition(m_guard, m_action, condition, timeout, unit);
		}
	}
	
	public static class AwaitCondition implements Serializable {
		private static final long serialVersionUID = 1L;
		
		protected final Guard m_guard;
		private final Runnable m_preAction;
		private Supplier<Boolean> m_precondition;
		
		AwaitCondition(Guard guard, Runnable preAction, Supplier<Boolean> precondition) {
			Preconditions.checkArgument(guard != null, "Guard is null");
			Preconditions.checkArgument(precondition != null, "precondition is null");

	        m_guard = guard;
			m_preAction = preAction;
	        m_precondition = precondition;
		}

		public void andReturn() throws InterruptedException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * 
		 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
		 */
		public void andRun(Runnable task) throws InterruptedException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();

				task.run();
				m_guard.signalAll();
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link ExecutionException}을 발생시킨다.
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 * 
		 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws ExecutionException 작업 ({@code work}) 수행 중 예외가 발생한 경우.
		 */
		public void andRunChecked(CheckedRunnable task) throws InterruptedException, ExecutionException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();

				try {
					task.run();
					m_guard.signalAll();
				}
				catch ( Throwable e ) {
					Throwable cause = Throwables.unwrapThrowable(e);
					throw new ExecutionException(cause);
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 * 
		 * @return 작업의 결과 값.
		 * @throws InterruptedException	대기 과정 중에 쓰레드가 중단된 경우.
		 */
		public <T> T andGet(Supplier<T> supplier) throws InterruptedException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();
				
				T result = supplier.get();
				m_guard.signalAll();
				return result;
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link RuntimeExecutionException}을 발생시킨다.
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 * 
		 * @return 작업의 결과 값.
		 * @throws InterruptedException	대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws ExecutionException 		대기 제한 시각을 경과한 경우.
		 * @throws TimeoutException	작업 ({@code supplier}) 수행 중 예외가 발생한 경우.
		 */
		public <T> T andGetChecked(CheckedSupplier<T> supplier) throws InterruptedException, ExecutionException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();
				
				try {
					T result = supplier.get();
					m_guard.signalAll();
					
					return result;
				}
				catch ( Throwable e ) {
					Throwable cause = Throwables.unwrapThrowable(e);
					throw new ExecutionException(cause);
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 사전 조건이 만족할 때까지 대기한다.
		 * <p>
		 * {@link #m_precondition}이 {@code true}가 될 때까지 대기한다.
		 * 본 메소드는 {@link Guard} 객체의 lock을 획득한 상태에서 호출되어야 한다.
		 * 
		 * @throws InterruptedException	대기 중에 interrupt가 발생한 경우.
		 */
		private void awaitConditionSatisfied() throws InterruptedException {
			if ( m_preAction != null ) {
                m_preAction.run();
			}
			
			while ( !m_precondition.get() ) {
				m_guard.awaitSignal();
			}
		}
	}
	
	public static class TimedAwaitCondition implements Serializable {
		private static final long serialVersionUID = 1L;
		
		protected final Guard m_guard;
		private final Runnable m_preAction;
		private Supplier<Boolean> m_precondition;
		@Nullable private Date m_due;
		@Nullable private Duration m_timeout;
		
		TimedAwaitCondition(Guard guard, Runnable preAction, Supplier<Boolean> precondition) {
			Preconditions.checkArgument(guard != null, "Guard is null");
			Preconditions.checkArgument(precondition != null, "Precondition is null");
			
	        m_guard = guard;
	        m_preAction = preAction;
	        m_precondition = precondition;
		}
		
		TimedAwaitCondition(Guard guard, Runnable preAction, Supplier<Boolean> precondition, Date due) {
			Preconditions.checkArgument(guard != null, "Guard is null");
			Preconditions.checkArgument(precondition != null, "Precondition is null");
			Preconditions.checkArgument(due != null, "Due is null");
			
	        m_guard = guard;
	        m_preAction = preAction;
	        m_precondition = precondition;
	        m_due = due;
		}
		
		TimedAwaitCondition(Guard guard, Runnable preAction, Supplier<Boolean> precondition, Duration timeout) {
			Preconditions.checkArgument(guard != null, "Guard is null");
			Preconditions.checkArgument(precondition != null, "Precondition is null");
			Preconditions.checkArgument(timeout != null, "Timeout is null");
			
	        m_guard = guard;
	        m_preAction = preAction;
	        m_precondition = precondition;
	        m_timeout = timeout;
		}
		
		TimedAwaitCondition(Guard guard, Runnable preAction, Supplier<Boolean> precondition, long timeout,
							TimeUnit unit) {
			Preconditions.checkArgument(guard != null, "Guard is null");
			Preconditions.checkArgument(precondition != null, "Precondition is null");
			Preconditions.checkArgument(timeout > 0, "Timeout is larger than zero");
			Preconditions.checkArgument(unit != null, "TimeUnit is null");
			
	        m_guard = guard;
	        m_preAction = preAction;
	        m_precondition = precondition;
	
			switch ( unit ) {
				case NANOSECONDS:
	                m_timeout = Duration.ofNanos(timeout);
	            case MICROSECONDS:
	            	m_timeout = Duration.ofNanos(TimeUnit.MICROSECONDS.toNanos(timeout));
	            case MILLISECONDS:
	            	m_timeout = Duration.ofMillis(timeout);
	            case SECONDS:
	            	m_timeout = Duration.ofSeconds(timeout);
	            case MINUTES:
	            	m_timeout = Duration.ofMinutes(timeout);
	            case HOURS:
	            	m_timeout = Duration.ofHours(timeout);
	            case DAYS:
	            	m_timeout = Duration.ofDays(timeout);
	            default:
	                throw new IllegalArgumentException("Unknown TimeUnit: " + unit);
			}
		}
	
		public boolean andReturn() throws InterruptedException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();
				return true;
			}
			catch ( TimeoutException e ) {
				return false;
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link RuntimeExecutionException}을 발생시킨다.
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 * 
		 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws TimeoutException 대기 제한 시각을 경과한 경우.
		 */
		public void andRun(Runnable task) throws InterruptedException, TimeoutException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();
	
				task.run();
				m_guard.signalAll();
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link RuntimeExecutionException}을 발생시킨다.
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 * 
		 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws TimeoutException 대기 제한 시각을 경과한 경우.
		 * @throws ExecutionException 작업 ({@code work}) 수행 중 예외가 발생한 경우.
		 */
		public void andRunChecked(CheckedRunnable task) throws InterruptedException, TimeoutException, ExecutionException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();
				
				try {
					task.run();
					m_guard.signalAll();
				}
				catch ( Throwable e ) {
					Throwable cause = Throwables.unwrapThrowable(e);
					throw new ExecutionException(cause);
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link RuntimeExecutionException}을 발생시킨다.
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 * 
		 * @return 작업의 결과 값.
		 * @throws InterruptedException	대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws TimeoutException	작업 ({@code supplier}) 수행 중 예외가 발생한 경우.
		 */
		public <T> T andGet(Supplier<T> supplier) throws InterruptedException, TimeoutException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();
				
				T result = supplier.get();
				m_guard.signalAll();
				return result;
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link RuntimeExecutionException}을 발생시킨다.
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 * 
		 * @return 작업의 결과 값.
		 * @throws InterruptedException	대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws ExecutionException 		대기 제한 시각을 경과한 경우.
		 * @throws TimeoutException	작업 ({@code supplier}) 수행 중 예외가 발생한 경우.
		 */
		public <T> T andGetChecked(CheckedSupplier<T> supplier) throws InterruptedException, TimeoutException, ExecutionException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();
				
				try {
					T result = supplier.get();
					m_guard.signalAll();
					
					return result;
				}
				catch ( Throwable e ) {
					Throwable cause = Throwables.unwrapThrowable(e);
					throw new ExecutionException(cause);
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 사전 조건이 만족할 때까지 대기한다.
		 * <p>
		 * 사전 조건이 없는 경우에는 바로 반환한다.
		 * 본 메소드는 {@link Guard} 객체의 lock을 획득한 상태에서 호출되어야 한다.
		 * 
		 * @throws RuntimeTimeoutException	사전 조건 대기 시간이 경과한 경우.
		 * @throws InterruptedException	대기 중에 interrupt가 발생한 경우.
		 */
		private void awaitPreconditionSatisfied() throws InterruptedException, TimeoutException {
			Date due = m_due;
			if ( m_due == null ) {
				due = FOption.map(m_timeout, ts -> Date.from(Instant.now().plus(ts)));
			}
			
			if ( m_preAction != null ) {
                m_preAction.run();
			}
			
			while ( !m_precondition.get() ) {
				if ( !m_guard.awaitSignal(due) ) {
					String msg = (m_due != null) ? String.format("due=%s", due)
													: String.format("timeout=%s", m_timeout);
					throw new TimeoutException(msg);
				}
			}
		}
	}
	
}
