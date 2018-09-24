package utils;

import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import utils.Unchecked.CheckedSupplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Guard {
	private final Lock m_lock;
	private final Condition m_cond;
	
	public static Guard by(Lock lock) {
		return new Guard(lock);
	}
	
	public static Guard by(Lock lock, Condition cond) {
		return new Guard(lock, cond);
	}
	
	private Guard(Lock lock) {
		Objects.requireNonNull(lock, "lock is null");
		
		m_lock = lock;
		m_cond = null;
	}
	
	private Guard(Lock lock, Condition cond) {
		Objects.requireNonNull(lock, "lock is null");
		Objects.requireNonNull(cond, "Condition is null");
		
		m_lock = lock;
		m_cond = cond;
	}
	
	public void run(Runnable work) {
		m_lock.lock();
		try {
			work.run();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public void runAndSignal(Runnable work) {
		Preconditions.checkState(m_cond != null, "Condition is null");
		
		m_lock.lock();
		try {
			work.run();
			m_cond.signalAll();
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
	
	public <T> T get(Supplier<T> suppl) {
		m_lock.lock();
		try {
			return suppl.get();
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
	
	public void awaitUntil(Supplier<Boolean> predicate) throws InterruptedException {
		Objects.requireNonNull(predicate, "Until-condition is null");
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
		Objects.requireNonNull(predicate, "While-condition is null");
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