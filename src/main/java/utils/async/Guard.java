package utils.async;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import utils.func.CheckedSupplierX;


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
	 * @return	대기 제한 시각이 경과하기 전에 signal을 받아 대기가 멈춘 경우는 {@code true},
	 * 			그렇지 않고 시간 제한으로 대기가 멈춘 경우는 {@code false}.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public boolean awaitUntilInGuard(Date due) throws InterruptedException {
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
	 * 주어진 작업을 수행한다.
	 * <p>
	 * 본 메소드는 lock을 획득한 상태에서 주어진 작업을 수행한다.
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
	 * 주어진 작업을 수행한다. 작업이 완료되면 모든 대기 중인 쓰레드에게 signal을 보낸다.
	 * <p>
	 * 본 메소드는 lock을 획득한 상태에서 주어진 작업을 수행한다.
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
}
