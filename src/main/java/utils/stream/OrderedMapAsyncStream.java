package utils.stream;


import java.util.concurrent.CancellationException;

import javax.annotation.concurrent.GuardedBy;

import utils.Preconditions;
import utils.Tuple;
import utils.async.AsyncResult;
import utils.async.Executions;
import utils.async.StartableExecution;
import utils.func.CheckedFunction;
import utils.func.FOption;
import utils.func.Try;
import utils.func.Unchecked;
import utils.stream.FStreams.AbstractFStream;
import utils.thread.Guard;


/**
 * 입력 스트림 각 원소에 비동기 매핑을 적용하되 <b>입력 순서를 결과에 그대로 보존</b>하는 스트림.
 * <p>
 * {@code workerCount}개의 매핑 작업을 동시에 시작해 두지만, 결과는 항상 입력에서의 원래 순서대로
 * 방출된다. 이를 위해 시작된 작업들의 핸들({@link StartableExecution})을 입력 순서대로
 * {@code m_outChannel}에 enqueue하고, 소비자는 그 핸들을 꺼내 {@link StartableExecution#waitForFinished}
 * 로 결과를 기다린다. 따라서 어느 한 작업이 느리면 그 뒤의 빠른 작업 결과들이 큐에 쌓여 대기한다.
 * <p>
 * 매핑 결과는 {@link Try}로 감싸여 노출된다 — mapper에서 예외가 발생하면 그 원소는
 * {@link Try#failure(Throwable)}로 결과 스트림에 포함되고, 정상 결과는 {@link Try#success(Object)}로
 * 포함된다. 결과 스트림은 mapper 예외로 인해 조기 종료되지 않는다.
 * <p>
 * 입력 순서를 유지하지 않아도 되는 경우 더 높은 처리량을 제공하는 {@link UnorderedMapAsyncStream}을
 * 사용한다.
 * <p>
 * <b>close 의미론</b>: {@link #close()} 호출 시 출력 채널과 입력 스트림을 닫는다. 이미 시작되어 진행
 * 중인 매핑 작업에는 {@code cancel}을 전파하지 않으므로 작업은 끝까지 실행되지만, 그 결과는 닫힌
 * 채널에 enqueue되거나 소비자에 의해 더 이상 소비되지 않아 silent drop된다. close 이후 새로 호출되는
 * {@link #startNext()}는 {@link IllegalStateException}으로 즉시 빠져나온다.
 *
 * @param <S> 입력 스트림 원소 타입.
 * @param <T> 매핑 결과 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
class OrderedMapAsyncStream<S,T> extends AbstractFStream<Tuple<S,Try<T>>> {
	/** 입력 스트림에 매핑 함수를 적용해 {@code (input, exec)} 튜플로 변환한 lazy 스트림. */
	private final FStream<Tuple<S,StartableExecution<T>>> m_jobStream;

	/** 워커 수, executor 등 비동기 실행 옵션. */
	private final AsyncExecutionOptions m_options;
	/** Producer 측 상태({@link #m_outChannel} 변경, {@link #m_runningWorkerCount} 변경)를 보호한다. */
	private final Guard m_guard = Guard.create();
	/**
	 * Producer 측 operations ({@code supply}, {@code endOfSupply}, {@code close})은
	 * {@code m_guard} 안에서 직렬화된다. Consumer의 {@code next()} 는 {@link SuppliableFStream}
	 * 자체의 thread-safety에 의존해 lock 없이 호출된다.
	 */
	private final SuppliableFStream<Tuple<S,StartableExecution<T>>> m_outChannel;
	/**
	 * 아직 종료 시그널이 필요한 worker slot 수. {@link #startNext()} 가 빈 입력을 만날 때마다
	 * 감소하며, 0이 되면 {@code m_outChannel.endOfSupply()}를 호출하여 소비자에게 종료를 알린다.
	 */
	@GuardedBy("m_guard") int m_runningWorkerCount = 0;

	/**
	 * @param src     입력 스트림. {@code null} 여부는 별도 검증하지 않으므로 호출자가 non-null임을
	 *                보장해야 한다.
	 * @param mapper  각 원소에 적용할 매핑 함수.
	 * @param options 워커 수/executor 등 비동기 옵션. {@code null} 여부는 별도 검증하지 않으므로
	 *                호출자가 non-null임을 보장해야 한다.
	 *                {@link AsyncExecutionOptions#getKeepOrder()}는 본 클래스가 항상 순서 보존이므로
	 *                참고되지 않으며, {@link AsyncExecutionOptions#getWorkerCount()}는 동시 실행 작업 수
	 *                및 내부 채널 버퍼 크기로 사용된다.
	 * @throws IllegalArgumentException {@code mapper}가 {@code null}인 경우.
	 */
	OrderedMapAsyncStream(FStream<S> src, CheckedFunction<? super S, ? extends T> mapper,
							AsyncExecutionOptions options) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

		m_options = options;
		m_jobStream = src.map(input -> applyFunction(mapper, input));
		m_outChannel = new SuppliableFStream<>(options.getWorkerCount());
	}

	/**
	 * 내부 채널과 입력 스트림을 close한다. close 중 발생한 예외는 무시된다.
	 * <p>
	 * {@code m_outChannel.close()}는 {@code m_guard} 안에서 호출해 동시 진행 중인
	 * {@link #startNext()}와 직렬화한다. 반면 {@code m_jobStream.close()}는 외부 스트림이라
	 * close가 길어질 수 있어 lock 밖에서 호출한다 — {@code m_jobStream}은 close될 시점에는
	 * 더 이상 {@code startNext()}에서 접근되지 않는다 (위의 {@code m_outChannel.close()}로
	 * 종료 상태 도달 후 새 startNext 호출은 즉시 IllegalStateException으로 종료).
	 */
	@Override
	protected void closeInGuard() throws Exception {
		m_guard.run(() -> Unchecked.runOrIgnore(m_outChannel::close));
		Unchecked.runOrIgnore(m_jobStream::close);
	}

	/**
	 * 워커 수만큼 작업을 미리 시작해 채널에 채워 둔다. {@link AbstractFStream}에 의해 첫 {@link #next()}
	 * 직전에 한 번 호출된다.
	 */
	@Override
	protected void initialize() {
		int n = m_options.getWorkerCount();
		// {@code m_runningWorkerCount} 는 @GuardedBy("m_guard") 이므로 lock 안에서 초기화한다.
		m_guard.run(() -> m_runningWorkerCount = n);
		for ( int i = 0; i < n; ++i ) {
			startNext();
		}
	}

	/**
	 * 채널 헤드의 작업이 끝나기를 기다린 후 그 결과를 {@link Try}로 감싸 반환하고, 새 작업을 채널 끝에
	 * 시작한다. 채널이 비어 있으면 {@link FOption#empty()}를 반환한다.
	 * <p>
	 * 결과 매핑/예외 처리 규칙은 {@link #awaitResult(Tuple)}을 참고한다.
	 */
	@Override
	protected FOption<Tuple<S,Try<T>>> nextInGuard() {
		return m_outChannel.next()
							.map(this::awaitResult)
							.ifPresent(__ -> startNext());
	}
	
	/**
	 * 입력 원소를 비동기 매핑 실행 객체로 감싼다. 실제 매핑은 {@link StartableExecution#start()} 호출
	 * 시점에 시작된다.
	 *
	 * @param func  매핑 함수.
	 * @param input 입력 원소.
	 * @return 입력 원소와 아직 시작되지 않은 매핑 실행 객체의 튜플.
	 */
	private Tuple<S,StartableExecution<T>> applyFunction(CheckedFunction<? super S, ? extends T> func,
														S input) {
		StartableExecution<T> exec = Executions.supplyCheckedAsync(() -> func.apply(input),
																	m_options.getExecutor());
		return Tuple.of(input, exec);
	}

	/**
	 * 주어진 매핑 작업이 끝나기를 기다린 후 결과를 {@link Try}로 감싸 반환한다.
	 * <ul>
	 *   <li>정상 종료 → {@link Try#success(Object)}.</li>
	 *   <li>매핑 함수 실패 → 원본 cause를 그대로 담은 {@link Try#failure(Throwable)}
	 *       ({@code ExecutionException} 래핑 없음).</li>
	 *   <li>취소되었거나 예상치 못한 상태 → {@link CancellationException}을 담은 failure.</li>
	 *   <li>대기 중 {@link InterruptedException} 발생 → 인터럽트 플래그를 복원하고 해당 예외를 담은
	 *       failure로 반환한다.</li>
	 * </ul>
	 *
	 * @param job 입력 원소와 시작된 매핑 실행 객체의 튜플.
	 * @return 입력 원소와 {@link Try}로 감싼 매핑 결과의 튜플.
	 */
	private Tuple<S,Try<T>> awaitResult(Tuple<S,StartableExecution<T>> job) {
		S input = job._1();
		StartableExecution<T> exec = job._2();
		
		try {
			AsyncResult<T> ret = exec.waitForFinished();
			if ( ret.isCompleted() ) {
				return Tuple.of(input, Try.success(ret.getUnchecked()));
			}
			if ( ret.isFailed() ) {
				// 원본 cause를 그대로 노출 — MapUnorderedAsyncStream의 Result.asTry()와 일관.
				// {@code ret.get()} 을 호출하면 ExecutionException으로 한 번 래핑되므로 사용하지 않는다.
				return Tuple.of(input, Try.<T>failure(ret.getFailureCause()));
			}
			// cancelled 또는 예상치 못한 상태.
			return Tuple.of(input, Try.<T>failure(new CancellationException()));
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			return Tuple.of(input, Try.<T>failure(e));
		}
	}

	/**
	 * 입력 스트림에서 다음 원소를 꺼내 비동기 매핑 작업을 시작하고 채널에 enqueue한다.
	 * <p>
	 * <b>시작 순서</b>: {@code exec.start()}를 채널 enqueue보다 <em>먼저</em> 호출한다. 반대 순서로
	 * 하면 소비자가 아직 시작되지 않은 exec을 꺼내 {@link StartableExecution#waitForFinished()}에서
	 * 영구 블록되는 race가 발생할 수 있다.
	 * <p>
	 * 입력 스트림이 더 이상 원소를 산출하지 않으면 워커 카운트를 감소시키고, 마지막 워커가 끝나는 시점에
	 * 채널의 {@code endOfSupply}를 호출하여 소비자에게 종료를 알린다.
	 * <p>
	 * <b>예외 처리</b>:
	 * <ul>
	 *   <li>채널 supply 도중 {@link InterruptedException} → 인터럽트 플래그를 복원하고 조용히
	 *       종료한다. exec은 이미 시작되어 자체 실행을 계속한다.</li>
	 *   <li>채널이 동시에 close/endOfSupply된 경우의 {@link IllegalStateException} → 무시한다.
	 *       exec은 끝까지 실행되지만 결과는 폐기된다.</li>
	 *   <li>{@code exec.start()}가 동기적으로 던진 그 외 {@link RuntimeException} → 채널을
	 *       {@code endOfSupply(e)}로 종료시켜 소비자에게 예외로 전파한다. 이미 enqueue된 결과는
	 *       정상 소비된 뒤 마지막에 본 예외가 던져진다.</li>
	 * </ul>
	 */
	private void startNext() {
		m_guard.lock();
		try {
			m_jobStream.next()
						.ifPresent(job -> {
							try {
								S input = job._1();
								StartableExecution<T> exec = job._2();
								// exec을 먼저 시작한 후 채널에 enqueue한다 — 반대 순서로 하면
								// consumer가 unstarted exec을 꺼내 waitForFinished()에서 블록되는
								// race가 생길 수 있다.
								exec.start();
								m_outChannel.supply(Tuple.of(input, exec));
							}
							catch ( InterruptedException e ) {
								getLogger().warn("interrupted while supplying worker's result", e);
								Thread.currentThread().interrupt();
							}
							catch ( IllegalStateException ignored ) {
								// 동시에 close되었거나 endOfSupply된 경우. exec은 이미 시작되어
								// 자체적으로 완료되지만 결과는 폐기된다.
							}
							catch ( RuntimeException e ) {
								// Defensive — exec.start()가 동기적으로 RuntimeException을 던지는 이론적
								// 케이스의 안전망. utils.async.CompletableFutureAsyncExecution#start()
								// 은 내부에서 Throwable을 catch하여 exec를 FAILED 상태로 전이시키므로
								// 보통은 여기에 도달하지 않으며, RejectedExecutionException 등은 element-wise
								// Try.failure로 노출된다. 그래도 custom Execution 구현이나 state-machine 이상
								// 으로 예외가 흘러나오는 경우를 대비해 fatal로 처리한다 — 이미 채널에 적재된
								// 결과는 consumer가 정상 소비하고, 그 후 본 에러가 RuntimeException으로
								// 래핑되어 next()에서 전파된다.
								m_outChannel.endOfSupply(e);
							}
						})
						.ifAbsent(() -> {
							if ( --m_runningWorkerCount == 0 ) {
								m_outChannel.endOfSupply();
							}
						});
		}
		finally {
			m_guard.unlock();
		}
	}
}