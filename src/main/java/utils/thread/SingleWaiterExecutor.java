package utils.thread;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Preconditions;
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
 * <b>제출 계약</b>: 아직 시작하지 않은 작업을 <b>한 번만</b> 제출한다. 제출한 뒤에는 실행기가 작업의 시작을 관리하므로
 * 호출자가 직접 {@code start()}하지 않는다. 실행 중이거나 대기 중인 작업을 다시 제출하면 거부한다.
 * <p>
 * <b>결과 추적</b>: 본 클래스는 {@link java.util.concurrent.Executor}를 구현하지 않으며 fire-and-forget 형태로 사용된다.
 * 제출한 작업의 결과(완료, 실패, 교체·중단에 따른 취소)는 그 작업의 {@link StartableExecution#whenFinished} 등의
 * 종료 콜백으로 추적한다. 실행기는 제출된 모든 작업이 결국 종료 상태가 되도록 한다. 작업의 {@code start()}가 작업을
 * 끝내지 않은 채 예외를 던지면 실행기가 그 작업을 {@code cancel(true)}하므로, 이 경우 결과는 실패가 아니라
 * <b>취소</b>로 나타난다(원인은 로그와, {@link #submit}에서 바로 시작한 경우 호출자에게 던진 예외로 알 수 있다).
 * <p>
 * <b>실행 스레드</b>: 전용 작업 스레드가 없다. 첫 작업의 {@code start()}는 {@link #submit}을 호출한 스레드에서, 다음 작업의
 * {@code start()}는 앞 작업의 종료 콜백 스레드에서 실행된다. 따라서 {@code start()}가 오래 걸리는 작업이면
 * {@link #submit}도 그만큼 늦게 반환한다.
 * <p>
 * 모든 상태 변경은 내부 {@link Guard} lock으로 직렬화된다. 작업의 {@code start()}와 {@code cancel(true)}는 그 lock 밖에서
 * 호출한다. 작업의 {@code start()}가 예외를 던져도 실행기가 멈추지 않는다(시작에 실패한 작업은 실행 슬롯에서 내린다).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SingleWaiterExecutor implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(SingleWaiterExecutor.class);

	private volatile Logger m_logger = s_logger;

	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private StartableExecution<?> m_pendingJob = null;
	@GuardedBy("m_guard") private StartableExecution<?> m_running = null;
	@GuardedBy("m_guard") private boolean m_shutdown = false;

	/**
	 * 새 {@code SingleWaiterExecutor}를 생성한다.
	 */
	public SingleWaiterExecutor() { }

	/**
	 * 작업을 제출한다.
	 * <p>
	 * 실행 중인 작업이 없으면 즉시 실행을 시작한다. 실행 중인 작업이 있으면 대기 슬롯에 보관한다.
	 * 이미 대기 슬롯에 다른 작업이 있는 경우 그 작업은 {@code cancel(true)}되어 폐기되고 새 작업이
	 * 슬롯을 차지한다 (latest-wins). 교체된 작업의 취소가 예외를 던지면 로그만 남긴다. 새 작업은 이미 접수되었으므로
	 * 호출자에게 실패로 알리지 않는다.
	 * <p>
	 * 제출한 작업의 결과는 그 작업의 종료 콜백으로 추적한다(클래스 설명의 "결과 추적" 참조). 본 메소드는 결과를 반환하지
	 * 않는다.
	 * <p>
	 * 작업의 {@code start()}와 교체된 작업의 {@code cancel(true)}는 내부 lock 밖에서 호출한다. 작업 쪽 lock과
	 * 순서가 뒤집혀 교착하지 않게 하기 위해서다. 즉시 시작한 작업의 {@code start()}가 예외를 던지면 그 작업을 실행
	 * 슬롯에서 내리고(그 사이 들어온 대기 작업이 있으면 그것을 시작한다) 예외를 호출자에게 그대로 전파한다.
	 * 즉시 시작하는 경우 본 메소드는 작업의 {@code start()}가 끝날 때까지 반환하지 않는다.
	 *
	 * @param exec	제출할 실행 (non-null, 아직 제출하지 않은 것)
	 * @throws IllegalArgumentException	{@code exec}가 {@code null}이거나, 이미 실행 중이거나 대기 중인 작업인 경우
	 * @throws IllegalStateException	{@link #shutdownNow()}가 이미 호출된 경우
	 */
	public void submit(StartableExecution<?> exec) {
		Preconditions.checkNotNullArgument(exec, "StartableExecution is null");

		// lock 안에서는 슬롯만 정하고, start()·cancel()은 lock 밖에서 부른다.
		StartableExecution<?>[] replaced = new StartableExecution<?>[1];
		boolean startNow = m_guard.get(() -> {
			Preconditions.checkState(!m_shutdown, "executor has been shut down");
			// 같은 작업을 다시 제출하면 교체 과정에서 그 작업 자체(또는 실행 중인 작업)를 취소하게 된다.
			Preconditions.checkArgument(exec != m_running && exec != m_pendingJob,
										"the job is already submitted: %s", exec);

			if ( m_running == null ) {
				// 실행 중인 작업이 없으면, 현재 작업을 실행한다.
				m_running = exec;
				return true;
			}
			else {
				// 대기 중인 작업이 있으면, 새 작업으로 교체한다.
				replaced[0] = m_pendingJob;
				m_pendingJob = exec;
				return false;
			}
		});

		if ( replaced[0] != null ) {
			getLogger().debug("pending job is replaced and cancelled: {}", replaced[0]);
			cancelQuietly(replaced[0]);
		}
		if ( startNow ) {
			launch(exec, true);
		}
	}

	/**
	 * 본 실행기를 중단한다. 대기 슬롯에 보관된 작업과 실행 중인 작업 모두에 {@code cancel(true)}을 호출한다(내부 lock
	 * 밖에서). (2026-10-31 이전 이름은 {@code stop()}이었다. 재시작할 수 없는 즉시 중단이라
	 * {@link java.util.concurrent.ExecutorService#shutdownNow()}와 이름을 맞췄다.)
	 * <p>
	 * 본 메소드 호출 후 {@link #submit}은 {@link IllegalStateException}을 던진다 — 재시작은 지원하지 않는다.
	 * cancel은 요청만 하므로 실제 취소 완료 여부를 보장하지 않는다. 동기 대기가 필요하면 호출자가
	 * 별도의 메커니즘으로 대기해야 한다.
	 * <p>
	 * 각 작업의 취소는 따로 시도한다. 한 작업의 {@code cancel(true)}가 예외를 던져도 다른 작업의 취소를 시도한 뒤, 첫
	 * 예외에 나머지를 {@link Throwable#addSuppressed(Throwable) suppressed}로 붙여 던진다. 실행기는 이미 중단된 상태다.
	 * (2026-10-31 이전에는 대기 작업의 취소가 던지면 실행 중인 작업을 취소하지 못했고, 슬롯이 이미 비어 다시 불러도
	 * 회복되지 않았다.) 이미 중단된 실행기에 다시 호출하면 아무 일도 하지 않는다.
	 *
	 * @throws RuntimeException	작업의 {@code cancel(true)}가 던진 예외(모든 작업의 취소를 시도한 뒤)
	 */
	public void shutdownNow() {
		StartableExecution<?>[] jobs = m_guard.get(() -> {
			StartableExecution<?>[] taken = { m_pendingJob, m_running };
			m_pendingJob = null;
			m_running = null;
			m_shutdown = true;
			return taken;
		});

		RuntimeException failure = null;
		for ( StartableExecution<?> job: jobs ) {
			if ( job != null ) {
				try {
					job.cancel(true);
				}
				catch ( RuntimeException e ) {
					if ( failure == null ) {
						failure = e;
					}
					else {
						failure.addSuppressed(e);
					}
				}
			}
		}
		if ( failure != null ) {
			throw failure;
		}
	}

	/**
	 * {@link #shutdownNow()}가 호출되었는지 여부를 반환한다.
	 *
	 * @return	중단되었으면 {@code true}. 이후의 {@link #submit}은 거부된다.
	 */
	public boolean isShutdown() {
		return m_guard.read(() -> m_shutdown);
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
	 * 실행 슬롯에 올린 작업을 시작한다. 내부 lock 밖에서 호출한다.
	 * <p>
	 * 종료 콜백을 먼저 등록하므로, {@code start()} 안에서 바로 끝나도 {@link #onDone(StartableExecution)}이 불린다.
	 * {@code start()}가 예외를 던지면 다음을 한다.
	 * <ol>
	 *   <li>작업이 아직 끝나지 않았으면 {@code cancel(true)}로 종료 상태로 만든다. 그러지 않으면 작업의 종료 콜백이 오지
	 *       않아 호출자가 결과를 추적할 수 없다(검토 #10의 외부 검토).</li>
	 *   <li>그 작업을 실행 슬롯에서 내리고 다음 대기 작업으로 넘어간다. 작업이 끝나지 않으면 종료 콜백으로는 슬롯이
	 *       비지 않기 때문이다.</li>
	 * </ol>
	 *
	 * @param exec		시작할 작업
	 * @param rethrow	{@code start()}의 예외를 다시 던질지 여부. {@link #submit}은 호출자에게 알리고, 종료 콜백에서 이어
	 * 					시작한 경우는 받을 곳이 없으므로 로그만 남긴다.
	 */
	private void launch(StartableExecution<?> exec, boolean rethrow) {
		try {
			exec.whenFinishedAsync(result -> onDone(exec));
			exec.start();
		}
		catch ( RuntimeException | Error e ) {
			getLogger().error("failed to start a job; cancelling it: {}", exec, e);
			if ( !exec.isDone() ) {
				cancelQuietly(exec);
			}
			onDone(exec);
			if ( rethrow ) {
				throw e;
			}
		}
	}

	/**
	 * 작업을 취소한다. 취소가 예외를 던지면 로그만 남긴다.
	 */
	private void cancelQuietly(StartableExecution<?> exec) {
		try {
			exec.cancel(true);
		}
		catch ( RuntimeException e ) {
			getLogger().warn("failed to cancel a job: {}", exec, e);
		}
	}

	/**
	 * 실행이 끝난(또는 시작에 실패한) 작업을 실행 슬롯에서 내리고, 대기 슬롯에 작업이 있으면 다음 작업을 시작한다.
	 * <p>
	 * {@link StartableExecution#whenFinishedAsync} 콜백과 시작 실패 처리에서 호출된다. 호출된 시점에
	 * {@code m_running}이 더 이상 {@code exec}이 아닌 경우 (예: {@link #shutdownNow()}가 먼저 진행되어
	 * 실행 슬롯을 비웠거나, 이미 정리된 경우) 아무 동작도 하지 않는다. 다음 작업의 시작은 lock 밖에서 하며, 그 시작이
	 * 실패하면 로그를 남기고 다시 다음 작업으로 넘어간다(2026-10-31 이전에는 그 예외가 비동기 리스너 안에서 사라지고
	 * 실행 슬롯이 남았다).
	 *
	 * @param exec	종료된 실행
	 */
	private void onDone(StartableExecution<?> exec) {
		StartableExecution<?> next = m_guard.get(() -> {
			if ( m_running != exec ) {
				return null;
			}

			StartableExecution<?> pending = m_pendingJob;
			m_pendingJob = null;
			m_running = pending;
			return pending;
		});
		if ( next != null ) {
			launch(next, false);
		}
	}
}
