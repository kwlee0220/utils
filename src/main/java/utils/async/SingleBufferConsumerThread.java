package utils.async;

import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.jcip.annotations.GuardedBy;
import utils.LoggerSettable;
import utils.Throwables;



/**
 * * <code>SingleBufferConsumerThread</code>는 길이 1의 데이터 버퍼를 사용하는
 * 데이터 소비자 쓰레드를 지원하기 위한 추상 클래스이다.
 * 길이 1의 버퍼를 사용하기 때문에 복수 개의 데이타가 추가되는 경우 가장 마직막으로
 * 추가된 데이타만 유지된다.
 * <p>
 * <code>SingleBufferConsumerThread</code>를 상속하는 경우 다음과 같은 메소드를
 * 재정의한다.
 * <dl>
 * 	<dt>{@link #consume(Object)}
 * 	<dd>{@link #waitNextWork()} 호출을 통해 데이타가
 * 		데이타 풀에 삽입되는 경우 이를   데이타 소비 쓰레드 하에서 호출한다.
 * </dl>
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class SingleBufferConsumerThread<T,R> extends Thread implements LoggerSettable {
	static final Logger s_logger = LoggerFactory.getLogger(SingleBufferConsumerThread.class);

	private static final int STATE_NOT_STARTED = 0;
	private static final int STATE_RUNNING = 1;
	private static final int STATE_STOP_REQUESTED = 2;
	private static final int STATE_STOPPED = 3;
	
	private volatile Logger m_logger = s_logger;
	
	private final Lock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private SingleBufferWork<T, R> m_pendingJob = null;
	@GuardedBy("m_lock") private int m_state = STATE_NOT_STARTED;
	
	/**
	 * 생성된 데이타를 제공한다.
	 * 
	 * @param data	생성된 데이타.
	 * @throws Throwable	데이타 소비 중 예외가 발생된 경우.
	 */
	protected abstract R consume(T data) throws Throwable;
	
	protected SingleBufferConsumerThread() {
		setLogger(s_logger);
	}
	protected SingleBufferConsumerThread(String name) {
		super(name);
		setLogger(s_logger);
	}
	
	/**
	 * 소비자 쓰레드가 처리할 데이타를 추가한다.
	 * <p>
	 * 만일 아직 소비자 쓰레드에 의해 처리되지 않은 데이타는 제거된다.
	 * 만일 소비자 쓰레드가 처리할 데이타가 없어 수면 중인 경우는 데이터 추가를 통해
	 * 깨어나게 된다.
	 * 
	 * @param job	추가할 작업.
	 */
	public final SingleBufferWork<T,R> submit(T job) {
		Objects.requireNonNull(job, "job is null");
		
		m_lock.lock();
		try {
			if ( m_pendingJob != null ) {
				// 대기 중인 작업이 있으면, 대기 작업을 삭제한다.
				m_pendingJob.notifyIgnored();

				getLogger().debug("pending job is ignored: {}", m_pendingJob.getJob());
			}
			else {
				// 대기 중인 작업이 없으면, 작업 쓰레드를 sleep하고 있는 경우가 있으므로 깨운다.
				m_cond.signalAll();
			}
			
			return m_pendingJob = new SingleBufferWork<>(job);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	@Override
	public final void run() {
		SingleBufferWork<T,R> work =null;
		
		while ( true ) {
			try {
				work = waitNextWork();
				if ( work == null ) {
					getLogger().info("stopped {}={}", getClass().getName(), this);
					
					return;
				}
				
				// 해당 작업이 수행 작업으로 선정된 것을 알린다.
				work.notifySelected();
				getLogger().debug("job selected for execution: {}", work.getJob());
				
				// 작업을 수행한다.
				try {
					R result = consume(work.getJob());
					
					// 작업 종료 후에 작업이 종료되었음을 알린다.
					work.notifyCompleted(result);
					getLogger().debug("job is completed: {}, result={}", work.getJob(), result);
				}
				catch ( Throwable e ) {
					work.notifyFailed(e);
					getLogger().warn("fails to consume: data={}, cause={}",
										work, Throwables.unwrapThrowable(e));
				}
			}
			catch ( InterruptedException e ) {
				getLogger().warn("" + e);
				
				return;
			}
		}
	}
	
	/**
	 * 소비자 쓰레드를 시작시킨다.
	 */
	public final void startConsume() {
		m_lock.lock();
		try {
			Preconditions.checkState(m_state == STATE_NOT_STARTED, "already started");
			
			start();
			
			m_state = STATE_RUNNING;
			m_cond.signalAll();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * 소비자 쓰레드를 종료시킨다.
	 * <p>
	 * 실제 쓰레드가 종료되기 전에 반환될 수 있다.
	 */
	public final void stopConsume() {
		m_lock.lock();
		try {
			if ( m_state == STATE_NOT_STARTED ) {
				throw new IllegalStateException("not started yet");
			}
			if ( m_state == STATE_STOPPED ) {
				return;
			}
			else if ( m_state == STATE_RUNNING ) {
				m_state = STATE_STOP_REQUESTED;
				m_cond.signalAll();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * 소비자 쓰레드가 완전히 종료될 때까지 대기한다.
	 * <p>
	 * 쓰레드가 이미 종료된 경우는 호출이 무시되고, 쓰레드가 시작되지도 않은 경우는
	 * {@link IllegalStateException} 예외를 발생한다.
	 */
	public final void waitForStopped() throws InterruptedException {
		m_lock.lock();
		try {
			if ( m_state == STATE_NOT_STARTED ) {
				throw new IllegalStateException("consume has not been started");
			}
			if ( m_state == STATE_STOPPED ) {
				return;
			}
			
			while ( m_state != STATE_STOPPED ) {
				m_cond.await();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger != null ? logger : s_logger;
	}
	
	private SingleBufferWork<T,R> waitNextWork() throws InterruptedException {
		// 'stopConsume()' 이 호출된 경우는 null이 반환된다.
		//
		m_lock.lock();
		try {
			while ( m_pendingJob == null ) {
				// consuming thread의 종료 요청이 있는 경우.
				if ( m_state == STATE_STOP_REQUESTED ) {
					m_state = STATE_STOPPED;
					m_cond.signalAll();
					
					return null;
				}
				
				// 요청 작업이 없기 때문에 sleep한다.
				m_cond.await();
			}
			
			// 버퍼에 있는 데이타를 반환하기 전에 stop()이 호출되었는지 확인한다.
			if ( m_state == STATE_STOP_REQUESTED ) {
				m_state = STATE_STOPPED;
				m_cond.signalAll();
				
				return null;
			}
			
			SingleBufferWork<T,R> nextWork = m_pendingJob;
			m_pendingJob = null;
			
			return nextWork;
		}
		finally {
			m_lock.unlock();
		}
	}
}
