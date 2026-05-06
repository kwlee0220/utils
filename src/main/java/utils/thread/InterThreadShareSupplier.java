package utils.thread;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Throwables;
import utils.Utilities;
import utils.func.CheckedSupplier;


/**
 * 동일한 값 생성 작업이 여러 쓰레드에서 중복 수행되지 않도록 조율하는 {@link CheckedSupplier}.
 *
 * <p>한 쓰레드가 {@link #get()} 을 호출해 내부 supplier 로부터 값을 생성하는 동안
 * 다른 쓰레드가 {@code get()} 을 호출하면, 새 생성을 시작하지 않고 진행 중인 생성이
 * 완료될 때까지 대기한 뒤 그 결과(또는 예외)를 공유받아 반환한다. 생성이 겹치지 않는
 * 순차적 호출에는 매번 새롭게 내부 supplier 를 호출한다 — 즉 본 클래스는 결과를 영구
 * 캐시(memoize)하지 않으며 "동시에 진행 중인 호출 간의 결과 공유(call coalescing)"
 * 만을 제공한다.
 *
 * <p>{@link #setWaitTimeout(Duration)} 로 공유 대기 제한 시간을 지정할 수 있으며,
 * 지정하지 않으면 진행 중인 생성이 끝날 때까지 무제한 대기한다.
 *
 * @param <T>  생성되는 값의 타입
 *
 * @author Kang-Woo Lee
 */
public class InterThreadShareSupplier<T> implements CheckedSupplier<T>, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(InterThreadShareSupplier.class);

	@NotNull private final CheckedSupplier<T> m_srcSupplier;
	private volatile Duration m_waitTimeout = null;	// default: infinite wait
	@NotNull private volatile Logger m_logger = s_logger;
	
	private final Lock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private T m_result;
	@GuardedBy("m_lock") private boolean m_isProducing;
	@GuardedBy("m_lock") private ExecutionException m_cause;
	private final ConditionVariable m_cvDone = new ConditionVariable(m_cond, ()->m_isProducing);
	
	public InterThreadShareSupplier(@NotNull CheckedSupplier<T> srcSupplier) {
		Utilities.checkNotNullArgument(srcSupplier, "null CheckedSupplier");
		m_srcSupplier = srcSupplier;
	}
	
	public void setWaitTimeout(Duration dur) {
		m_waitTimeout = dur;
	}

	/**
	 * 공유된 생성 결과를 반환한다.
	 *
	 * <p>호출 시점에 다른 쓰레드가 이미 내부 supplier 로부터 값을 생성 중이면,
	 * 그 생성이 끝날 때까지 대기한 뒤 동일한 결과(또는 예외)를 공유받아 반환한다.
	 * 진행 중인 생성이 없으면 현재 쓰레드가 직접 내부 supplier 를 호출하여 값을
	 * 생성하며, 이 동안 들어오는 다른 {@code get()} 호출은 위 규칙에 따라
	 * 같은 결과를 공유받는다.
	 *
	 * <p>{@link #setWaitTimeout(Duration)} 으로 대기 제한 시간이 설정되어 있고
	 * 그 시간 내에 진행 중인 생성이 끝나지 않으면 {@link TimeoutException} 이
	 * 발생한다. 설정되지 않은 경우는 무제한 대기한다.
	 *
	 * @return 내부 supplier 가 생성한 값
	 * @throws InterruptedException  대기 중 쓰레드가 인터럽트된 경우
	 * @throws TimeoutException      설정된 대기 시간 내에 공유 대상 생성이 완료되지 않은 경우
	 * @throws ExecutionException    내부 supplier 가 예외로 종료된 경우
	 *                               (원인 예외는 {@link ExecutionException#getCause()} 로 조회)
	 */
	@Override
	public T get() throws InterruptedException, TimeoutException, ExecutionException {
		m_lock.lock();
		try {
			// 다른 쓰레드에서 produce() 함수를 호출하여 데이터를 생성 중에 있는 경우는
			// 해당 작업이 종료될 때까지 대기한 후, 그 결과를 반환한다.
			if ( m_isProducing ) {
				if ( m_waitTimeout != null ) {
					if ( !m_cvDone.await(m_waitTimeout.toMillis(), TimeUnit.MILLISECONDS) ) {
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
