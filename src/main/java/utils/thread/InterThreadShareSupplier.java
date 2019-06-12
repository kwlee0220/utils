package utils.thread;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jcip.annotations.GuardedBy;
import utils.LoggerSettable;
import utils.exception.CheckedSupplier;
import utils.exception.Throwables;



/**
 *
 * @author Kang-Woo Lee
 */
public class InterThreadShareSupplier<T> implements CheckedSupplier<T>, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(InterThreadShareSupplier.class);

	@Nonnull private final CheckedSupplier<T> m_srcSupplier;
	private volatile long m_maxWaitMillis = -1;	// default: infinite wait
	@Nonnull private volatile Logger m_logger = s_logger;
	
	private final Lock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private T m_result;
	@GuardedBy("m_lock") private boolean m_isProducing;
	@GuardedBy("m_lock") private ExecutionException m_cause;
	private final ConditionVariable m_cvDone = new ConditionVariable(m_cond, ()->m_isProducing);
	
	public InterThreadShareSupplier(CheckedSupplier<T> srcSupplier) {
		Objects.requireNonNull(srcSupplier);
		
		m_srcSupplier = srcSupplier;
	}
	
	public void setMaxWaitMillis(long millis) {
		m_maxWaitMillis = millis;
	}

	@Override
	public T get() throws InterruptedException, TimeoutException, ExecutionException {
		m_lock.lock();
		try {
			// 다른 쓰레드에서 produce() 함수를 호출하여 데이터를 생성 중에 있는 경우는
			// 해당 작업이 종료될 때까지 대기한 후, 그 결과를 반환한다.
			if ( m_isProducing ) {
				if ( m_maxWaitMillis >= 0 ) {
					if ( !m_cvDone.await(m_maxWaitMillis, TimeUnit.MILLISECONDS) ) {
						throw new TimeoutException();
					}
					else {
						m_cvDone.await();
					}
				}
				
				if ( m_result != null ) {
					m_logger.debug("use pre-produced result");
					return m_result;
				}
				
				throw m_cause;
			}
			else {
				// 영상이 capturing 중임을 알려 다른 쓰레드가 추가로 capture하지 못하도록 한다.
				m_isProducing = true;
			}
		}
		finally {
			m_lock.unlock();
		}
		
		return produce();
	}
	
	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = (logger != null) ? logger : s_logger;
	}
	
	private T produce() throws ExecutionException {
		T result = null;
		Throwable cause = null;
		try {
			result = m_srcSupplier.get();
		}
		catch ( Throwable e ) {
			cause = Throwables.unwrapThrowable(e);
		}
		
		m_lock.lock();
		try {
			m_result = result;
			m_cause = new ExecutionException(cause);
			m_isProducing = false;
			m_cond.signalAll();

			if ( m_result != null ) {
				return m_result;
			}
			
			throw m_cause;
		}
		finally {
			m_lock.unlock();
		}
	}
}
