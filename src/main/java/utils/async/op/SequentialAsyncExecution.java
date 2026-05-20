package utils.async.op;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.async.AbstractAsyncExecution;
import utils.async.CancellableWork;
import utils.async.StartableExecution;
import utils.func.Result;
import utils.stream.FStream;



/**
 * 복수개의 {@link StartableExecution}을 지정된 순서대로 수행시키는 합성 비동기 수행.
 * <p>
 * 등록된 순서대로 한 번에 하나씩 element를 시작·대기·종료 후 다음으로 진행한다.
 * 본 합성 실행의 결과 타입은 {@link Object}이며 결과 값은 마지막으로 성공한 element의 결과를
 * 따른다 — element들의 개별 결과는 본 합성 실행을 통해 노출되지 않는다.
 * 개별 결과가 필요하면 호출자가 element를 직접 참조해야 한다.
 * <p>
 * 라이프사이클:
 * <ul>
 *   <li>{@link #start()} 호출 → {@code notifyStarting()} → 가상의 "이전 element 완료" 이벤트를
 *       발생시켜 첫 element를 시작. 첫 element가 시작될 때 {@code notifyStarted()}로 RUNNING 진입.</li>
 *   <li>각 element의 종료 후({@link StartableExecution#whenFinishedAsync}) 다음 element가 큐에서
 *       꺼내져 시작된다.</li>
 *   <li>모든 element가 정상 완료하면 본 실행도 {@code COMPLETED}({@code result=마지막 element의 결과})로
 * 		전이한다.</li>
 *   <li>중간 element가 실패하면 본 실행은 즉시 {@code FAILED}로 전이하고 뒤따르는 element는
 *       시작되지 않는다.</li>
 *   <li>중간 element가 취소되면 본 실행은 즉시 {@code CANCELLED}로 전이한다.</li>
 * </ul>
 * <p>
 * 본 클래스는 {@link CancellableWork}를 구현한다. 외부 {@code cancel(true)} 호출 시 현재 실행 중인
 * element에 {@code cancel(true)}를 전파하며, element의 취소 결과가 도착하면 본 실행도 CANCELLED로
 * 종료된다.
 * <p>
 * 빈 시퀀스를 받으면 {@link #start()} 즉시 {@code COMPLETED}({@code result=null})로 종료된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SequentialAsyncExecution extends AbstractAsyncExecution<Object>
										implements CancellableWork {
	private static final Logger s_logger = LoggerFactory.getLogger(SequentialAsyncExecution.class);

	/** {@link #onFinished} 안에서 다음 element 처리 분기를 결정하는 3-way 결과. */
	private enum NextStep { PROCEED, CANCEL, SKIP }
	
	private final FStream<StartableExecution<?>> m_sequence;
	
	@Nullable @GuardedBy("m_aopGuard") private StartableExecution<?> m_cursor = null;
	@GuardedBy("m_aopGuard") private int m_index = -1;
	
	/**
	 * 정적 팩토리 메소드.
	 *
	 * @param sequence 순차 수행할 element 시퀀스. {@code null}이면 안 된다.
	 * @return 새 {@link SequentialAsyncExecution} 인스턴스.
	 * @throws IllegalArgumentException {@code sequence}가 {@code null}인 경우.
	 */
	public static SequentialAsyncExecution of(FStream<StartableExecution<?>> sequence) {
		return new SequentialAsyncExecution(sequence);
	}

	/**
	 * 순차 수행 비동기 객체를 생성한다.
	 * <p>
	 * 수행 순서는 {@code execSeq}가 element를 산출하는 순서로 정해진다.
	 *
	 * @param execSeq 순차 수행될 비동기 수행 시퀀스. {@code null}이면 안 된다.
	 *                빈 시퀀스가 주어지면 {@link #start()} 즉시 정상 완료한다.
	 * @throws IllegalArgumentException {@code execSeq}가 {@code null}인 경우.
	 */
	SequentialAsyncExecution(FStream<StartableExecution<?>> execSeq) {
		Preconditions.checkNotNullArgument(execSeq, "AsyncExecution sequence");

		m_sequence = execSeq;

		setLogger(s_logger);
	}

	/**
	 * 가장 최근에 시작된 element를 반환한다.
	 * <p>
	 * 시퀀스가 정상적으로 모두 완료된 경우에는 {@code null}로 리셋되지만, 중간 element가 실패하거나
	 * 취소되어 시퀀스가 조기 종료된 경우에는 그 element를 그대로 가리킨다. 즉 본 합성 실행이
	 * 종료된 후의 반환 값은 다음과 같다:
	 * <ul>
	 *   <li>{@code COMPLETED} — 항상 {@code null}.</li>
	 *   <li>{@code FAILED} / {@code CANCELLED} — 실패/취소를 유발한 element.</li>
	 * </ul>
	 *
	 * @return 가장 최근에 시작된 element. 아직 어떤 element도 시작되지 않았거나 모든 element가
	 *         정상 완료된 경우 {@code null}.
	 */
	public StartableExecution<?> getCurrentExecution() {
		return m_aopGuard.get(() -> m_cursor);
	}

	/**
	 * 가장 최근에 시작된 element의 0-based 인덱스를 반환한다.
	 * <p>
	 * {@link #getCurrentExecution()}과 같은 원리로, 본 합성 실행이 정상 완료된 후에는 마지막 element의
	 * 인덱스가 그대로 유지되며, 시작 전에는 {@code -1}을 반환한다.
	 *
	 * @return 가장 최근에 시작된 element의 인덱스. 아직 어떤 element도 시작되지 않은 경우 {@code -1}.
	 */
	public int getCurrentExecutionIndex() {
		return m_aopGuard.get(() -> m_index);
	}

	/**
	 * 순차 수행을 시작한다.
	 * <p>
	 * 가상의 "이전 element 완료" 이벤트를 발생시켜 첫 element를 시작시킨다.
	 * 이미 시작된 상태이거나 종료된 상태에서는 아무 동작을 하지 않는다.
	 */
	@Override
	public void start() {
		if ( !notifyStarting() ) {
			return;
		}
		onFinished(Result.success(null));
	}

	/**
	 * {@link CancellableWork} 구현. 현재 수행 중인 element에 {@code cancel(true)}를 전파한다.
	 * <p>
	 * 본 메소드는 항상 {@code true}를 반환한다 — 실제 element 취소 성공 여부와 무관하며, element가
	 * 취소되지 않더라도 다음 종료 콜백에서 {@link AbstractAsyncExecution#isCancelRequested()}에 의해
	 * 본 실행은 CANCELLED로 종료된다.
	 *
	 * @return 항상 {@code true}.
	 */
	@Override
	public boolean cancelWork() {
		m_aopGuard.run(() -> {
			getLogger().debug("cancelWork() called: index={}, current={}", m_index, m_cursor);
			if ( m_cursor != null ) {
				m_cursor.cancel(true);
			}
		});
		return true;
	}
	
	@Override
	public String toString() {
		return m_aopGuard.get(() ->
					String.format("Sequential[index=%d, current=%s]", m_index, m_cursor));
	}
	
	/**
	 * 직전 element의 종료 결과를 받아 다음 element를 진행하거나 합성 실행을 종료한다.
	 * <p>
	 * 이 메소드는 두 경로에서 호출된다:
	 * <ul>
	 *   <li>{@link #start()}에서 가상의 "이전 element 성공"(결과 {@code null})으로 한 번.</li>
	 *   <li>각 element의 {@code whenFinishedAsync} 콜백에서 비동기로.</li>
	 * </ul>
	 * 직전 결과가 성공이면 다음 element를 꺼내 시작시키고, 다음 element가 없으면 직전 결과를 본 실행의
	 * 결과로 하여 {@code COMPLETED}로 종료시킨다. 즉 비-빈 시퀀스의 경우 마지막 element의 결과가, 빈
	 * 시퀀스의 경우 {@code null}(가상 성공의 결과)이 본 실행의 결과가 된다. 직전 결과가 실패/취소이면
	 * 본 실행도 동일하게 종료된다.
	 *
	 * @param result 직전 element의 종료 결과(또는 {@link #start()}의 가상 성공).
	 */
	private void onFinished(Result<?> result) {
		if ( result.isSuccessful() ) {
			// 시퀀스가 비어 있는 경우 notifyStarting()만 호출된 상태이므로
			// 본 합성 실행을 RUNNING으로 전이시키기 위해 먼저 강제로 notifyStarted()를 호출한다.
			int index = m_aopGuard.get(() -> m_index);
			if ( index == -1 ) {
				notifyStarted();
			}

			m_sequence.next()
					.ifPresent(next -> {
						// 상태 점검과 cursor 갱신을 한 lock 영역에서 처리하여
						// 그 사이에 외부 cancel이 끼어드는 race를 차단한다.
						// (next.start()는 lock 밖에서 호출 — element 콜백 체인이 lock을
						// 의도치 않게 길게 잡지 않도록.)
						NextStep step = m_aopGuard.get(() -> {
							if ( isCancelRequested() ) {
								return NextStep.CANCEL;
							}
							if ( !isRunning() ) {
								return NextStep.SKIP;
							}
							m_cursor = next;
							++m_index;
							return NextStep.PROCEED;
						});
						switch ( step ) {
							case CANCEL:
								getLogger().debug("cancel requested before next element: index={}", index + 1);
								notifyCancelled();
								break;
							case PROCEED:
								getLogger().debug("starting element[{}]: {}", index + 1, next);
								next.whenFinishedAsync(this::onFinished);
								next.start();
								break;
							case SKIP:
								getLogger().debug("composite no longer RUNNING; skip element[{}]", index + 1);
								break;
						}
					})
					.ifAbsent(() -> {
						m_aopGuard.run(() -> m_cursor = null);

						getLogger().debug("sequence exhausted, attempt to complete");
						if ( !notifyCompleted(result.getUnchecked()) ) {
							// CANCELLING 상태에서 마지막 element가 정상 완료된 경우
							// notifyCompleted는 false를 반환한다. 외부 cancel 요청을
							// 존중해 최종 상태를 CANCELLED로 정한다.
							getLogger().debug("notifyCompleted failed (already CANCELLING); transition to CANCELLED");
							notifyCancelled();
						}
					});
		}
		else if ( result.isNone() ) {
			getLogger().debug("element cancelled at index={}; cancel composite", m_aopGuard.get(() -> m_index));
			notifyCancelled();
		}
		else if ( result.isFailed() ) {
			getLogger().debug("element failed at index={}; fail composite, cause={}",
								m_aopGuard.get(() -> m_index), result.getCause().toString());
			notifyFailed(result.getCause());
		}
		else {
			throw new AssertionError();
		}
	}
}