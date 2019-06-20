package utils;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import utils.func.CheckedConsumer;
import utils.func.CheckedFunction;
import utils.func.CheckedRunnable;
import utils.func.CheckedSupplier;
import utils.func.Try;
/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Guard {
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
		Utilities.checkNotNullArgument(lock, "lock is null");
		
		m_lock = lock;
		m_cond = null;
	}
	
	private Guard(Lock lock, Condition cond) {
		Utilities.checkNotNullArgument(lock, "lock is null");
		Utilities.checkNotNullArgument(cond, "Condition is null");
		
		m_lock = lock;
		m_cond = cond;
	}
	
	public Lock getLock() {
		return m_lock;
	}
	
	public void lock() {
		m_lock.lock();
	}
	
	public void unlock() {
		m_lock.unlock();
	}
	
	public void signalAll() {
		m_cond.signalAll();
	}

	public boolean awaitUntil(Date due) throws InterruptedException {
		return m_cond.awaitUntil(due);
	}
	
	public Condition getCondition() {
		return m_cond;
	}
	
	public void run(Runnable work) {
		run(work, false);
	}
	
	public void run(Runnable work, boolean signal) {
		m_lock.lock();
		try {
			work.run();
			if ( signal ) {
				m_cond.signalAll();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public Try<Void> tryToRun(CheckedRunnable work) {
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
		private boolean m_signal = false;
		
		GuardedRunnable(Runnable task) {
			Utilities.checkNotNullArgument(task, "task is null");
			
			m_task = task;
		}
		
		public GuardedRunnable signalAll() {
			m_signal = true;
			return this;
		}

		@Override
		public void run() {
			m_lock.lock();
			try {
				m_task.run();
				if ( m_signal ) {
					m_cond.signalAll();
				}
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
	
	public <T> Try<T> tryToGet(CheckedSupplier<T> suppl) {
		m_lock.lock();
		try {
			return Try.success(suppl.get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> GuardedSupplier<T> lift(Supplier<T> suppl) {
		return new GuardedSupplier<>(suppl);
	}
	
	public class GuardedSupplier<T> implements Supplier<T> {
		private final Supplier<T> m_suppl;
		private boolean m_signal = false;
		
		GuardedSupplier(Supplier<T> suppl) {
			Utilities.checkNotNullArgument(suppl, "Supplier is null");
			
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
			Utilities.checkNotNullArgument(consumer, "Consumer is null");
			
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
			Utilities.checkNotNullArgument(func, "Function is null");
			
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
	
	public void awaitUntil(Supplier<Boolean> predicate) throws InterruptedException {
		Utilities.checkNotNullArgument(predicate, "Until-condition is null");
		Utilities.checkState(m_cond != null, "Condition is null");
		
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
	
	public boolean awaitUntil(Supplier<Boolean> predicate, long timeout, TimeUnit tu)
		throws InterruptedException {
		Utilities.checkNotNullArgument(predicate, "Until-condition is null");
		Utilities.checkNotNullArgument(tu, "TimeUnit is null");
		Utilities.checkArgument(timeout >= 0, "timeout should be larger than zero");
		Utilities.checkState(m_cond != null, "Condition is null");
		
		Date due = new Date(System.currentTimeMillis() + tu.toMillis(timeout));
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
	
	public void awaitUntilAndRun(Supplier<Boolean> predicate, Runnable work)
		throws InterruptedException {
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				m_cond.await();
			}
			work.run();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public void awaitUntilAndRun(Supplier<Boolean> predicate, Runnable work, boolean signal)
		throws InterruptedException {
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
	
	public <T> T awaitUntilAndGet(Supplier<Boolean> predicate, Supplier<T> suppl)
		throws InterruptedException {
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				m_cond.await();
			}
			return suppl.get();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> T awaitUntilAndGet(Supplier<Boolean> predicate, Supplier<T> suppl,
									long timeout, TimeUnit tu)
		throws InterruptedException, TimeoutException {
		Utilities.checkNotNullArgument(predicate, "Until-condition is null");
		Utilities.checkNotNullArgument(tu, "TimeUnit is null");
		Utilities.checkArgument(timeout >= 0, "timeout should be larger than zero");
		Utilities.checkState(suppl != null, "Supplier is null");
		
		Date due = new Date(System.currentTimeMillis() + tu.toMillis(timeout));
		m_lock.lock();
		try {
			while ( !predicate.get() ) {
				if ( !m_cond.awaitUntil(due) ) {
					throw new TimeoutException();
				}
			}

			return suppl.get();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public <T> Try<T> awaitUntilAndTryToGet(Supplier<Boolean> predicate, CheckedSupplier<T> suppl)
		throws InterruptedException {
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
	
	public void awaitWhile(Supplier<Boolean> predicate) throws InterruptedException {
		Utilities.checkNotNullArgument(predicate, "While-condition is null");
		Utilities.checkState(m_cond != null, "Condition is null");
		
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
	
	public boolean awaitWhile(Supplier<Boolean> predicate, long timeout, TimeUnit unit)
		throws InterruptedException {
		Utilities.checkNotNullArgument(predicate, "While-condition is null");
		Utilities.checkState(m_cond != null, "Condition is null");
		Utilities.checkArgument(timeout >= 0, "invalid timeout: " + timeout);
		
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
	
	public void awaitWhileAndRun(Supplier<Boolean> predicate, Runnable work)
		throws InterruptedException {
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
	
	public <T> T awaitWhileAndGet(Supplier<Boolean> predicate, Supplier<T> suppl)
		throws InterruptedException {
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
