package utils.async.op;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.async.AbstractAsyncExecution;
import utils.async.AsyncState;
import utils.async.CancellableWork;
import utils.thread.Guard;
import utils.async.StartableExecution;
import utils.func.Result;
import utils.func.Try;


/**
 * 주어진 복수의 {@link StartableExecution}을 동시에 수행시키는 합성 비동기 수행 클래스.
 * <p>
 * 본 연산이 시작되면 모든 원소가 동시에 시작된다. 기본적으로 모든 원소가 성공 완료되어야
 * 본 연산도 완료되며, {@link #setElementCompletionCountToComplete(int)}로 부분 완료 기준을
 * 지정할 수 있다 (예: N개만 성공해도 완료로 간주). 본 연산은 항상 결과 값으로 {@code null}을
 * 반환한다.
 * <ul>
 *   <li>원소의 실패/취소는 본 연산을 실패/취소시키지 않으나, 성공 완료 카운트에는 포함되지 않는다.</li>
 *   <li>완료 임계값에 도달하면 아직 수행 중인 원소들은 강제 취소된다.</li>
 *   <li>본 연산이 외부에서 취소되면 모든 원소가 취소 요청되고 본 연산은 CANCELLED로 전이된다.</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ConcurrentAsyncExecution extends AbstractAsyncExecution<Void>
												implements CancellableWork {
	private static final Logger s_logger = LoggerFactory.getLogger(ConcurrentAsyncExecution.class);
	
	private final StartableExecution<?>[] m_elements;
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private int m_noOfElmCompletionToCompletion;
	@GuardedBy("m_guard") private int m_noOfFinishes = 0;
	@GuardedBy("m_guard") private int m_noOfCompletions = 0;

	/**
	 * 동시 수행 비동기 수행 객체를 생성한다.
	 * <p>
	 * 기본적으로 모든 원소가 성공 완료되어야 본 연산이 완료된다. 부분 완료 기준은
	 * {@link #setElementCompletionCountToComplete(int)}로 별도 지정한다.
	 *
	 * @param elements	동시 수행될 비동기 수행 객체 배열.
	 * @throws IllegalArgumentException	<code>elements</code>가 <code>null</code>인 경우.
	 */
	public ConcurrentAsyncExecution(StartableExecution<?>... elements) {
		Preconditions.checkNotNullArgument(elements, "elements is null");

		m_elements = elements;
		m_noOfElmCompletionToCompletion = elements.length;

		setLogger(s_logger);
	}

	/**
	 * 본 합성 실행이 완료된 것으로 간주되기 위한 "성공 완료" 원소 수를 변경한다.
	 * <p>
	 * 기본값은 전체 원소 수이며, 즉 모든 원소가 성공적으로 완료되어야 합성 실행이 완료된다.
	 * 이 값을 N으로 설정하면 N개의 원소가 성공 완료되는 순간 합성 실행도 완료로 간주되고
	 * 나머지 원소들은 강제 취소된다. {@code count}를 전체 원소 수와 같게 지정하면 기본값과
	 * 동일하게 동작한다.
	 * <p>
	 * 본 메소드는 {@link #start()} 호출 이전(NOT_STARTED 상태)에만 호출할 수 있다.
	 *
	 * @param count 완료로 간주하기 위한 성공 원소 수. {@code 0 < count <= 전체 원소 수} 범위여야 한다.
	 * @throws IllegalArgumentException {@code count}가 범위 밖인 경우.
	 * @throws IllegalStateException 본 실행이 이미 시작되었거나 종료된 경우.
	 */
	public void setElementCompletionCountToComplete(int count) {
		Preconditions.checkArgument(count > 0 && count <= m_elements.length,
									"count > 0 && count <= %d", m_elements.length);
		Preconditions.checkState(getState() == AsyncState.NOT_STARTED,
									"already started: state=%s", getState());

		m_guard.run(() -> m_noOfElmCompletionToCompletion = count);
	}

	@Override
	public String toString() {
		return String.format("Concurrent[size=%d, finishes=%d, completions=%d]",
								m_elements.length, m_noOfFinishes, m_noOfCompletions);
	}

	/**
	 * 모든 원소 비동기 수행을 동시에 시작한다.
	 * <p>
	 * 이미 시작/종료 상태이면 아무 작업도 하지 않는다. 각 원소에 대해 종료 콜백을
	 * 등록한 뒤 시작시킨다. 모든 원소를 시작한 뒤 본 실행을 RUNNING으로 전이한다.
	 * 원소의 {@code start()}에서 예외가 발생하면 이미 시작된 원소들을 모두 취소하고
	 * 본 실행을 FAILED로 전이한다.
	 */
	@Override
	public void start() {
		if ( !notifyStarting() ) {
			return;
		}

		for ( int i =0; i < m_elements.length; ++i ) {
			m_elements[i].whenFinishedAsync(this::onElementFinished);
			try {
				m_elements[i].start();
			}
			catch ( Throwable e ) {
				// 이미 시작된 원소들을 모두 취소시키고 본 실행을 FAILED로 전이한다.
				for ( int j = 0; j < i; ++j ) {
					try { m_elements[j].cancel(true); } catch ( Exception ignored ) { }
				}
				notifyStarted();
				notifyFailed(e);
				return;
			}
		}

		notifyStarted();
	}

	/**
	 * 모든 원소 비동기 수행에 강제 취소를 요청한다.
	 * <p>
	 * {@link CancellableWork} 구현으로서, 본 실행에 대한 {@link #cancel(boolean)} 호출 시
	 * 내부적으로 호출된다. 개별 원소의 취소 중 예외가 발생하면 무시되며 나머지 원소들의
	 * 취소는 계속 진행된다.
	 *
	 * @return 항상 {@code true}.
	 */
	@Override
	public boolean cancelWork() {
		for ( int i =0; i < m_elements.length; ++i ) {
			try {
				m_elements[i].cancel(true);
			}
			catch ( Exception ignored ) { }
		}

		return true;
	}

	/**
	 * 원소 비동기 수행이 종료될 때 호출되는 콜백.
	 * <p>
	 * 본 실행이 RUNNING으로 전이된 후에 카운터를 갱신한다 (시작 중 원소가 즉시 종료되는
	 * 경우에 대비). 갱신된 카운터를 바탕으로 종료 조건을 판정한다:
	 * <ul>
	 *   <li>본 실행에 취소 요청이 있었다면 CANCELLED로 전이한다.</li>
	 *   <li>모든 원소가 종료되었거나, 성공 완료 원소 수가 임계값에 도달하면 COMPLETED로 전이한다.</li>
	 * </ul>
	 * 종료 전이가 일어났다면 아직 수행 중인 다른 원소들을 모두 강제 취소한다.
	 *
	 * @param result 원소의 종료 결과 (성공/실패/취소).
	 */
	private void onElementFinished(Result<?> result) {
		// start 과정 중에, 일부 element exec가 종료될 수도 있기 때문에
		// 모든 element의 start가 모두 끝날 때까지 대기한다.
		Try.run(() -> waitForStarted());
		
		m_guard.run(() -> {
			++m_noOfFinishes;
			result.ifSuccessful(r -> ++m_noOfCompletions);

			if ( isDone() ) {
				return;
			}

			if ( isCancelRequested() ) {
				notifyCancelled();
			}
			else if ( m_noOfFinishes >= m_elements.length
					|| m_noOfCompletions >= m_noOfElmCompletionToCompletion ) {
				notifyCompleted(null);
			}
		});
		
		if ( isDone() ) {
			// 아직 수행이 종료되지 않은 멤버 aop들의 수행을 중단시킨다.
			for ( int i =0; i < m_elements.length; ++i ) {
				try {
					m_elements[i].cancel(true);
				}
				catch ( Exception ignored ) { }
			}
		}
	}
}