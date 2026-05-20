package utils.thread;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Preconditions;
import utils.thread.Guard;
import utils.async.StartableExecution;



/**
 * 동시에 하나의 작업만 실행하고 대기 슬롯을 길이 1로 유지하는 latest-wins 실행기.
 * <p>
 * 한 번에 최대 하나의 {@link StartableExecution}만 실행되고, 실행 중일 때 들어온
 * {@code submit} 호출은 "대기 슬롯"에 한 개만 보관된다. 실행 중인 작업이 있을 때 새 작업이
 * 들어오면 기존 대기 슬롯의 작업은 {@link StartableExecution#cancel(boolean) cancel(true)}되어
 * 폐기되고 새 작업이 자리잡는다 (latest-wins). 실행 중인 작업이 끝나면 대기 슬롯의 작업이
 * 자동으로 다음 실행 대상이 된다.
 * <p>
 * 본 클래스는 {@link java.util.concurrent.Executor}를 구현하지 않으며 fire-and-forget 형태로 사용된다.
 * submit한 작업이 실제로 실행됐는지 / 교체되어 취소됐는지를 호출자가 추적하려면 직접
 * {@link StartableExecution}의 finish 콜백을 등록해야 한다.
 * <p>
 * 모든 상태 변경은 내부 {@link Guard} lock으로 직렬화된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SingleWaiterExecutor implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(SingleWaiterExecutor.class);
	
	private volatile Logger m_logger = s_logger;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private StartableExecution<?> m_pendingJob = null;
	@GuardedBy("m_guard") private StartableExecution<?> m_running = null;
	@GuardedBy("m_guard") private boolean m_stopped = false;
	
	/**
	 * 새 {@code SingleWaiterExecutor}를 생성한다.
	 */
	public SingleWaiterExecutor() { }

	/**
	 * 작업을 제출한다.
	 * <p>
	 * 실행 중인 작업이 없으면 즉시 실행을 시작한다. 실행 중인 작업이 있으면 대기 슬롯에 보관한다.
	 * 이미 대기 슬롯에 다른 작업이 있는 경우 그 작업은 {@code cancel(true)}되어 폐기되고 새 작업이
	 * 슬롯을 차지한다 (latest-wins).
	 * <p>
	 * 제출한 작업이 실제 실행됐는지 또는 후속 submit으로 교체되어 취소됐는지를 추적하려면 호출자가
	 * 직접 {@link StartableExecution#whenFinished} 등의 콜백을 등록해야 한다.
	 * 본 메소드는 그러한 결과를 반환하지 않는다.
	 *
	 * @param exec	제출할 실행 (non-null)
	 * @throws IllegalArgumentException	{@code exec}가 {@code null}인 경우
	 * @throws IllegalStateException	{@link #stop()}이 이미 호출된 경우
	 */
	public void submit(StartableExecution<?> exec) {
		Preconditions.checkNotNullArgument(exec, "StartableExecution is null");

		m_guard.run(() -> {
			Preconditions.checkState(!m_stopped, "stopped");

			if ( m_running == null ) {
				// 실행 중인 작업이 없으면, 현재 작업을 실행한다.
				startInGuard(exec);
			}
			else {
				if ( m_pendingJob != null ) {
					// 대기 중인 작업이 있으면, 새 작업으로 교체한다.
					m_pendingJob.cancel(true);

					getLogger().debug("pending job is replaced and cancelled: {}", m_pendingJob);
				}

				m_pendingJob = exec;
			}
		});
	}

	/**
	 * 본 실행기를 중단한다.
	 * <p>
	 * 대기 슬롯에 보관된 작업과 실행 중인 작업 모두에 {@code cancel(true)}을 호출한다.
	 * 본 메소드 호출 후 {@link #submit}은 {@link IllegalStateException}을 던진다 — 재시작은 지원하지 않는다.
	 * cancel은 요청만 하므로 실제 취소 완료 여부를 보장하지 않는다. 동기 대기가 필요하면 호출자가
	 * 별도의 메커니즘으로 대기해야 한다.
	 */
	public void stop() {
		m_guard.run(() -> {
			if ( m_pendingJob != null ) {
				m_pendingJob.cancel(true);
				m_pendingJob = null;
			}

			StartableExecution<?> running = m_running;
			m_running = null;
			if ( running != null ) {
				running.cancel(true);
			}

			m_stopped = true;
		});
	}

	/**
	 * 현재 사용 중인 logger를 반환한다.
	 *
	 * @return	{@link #setLogger(Logger)}로 설정된 logger 또는 클래스 기본 logger
	 */
	@Override
	public Logger getLogger() {
		return m_logger;
	}

	/**
	 * 로그 출력에 사용할 logger를 설정한다.
	 *
	 * @param logger	사용할 logger. {@code null}을 전달하면 클래스 기본 logger로 복원된다.
	 */
	@Override
	public void setLogger(Logger logger) {
		m_logger = logger != null ? logger : s_logger;
	}
	
	/**
	 * 주어진 실행을 현재 실행 작업으로 등록하고 시작한다.
	 * <p>
	 * {@code m_running}을 {@link StartableExecution#start() start()} 이전에 갱신함으로써
	 * {@code start()}가 동기 완료되어 {@link #onDone(StartableExecution)}이 reentrant 호출되더라도
	 * {@code m_running == exec} 조건을 만족시켜 정상 경로로 진입하도록 한다.
	 * <p>
	 * 본 메소드는 반드시 {@code m_guard} lock을 획득한 상태에서 호출되어야 한다.
	 *
	 * @param exec	실행할 작업 (non-null)
	 */
	@GuardedBy("m_guard")
	private void startInGuard(StartableExecution<?> exec) {
		m_running = exec;
		exec.whenFinishedAsync(result -> onDone(exec));
		exec.start();
	}

	/**
	 * 실행이 끝난 작업을 정리하고 대기 슬롯에 작업이 있으면 다음 작업을 시작한다.
	 * <p>
	 * {@link StartableExecution#whenFinishedAsync} 콜백으로 등록되어 호출된다. 호출된 시점에
	 * {@code m_running}이 더 이상 {@code exec}이 아닌 경우 (예: {@link #stop()}이 먼저 진행되어
	 * 실행 슬롯을 비웠거나, 다른 경로로 교체된 경우) 아무 동작도 하지 않는다.
	 *
	 * @param exec	종료된 실행
	 */
	private void onDone(StartableExecution<?> exec) {
		m_guard.run(() -> {
			if ( m_running != exec ) {
				return;
			}

			m_running = null;

			if ( m_pendingJob != null ) {
				startInGuard(m_pendingJob);
				m_pendingJob = null;
			}
		});
	}
}
