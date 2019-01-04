package utils.async;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.jcip.annotations.GuardedBy;
import utils.LoggerSettable;



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
class SingleBufferExecutor implements LoggerSettable {
	static final Logger s_logger = LoggerFactory.getLogger(SingleBufferExecutor.class);
	
	private volatile Logger m_logger = s_logger;
	
	private final Lock m_lock = new ReentrantLock();
	@GuardedBy("m_lock") private AsyncExecution<?> m_pendingJob = null;
	@GuardedBy("m_lock") private AsyncExecution<?> m_running = null;
	@GuardedBy("m_lock") private boolean m_stopped = false;
	
	SingleBufferExecutor() {
		setLogger(s_logger);
	}

	public void submit(AsyncExecution<?> exec) {
		Preconditions.checkState(!m_stopped, "stopped");
		Objects.requireNonNull(exec, "AsyncExecution is null");
		
		m_lock.lock();
		try {
			if ( m_running == null ) {
				startInGuard(exec);
			}
			else {
				if ( m_pendingJob != null ) {
					// 대기 중인 작업이 있으면, 대기 작업을 현 작업을 바꾼한다.
					m_pendingJob.cancel(true);

					getLogger().debug("pending job is ignored: {}", m_pendingJob);
				}
				
				m_pendingJob = exec;
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public void stop() {
		m_lock.lock();
		try {
			if ( m_running != null ) {
				m_running.cancel(true);
				m_running = null;
			}
			
			m_stopped = true;
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
	
	private void onDone(Result<?> result) {
		m_lock.lock();
		try {
			m_running = null;
			
			if ( m_pendingJob != null ) {
				startInGuard(m_pendingJob);
				m_pendingJob = null;
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	private void startInGuard(AsyncExecution<?> exec) {
		exec.whenDone(this::onDone);
		exec.start();
		m_running = exec;
	}
}
