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
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import utils.Throwables;
import utils.func.CheckedConsumer;
import utils.func.CheckedFunction;
import utils.func.CheckedRunnable;
import utils.func.CheckedRunnableX;
import utils.func.CheckedSupplier;
import utils.func.CheckedSupplierX;
import utils.func.Try;


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
	
	public static Guard by(Lock lock) {
		return new Guard(lock);
	}
	
	public static Guard by(Lock lock, Condition cond) {
		return new Guard(lock, cond);
	}
	
	private Guard(Lock lock) {
		Preconditions.checkArgument(lock != null, "lock is null");
		
		m_lock = lock;
		m_cond = null;
	}
	
	private Guard(Lock lock, Condition cond) {
		Preconditions.checkArgument(lock != null, "lock is null");
		Preconditions.checkArgument(cond != null, "Condition is null");
		
		m_lock = lock;
		m_cond = cond;
	}
	
	public Lock getLock() {
		return m_lock;
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
	public void signalAllInGuard() {
		m_cond.signalAll();
	}

	/**
	 * Wait until this guard is signaled.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 */
	public void awaitInGuard() throws InterruptedException {
		m_cond.await();
	}

	/**
	 * Guard가 signal을 받거나 주어진 제한 시각을 경과할 때까지 대기한다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 * 
	 * @param due	대기 제한 시각
	 * @return	대기 제한 시각이 경과하기 전에 signal을 받은 경우는 {@code true}, 그렇지 않은 경우는 {@code false}.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public boolean awaitInGuardUntil(Date due) throws InterruptedException {
		Preconditions.checkArgument(due != null, "due is null");
		
		return m_cond.awaitUntil(due);
	}
	
	/**
	 * Guard가 signal을 받거나 주어진 제한 시간이 도달할 때까지 대기한다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 * 
	 * @param dur	대기 제한 기간
	 * @return	대기 제한 시간이 경과하기 전에 signal을 받은 경우는 {@code true}, 그렇지 않은 경우는 {@code false}.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public boolean awaitInGuardFor(Duration dur) throws InterruptedException {
		Preconditions.checkArgument(dur != null, "period is null");
		
		Instant due = Instant.now().plus(dur);
		return m_cond.awaitUntil(Date.from(due));
	}
	
	/**
	 * Get the condition object associated with this guard.
	 * 
	 * @return	condition
	 */
	public Condition getCondition() {
		return m_cond;
	}
	
	/**
	 * Runs the given work while holding the lock.
	 * 
	 * @param work to run.
	 */
	public void run(Runnable work) {
		Preconditions.checkArgument(work != null, "work is null");
		
		m_lock.lock();
		try {
			work.run();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Runs the given work while holding the lock. If the work throws a checked
	 * exception, it rethrows the exception.
	 * 
	 * @param work to run.
	 * @throws X if the work throws a checked exception.
	 */
	public <X extends Throwable> void runOrThrow(CheckedRunnableX<X> work) throws X {
		Preconditions.checkArgument(work != null, "work is null");
		
		m_lock.lock();
		try {
			work.run();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Runs the given work while holding the lock and
	 * signals all waiting threads when it finishes.
	 * 
	 * @param work to run.
	 */
	public void runAndSignalAll(Runnable work) {
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
	
	/**
	 * Runs the given work while holding the lock and signals all waiting threads when it finishes.
	 * 
	 * @param work to run.
	 * @throws X if the work throws a checked exception.
	 */
	public <X extends Throwable> void runAnSignalAllOrThrow(CheckedRunnableX<X> work) throws X {
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
	
	/**
	 * Runs the given work while holding the lock and returns the {@code Try} object.
	 * 
	 * @param work to run.
	 * @return the result of the work.
	 */
	public Try<Void> tryToRun(CheckedRunnable work) {
		Preconditions.checkArgument(work != null, "work is null");
		
		m_lock.lock();
		try {
			work.run();
			return Try.success(null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public GuardedRunnable lift(Runnable task) {
		return new GuardedRunnable(task);
	}
	
	public class GuardedRunnable implements Runnable {
		private final Runnable m_task;
		
		GuardedRunnable(Runnable task) {
			Preconditions.checkArgument(task != null, "task is null");
			
			m_task = task;
		}

		@Override
		public void run() {
			m_lock.lock();
			try {
				m_task.run();
			}
			finally {
				m_lock.unlock();
			}
		}
	}
	
	public <T> T get(Supplier<T> suppl) {
		m_lock.lock();
		try {
			return suppl.get();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T,X extends Throwable> T getOrThrow(CheckedSupplierX<T,X> suppl) throws X {
		m_lock.lock();
		try {
			return suppl.get();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> T get(Supplier<T> suppl, boolean signal) {
		m_lock.lock();
		try {
			T result = suppl.get();
			if ( signal ) {
				m_cond.signalAll();
			}
			
			return result;
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> Try<T> tryToGet(CheckedSupplier<T> suppl, boolean signal) {
		m_lock.lock();
		try {
			return Try.success(suppl.get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
		finally {
			m_cond.signalAll();
			m_lock.unlock();
		}
	}
	public <T> Try<T> tryToGet(CheckedSupplier<T> suppl) {
		return tryToGet(suppl, true);
	}
	
	public <T> GuardedSupplier<T> lift(Supplier<T> suppl) {
		return new GuardedSupplier<>(suppl);
	}
	
	public class GuardedSupplier<T> implements Supplier<T> {
		private final Supplier<T> m_suppl;
		private boolean m_signal = false;
		
		GuardedSupplier(Supplier<T> suppl) {
			Preconditions.checkArgument(suppl != null, "Supplier is null");
			
			m_suppl = suppl;
		}
		
		public GuardedSupplier<T> signalAll() {
			m_signal = true;
			return this;
		}

		@Override
		public T get() {
			m_lock.lock();
			try {
				T ret = m_suppl.get();
				if ( m_signal ) {
					m_cond.signalAll();
				}
				
				return ret;
			}
			finally {
				m_lock.unlock();
			}
		}
	}
	
	public <T> void consume(Consumer<? super T> consumer, T data) {
		m_lock.lock();
		try {
			consumer.accept(data);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> void consume(Consumer<? super T> consumer, T data, boolean signal) {
		m_lock.lock();
		try {
			consumer.accept(data);
			if ( signal ) {
				m_cond.signalAll();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> Try<Void> tryToConsume(CheckedConsumer<? super T> consumer, T data, boolean signal) {
		m_lock.lock();
		try {
			consumer.accept(data);
			if ( signal ) {
				m_cond.signalAll();
			}
			return Try.success(null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> GuardedConsumer<T> lift(Consumer<T> consumer) {
		return new GuardedConsumer<>(consumer);
	}
	
	public class GuardedConsumer<T> implements Consumer<T> {
		private final Consumer<T> m_consumer;
		private boolean m_signal = false;
		
		GuardedConsumer(Consumer<T> consumer) {
			Preconditions.checkArgument(consumer != null, "Consumer is null");
			
			m_consumer = consumer;
		}
		
		public GuardedConsumer<T> signalAll() {
			m_signal = true;
			return this;
		}

		@Override
		public void accept(T data) {
			m_lock.lock();
			try {
				m_consumer.accept(data);
				if ( m_signal ) {
					m_cond.signalAll();
				}
			}
			finally {
				m_lock.unlock();
			}
		}
	}
	
	public <T,R> R apply(Function<T,R> func, T data) {
		m_lock.lock();
		try {
			return func.apply(data);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T,R> R apply(Function<T,R> func, T data, boolean signal) {
		m_lock.lock();
		try {
			R result = func.apply(data);
			if ( signal ) {
				m_cond.signalAll();
			}
			
			return result;
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T,R> Try<R> tryToApply(CheckedFunction<T,R> func, T data, boolean signal) {
		m_lock.lock();
		try {
			R result = func.apply(data);
			if ( signal ) {
				m_cond.signalAll();
			}
			
			return Try.success(result);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T,R> GuardedFunction<T,R> lift(Function<T,R> func) {
		return new GuardedFunction<>(func);
	}
	
	public class GuardedFunction<T,R> implements Function<T,R> {
		private final Function<T,R> m_func;
		private boolean m_signal = false;
		
		GuardedFunction(Function<T,R> func) {
			Preconditions.checkArgument(func != null, "Function is null");
			
			m_func = func;
		}
		
		public GuardedFunction<T,R> signalAll() {
			m_signal = true;
			return this;
		}

		@Override
		public R apply(T data) {
			m_lock.lock();
			try {
				R ret = m_func.apply(data);
				if ( m_signal ) {
					m_cond.signalAll();
				}
				
				return ret;
			}
			finally {
				m_lock.unlock();
			}
		}
	}
	
	/**
	 * Waits until the given condition is satisfied.
	 * 
	 * @param predicate until-condition
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 */
	public void awaitUntil(Supplier<Boolean> predicate) throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "Until-condition is null");
		Preconditions.checkState(m_cond != null, "Condition is null");
		
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				m_cond.await();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * 주어진 조건이 만족할 때까지 대기하고 {@code true}를 반환한다.
	 * <p>
	 * 대기 중 제한 시간이 경과한 경우에는 대기가 종료되고 {@code false}를 반환한다.
	 * 
	 * @param predicate    대기 종료 조건
	 * @param due    	대기 제한 시각
	 * @return	주어진 조건을 만족하여 대기가 종료된 경우는 {@code true}, 그렇지 않은 않고
	 *             제한 시간이 경과한 경우는 {@code false}를 반환한다.
	 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
	 */
	public boolean awaitUntil(Supplier<Boolean> predicate, Date due)
		throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "Until-condition is null");
		Preconditions.checkArgument(due != null, "due is null");
		
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				if ( !m_cond.awaitUntil(due) ) {
					return false;
				}
			}
			
			return true;
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * 주어진 조건이 만족할 때까지 대기하고 {@code true}를 반환한다.
	 * <p>
	 * 대기 중 제한 시간이 경과한 경우에는 대기가 종료되고 {@code false}를 반환한다.
	 * 
	 * @param predicate until-condition
	 * @param timeout   timeout
	 * @return	주어진 조건을 만족하여 대기가 종료된 경우는 {@code true}, 그렇지 않은 않고
	 *             제한 시간이 경과한 경우는 {@code false}를 반환한다.
	 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
	 */
	public boolean awaitUntil(Supplier<Boolean> predicate, Duration timeout)
		throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "Until-condition is null");
		Preconditions.checkArgument(timeout != null, "Timeout duration is null");
		
		return awaitUntil(predicate, Date.from(Instant.now().plus(timeout)));
	}
	
	/**
	 * 주어진 조건이 만족할 때까지 대기하고 {@code true}를 반환한다.
	 * <p>
	 * 대기 중 제한 시간이 경과한 경우에는 대기가 종료되고 {@code false}를 반환한다.
	 * 
	 * @param predicate until-condition
	 * @param timeout   timeout
	 * @param tu        time unit of the timeout
	 * @return	주어진 조건을 만족하여 대기가 종료된 경우는 {@code true}, 그렇지 않은 않고
	 *             제한 시간이 경과한 경우는 {@code false}를 반환한다.
	 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
	 */
	public boolean awaitUntil(Supplier<Boolean> predicate, long timeout, TimeUnit tu)
		throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "Until-condition is null");
		Preconditions.checkArgument(timeout >= 0, "invalid timeout: " + timeout);
		Preconditions.checkArgument(tu != null, "TimeUnit is null");
		
		Date due = new Date(System.currentTimeMillis() + tu.toMillis(timeout));
		return awaitUntil(predicate, due);
	}
	
	/**
	 * 주어진 조건이 만족할 때까지 대기하고 대기 조건이 만족한 경우 주어진 작업을 실행하고,
	 * 내부 condition variable을 signal한다.
	 * <p>
	 * 작업은 내부 lock을 획득한 상태에서 실행된다.
	 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
	 * 대기 조건 만족 후 작업 수행 중 예외가 발생한 경우에는 {@link ExecutionException}을 발생시킨다.
	 * 
	 * @param predicate    대기 종료 조건
	 * @param timeout		대기 제한 시각
	 * @param work      	대기 종료 조건이 만족한 경우 수행할 작업.
	 * @param signal		작업 수행 후 condition variable을 signal할지 여부.
	 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
	 * @throws TimeoutException 대기 제한 시각을 경과한 경우.
	 * @throws ExecutionException 작업 ({@code work}) 수행 중 예외가 발생한 경우.
	 */
	public void awaitUntilAndRun(Supplier<Boolean> predicate, Date due, CheckedRunnable work, boolean signal)
		throws InterruptedException, TimeoutException, ExecutionException {
		Preconditions.checkArgument(predicate != null, "Until-condition is null");
		Preconditions.checkArgument(due != null, "due is null");
		Preconditions.checkArgument(work != null, "work is null");
		
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				if ( !m_cond.awaitUntil(due) ) {
					throw new TimeoutException("due=" + due);
				}
			}
			try {
				work.run();
			}
			catch ( Throwable e ) {
				Throwable cause = Throwables.unwrapThrowable(e);
				throw new ExecutionException(cause);
			}
			
			if ( signal ) {
				m_cond.signalAll();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * 주어진 조건이 만족할 때까지 무한히 대기하고 대기 조건이 만족한 경우 주어진 작업을 실행한다.
	 * <p>
	 * 작업은 내부 lock을 획득한 상태에서 실행된다.
	 * 
	 * @param predicate    대기 종료 조건
	 * @param work      	대기 종료 조건이 만족한 경우 수행할 작업.
	 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
	 */
	public void awaitUntilAndRun(Supplier<Boolean> predicate, Runnable work)
		throws InterruptedException {
		awaitUntilAndRun(predicate, work, false);
	}
	
	/**
	 * 주어진 조건이 만족할 때까지 무한히 대기하고 대기 조건이 만족한 경우 주어진 작업을 실행하고,
	 * 내부 condition variable을 signal한다.
	 * <p>
	 * 작업은 내부 lock을 획득한 상태에서 실행된다.
	 * 
	 * @param predicate    대기 종료 조건
	 * @param work      	대기 종료 조건이 만족한 경우 수행할 작업.
	 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
	 */
	public void awaitUntilAndRun(Supplier<Boolean> predicate, Runnable work, boolean signal)
		throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "Until-condition is null");
		Preconditions.checkArgument(work != null, "work is null");
		
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				m_cond.await();
			}
			work.run();
			
			if ( signal ) {
				m_cond.signalAll();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Waits until the given condition is satisfied and then runs the supplier to get a value.
	 * If the predicate is not satisfied before the deadline, it throws a TimeoutException.
	 * 
	 * @param predicate until-condition.
	 * @param suppl     value supplier.
	 * @param due    deadline. If {@code null}, it waits indefinitely.
	 * @return the value returned by the supplier.
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 * @throws TimeoutException if the predicate is not satisfied before the deadline.
	 */
	public <T> T awaitUntilAndGet(Supplier<Boolean> predicate, CheckedSupplier<T> suppl, Date due, boolean singal)
		throws InterruptedException, TimeoutException, ExecutionException {
		Preconditions.checkArgument(predicate != null, "Until-condition is null");
		Preconditions.checkArgument(suppl != null, "value supplier is null");
		Preconditions.checkArgument(due != null, "due is null");
		
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				if ( !m_cond.awaitUntil(due) ) {
					throw new TimeoutException("due=" + due);
				}
			}
			
			try {
				T result = suppl.get();
                if ( singal ) {
                    m_cond.signalAll();
                }
                
                return result;
			}
			catch ( Throwable e ) {
				Throwable cause = Throwables.unwrapThrowable(e);
                throw new ExecutionException(cause);
            }
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Waits until the given condition is satisfied and then runs the supplier to get a value.
	 * 
	 * @param predicate until-condition.
	 * @param suppl     value supplier.
	 * @return the value returned by the supplier.
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 */
	public <T> T awaitUntilAndGet(Supplier<Boolean> predicate, Supplier<T> suppl)
		throws InterruptedException {
		try {
			return awaitUntilAndGet(predicate, suppl, (Date)null);	
		}
		catch ( TimeoutException neverHappens ) {
			throw new AssertionError();
		}
	}
	
	/**
	 * Waits until the given condition is satisfied and then runs the supplier to get a value.
	 * If the predicate is not satisfied before the deadline, it throws a TimeoutException.
	 * 
	 * @param predicate until-condition.
	 * @param suppl     value supplier.
	 * @param dur       timeout duration. If {@code null}, it waits indefinitely.
	 * @return the value returned by the supplier.
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 * @throws TimeoutException if the predicate is not satisfied before the deadline.
	 */
	public <T> T awaitUntilAndGet(Supplier<Boolean> predicate, Supplier<T> suppl, Duration dur)
		throws InterruptedException, TimeoutException {
		Preconditions.checkArgument(dur != null, "Duration is null");
		
		Date due = Date.from(Instant.now().plus(dur)); 
		return awaitUntilAndGet(predicate, suppl, due);
	}
	
	/**
	 * Waits until the given condition is satisfied and then runs the supplier to get a value.
	 * If the predicate is not satisfied before the deadline, it throws a TimeoutException.
	 * 
	 * @param predicate until-condition.
	 * @param suppl     value supplier.
	 * @param due    deadline. If {@code null}, it waits indefinitely.
	 * @return the value returned by the supplier.
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 * @throws TimeoutException if the predicate is not satisfied before the deadline.
	 */
	public <T> T awaitUntilAndGet(Supplier<Boolean> predicate, Supplier<T> suppl, Date due)
		throws InterruptedException, TimeoutException {
		Preconditions.checkArgument(predicate != null, "Until-condition is null");
		Preconditions.checkArgument(suppl != null, "value supplier is null");
		
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				if ( due == null ) {
					m_cond.await();
				}
				else {
					if ( !m_cond.awaitUntil(due) ) {
						throw new TimeoutException();
					}
				}
			}

			return suppl.get();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Waits until the given condition is satisfied and then runs the supplier to get a value.
	 * 
	 * @param predicate until-condition.
	 * @param suppl     value supplier.
	 * @return the value returned by the supplier.
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 */
	public <T> Try<T> awaitUntilAndTryToGet(Supplier<Boolean> predicate, CheckedSupplier<T> suppl)
		throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "While-condition is null");
		Preconditions.checkArgument(suppl != null, "value supplier is null");
		
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				m_cond.await();
			}
			return Try.success(suppl.get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Waits until the given condition is satisfied.
	 * 
	 * @param predicate while-condition
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 */
	public void awaitWhile(Supplier<Boolean> predicate) throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "While-condition is null");
		Preconditions.checkState(m_cond != null, "Condition is null");
		
		m_lock.lock();
		try {
			while ( predicate.get() ) {
				m_cond.await();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Waits until the given condition is satisfied within the specified timeout.
	 * If the condition is not satisfied before the deadline, it returns false.
	 * 
	 * @param predicate while-condition
	 * @param timeout   timeout
	 * @param unit      time unit of the timeout
	 * @return {@code true} if the condition is satisfied before the deadline, or {@code false} otherwise.
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 */
	public boolean awaitWhile(Supplier<Boolean> predicate, long timeout, TimeUnit unit)
		throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "While-condition is null");
		Preconditions.checkState(m_cond != null, "Condition is null");
		Preconditions.checkArgument(timeout >= 0, "invalid timeout: " + timeout);
		
		Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
		m_lock.lock();
		try {
			while ( predicate.get() ) {
				if ( !m_cond.awaitUntil(due) ) {
					return false;
				}
			}
			
			return true;
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Waits until the given condition is satisfied and then runs the given work.
	 * 
	 * @param predicate while-condition.
	 * @param work      work to run when the condition is satisfied.
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 */
	public void awaitWhileAndRun(Supplier<Boolean> predicate, Runnable work)
		throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "While-condition is null");
		Preconditions.checkState(m_cond != null, "Condition is null");
		
		m_lock.lock();
		try {
			while ( predicate.get() ) {
				m_cond.await();
			}
			work.run();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Waits until the given condition is satisfied and then runs the supplier to get a value.
	 * 
	 * @param predicate while-condition.
	 * @param suppl     value supplier.
	 * @return the value returned by the supplier.
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 */
	public <T> T awaitWhileAndGet(Supplier<Boolean> predicate, Supplier<T> suppl)
		throws InterruptedException {
		Preconditions.checkArgument(predicate != null, "While-condition is null");
		Preconditions.checkState(m_cond != null, "Condition is null");
		Preconditions.checkArgument(suppl != null, "value supplier is null");
		
		m_lock.lock();
		try {
			while ( predicate.get() ) {
				m_cond.await();
			}
			return suppl.get();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Waits until the given condition is satisfied and then runs the supplier to get a value.
	 * The supplier may throw a checked exception. In this case, it returns a Try object.
	 * 
	 * @param predicate while-condition.
	 * @param suppl     value supplier.	
	 * @return the {@code Try} object returned by the supplier.
	 * @throws InterruptedException if the current thread is interrupted while waiting.
	 */
	public <T> Try<T> awaitWhileAndTryToGet(Supplier<Boolean> predicate, CheckedSupplier<T> suppl)
		throws InterruptedException {
		m_lock.lock();
		try {
			while ( predicate.get() ) {
				m_cond.await();
			}
			return Try.success(suppl.get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
		finally {
			m_lock.unlock();
		}
	}
}
