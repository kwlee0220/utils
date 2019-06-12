package utils;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.vavr.control.Try;
import utils.exception.CheckedSupplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Guards {
	private Guards() {
		throw new AssertionError("Should not be called: " + Guards.class);
	}
	
	public static class Holder<T> {
		private T m_value;
		
		public Holder() {
			this(null);
		}
		
		public Holder(T value) {
			m_value = value;
		}
	
		public T get() { return m_value; }
		public void set(T value) { m_value = value; }
	}
	
	public static <T> Runnable toRunnable(final Consumer<T> consumer, final T data) {
		return new Runnable() {
			@Override
			public void run() {
				consumer.accept(data);
			}
		};
	}
	
	public static void run(Lock lock, Runnable work) {
		lock.lock();
		try {
			work.run();
		}
		finally {
			lock.unlock();
		}
	}
	
	public static void run(Lock lock, Condition cond, Runnable work) {
		lock.lock();
		try {
			work.run();
			cond.signalAll();
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> T get(Lock lock, Supplier<T> suppl) {
		lock.lock();
		try {
			return suppl.get();
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> Try<T> tryToGet(Lock lock, CheckedSupplier<T> suppl) {
		lock.lock();
		try {
			return Try.success(suppl.get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> void accept(Lock lock, T data, Consumer<T> consumer) {
		lock.lock();
		try {
			consumer.accept(data);
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> void awaitUntil(Lock lock, Condition cond, Supplier<Boolean> predicate)
		throws InterruptedException {
		lock.lock();
		try {
			while ( !predicate.get() ) {
				cond.await();
			}
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> void awaitUntilAndRun(Lock lock, Condition cond,
											Supplier<Boolean> predicate, Runnable work)
		throws InterruptedException {
		lock.lock();
		try {
			while ( !predicate.get() ) {
				cond.await();
			}
			work.run();
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> T awaitUntilAndGet(Lock lock, Condition cond,
											Supplier<Boolean> predicate, Supplier<T> suppl)
		throws InterruptedException {
		lock.lock();
		try {
			while ( !predicate.get() ) {
				cond.await();
			}
			return suppl.get();
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> void awaitWhile(Lock lock, Condition cond, Supplier<Boolean> predicate)
		throws InterruptedException {
		lock.lock();
		try {
			while ( predicate.get() ) {
				cond.await();
			}
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> void awaitWhileAndRun(Lock lock, Condition cond,
											Supplier<Boolean> predicate, Runnable work)
		throws InterruptedException {
		lock.lock();
		try {
			while ( predicate.get() ) {
				cond.await();
			}
			work.run();
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> T awaitWhileAndGet(Lock lock, Condition cond,
											Supplier<Boolean> predicate, Supplier<T> suppl)
		throws InterruptedException {
		lock.lock();
		try {
			while ( predicate.get() ) {
				cond.await();
			}
			return suppl.get();
		}
		finally {
			lock.unlock();
		}
	}
}
