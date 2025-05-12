package utils.async;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import utils.LoggerSettable;
import utils.func.Result;



/**
 * <code>SingleBufferExecutor</code>는 길이 1의 버퍼를 사용하는 Executor를 지원하는 클래스이다.
 * <p>
 * 길이 1의 버퍼를 사용하기 때문에 복수 개의 {@link StartableExecution}가
 * 추가되는 경우 가장 마지막으로 추가된 Execution만 실행된다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class LastSingleWaiterExecutor implements LoggerSettable {
	static final Logger s_logger = LoggerFactory.getLogger(LastSingleWaiterExecutor.class);
	
	private volatile Logger m_logger = s_logger;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private StartableExecution<?> m_pendingJob = null;
	@GuardedBy("m_guard") private StartableExecution<?> m_running = null;
	@GuardedBy("m_guard") private boolean m_stopped = false;
	
	public LastSingleWaiterExecutor() {
		setLogger(s_logger);
	}

	public void submit(StartableExecution<?> exec) {
		Preconditions.checkArgument(exec != null, "StartableExecution is null");
		Preconditions.checkState(!m_stopped, "stopped");
		
		m_guard.run(() -> {
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
		});
	}
	
	public void stop() {
		m_guard.run(() -> {
			if ( m_running != null ) {
				m_running.cancel(true);
				m_running = null;
			}
			
			m_stopped = true;
		});
	}
	
	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger != null ? logger : s_logger;
	}
	
	private void startInGuard(StartableExecution<?> exec) {
		exec.whenFinishedAsync(this::onDone);
		exec.start();
		m_running = exec;
	}
	
	private void onDone(Result<?> result) {
		m_guard.run(() -> {
			m_running = null;
			
			if ( m_pendingJob != null ) {
				startInGuard(m_pendingJob);
				m_pendingJob = null;
			}
		});
	}
}
