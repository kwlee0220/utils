package utils.async;

import javax.annotation.concurrent.GuardedBy;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractLoopThreadService extends AbstractExecutionThreadService {
	protected final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") protected boolean m_stopRequested = false;
	
	/**
	 * 중지	요청 여부를 반환한다.
	 *
	 * @return	중지 요청 여부.
	 */
	public boolean isStopRequested() {
		return m_guard.get(() -> m_stopRequested);
	}
	
	/**
	 * 중지를 요청한다.
	 */
	public void markStopRequested() {
		m_guard.get(() -> m_stopRequested = true);
	}
	
	/**
	 * 중지 요청이 있을 때까지 대기한다.
	 *
	 * @throws InterruptedException	쓰레드가 인터럽트된 경우.
	 */
	protected void awaitUntilStopRequested() throws InterruptedException {
		m_guard.awaitCondition(() -> m_stopRequested).andReturn();
	}
}
