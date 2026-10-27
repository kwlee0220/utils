package utils.async;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import utils.thread.Guard;

/**
 * 중지 요청이 있을 때까지 loop을 수행하는 Guava {@link com.google.common.util.concurrent.Service}의 추상 기반 클래스.
 * <p>
 * {@link AbstractExecutionThreadService}에 "중지 요청" 플래그와 그 플래그를 기다리는 수단을 더한다.
 * 하위 클래스는 {@link #run()}에서 {@link #isStopRequested()}가 {@code true}가 될 때까지 작업을 반복하고,
 * {@link #triggerShutdown()}에서 {@link #markStopRequested()}를 호출하여 {@link #stopAsync()}가 loop을
 * 깨우도록 한다. 대기가 필요한 경우에는 {@link #m_guard}의 조건 대기({@code awaitCondition(() -> m_stopRequested, ...)})
 * 또는 {@link #awaitUntilStopRequested()}를 사용한다.
 * <p>
 * <b>중지 요청과 서비스 상태:</b> {@link #markStopRequested()}는 플래그만 설정하며 Guava 서비스의 상태 전이를
 * 일으키지 않는다. 외부에서 직접 호출하면 {@link #stopAsync()}를 거치지 않고도 loop이 끝나며, 그 결과
 * {@link #run()}이 반환되어 서비스는 {@code TERMINATED}가 된다. 일반적인 중지는 {@link #stopAsync()}를 사용한다.
 * 한 번 설정된 중지 요청은 해제되지 않는다(Guava 서비스는 재시작할 수 없다).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractLoopService extends AbstractExecutionThreadService {
	protected final Guard m_guard = Guard.create();
	/**
	 * 중지 요청 여부. 변경은 {@link #m_guard}를 잡은 상태에서 하고(대기 중인 쓰레드를 깨우기 위함),
	 * 읽기는 lock 없이 할 수 있도록 {@code volatile}로 선언한다.
	 */
	protected volatile boolean m_stopRequested = false;

	/**
	 * 중지 요청 여부를 반환한다.
	 * <p>
	 * lock을 잡지 않고 읽으므로, 작업 중에 자주 호출해도 비용이 거의 없다.
	 *
	 * @return	중지 요청 여부.
	 */
	public boolean isStopRequested() {
		return m_stopRequested;
	}

	/**
	 * 중지를 요청한다.
	 * <p>
	 * 플래그를 설정하고 {@link #m_guard}에서 대기 중인 쓰레드를 깨운다. 서비스 상태는 바꾸지 않는다
	 * (클래스 Javadoc 참조).
	 */
	public void markStopRequested() {
		m_guard.run(() -> m_stopRequested = true);
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
