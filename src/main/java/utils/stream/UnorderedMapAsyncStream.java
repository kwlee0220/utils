package utils.stream;


import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.annotation.concurrent.GuardedBy;

import utils.Preconditions;
import utils.Tuple;
import utils.async.Executions;
import utils.async.StartableExecution;
import utils.func.CheckedFunction;
import utils.func.FOption;
import utils.func.Try;
import utils.func.Unchecked;
import utils.stream.FStreams.AbstractFStream;
import utils.thread.Guard;


/**
 * 입력 스트림 각 원소에 비동기 매핑을 적용하되 <b>결과 순서를 보존하지 않는</b> 스트림.
 * <p>
 * {@code workerCount}개의 매핑 작업을 동시에 시작해 두고, <b>완료되는 작업의 결과부터</b> 출력 채널에
 * 푸시한다. 따라서 빠르게 끝난 결과가 입력 순서보다 먼저 노출될 수 있다. 한 worker의 작업이 끝나면 그
 * worker가 입력 스트림에서 다음 원소를 꺼내 매핑을 이어가는 식으로, 모든 입력이 소진될 때까지 동시에
 * 최대 {@code workerCount}개의 매핑이 진행된다.
 * <p>
 * {@link OrderedMapAsyncStream}과 비교한 특징:
 * <ul>
 *   <li><b>처리량</b>: 느린 작업이 다른 빠른 작업의 결과 방출을 막지 않으므로 일반적으로 높다.</li>
 *   <li><b>순서</b>: 입력 순서와 출력 순서가 다를 수 있다. 순서 보존이 필요하면
 *       {@link OrderedMapAsyncStream}을 사용한다.</li>
 * </ul>
 * <p>
 * 매핑 결과는 {@link Try}로 감싸여 노출된다 — mapper에서 예외가 발생하면 그 원소는
 * {@link Try#failure(Throwable)}로 결과 스트림에 포함되고 정상 결과는 {@link Try#success(Object)}로
 * 포함된다. 결과 스트림은 mapper 예외로 인해 조기 종료되지 않는다.
 * <p>
 * <b>close 의미론</b>: {@link #close()} 호출 시 입력 스트림과 출력 채널을 닫고, 진행 중인 모든 매핑
 * 작업에 {@code cancel(true)}를 전파한다. 다만 매핑 함수가
 * {@link InterruptedException}/interrupt 상태를 적극적으로 처리하지 않으면 작업이 끝까지 실행될 수
 * 있다 (Java의 {@code CompletableFuture.cancel}은 진행 중인 supplier thread에 interrupt를 보내지
 * 않는다는 한계가 있음). close 시점 이후에 도착하는 결과는 닫힌 채널에 공급되며 silent drop된다.
 *
 * @param <S> 입력 스트림 원소 타입.
 * @param <T> 매핑 결과 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
class UnorderedMapAsyncStream<S,T> extends AbstractFStream<Tuple<S,Try<T>>> {
	private final FStream<Tuple<S,StartableExecution<T>>> m_jobStream;

	private final AsyncExecutionOptions m_options;
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private final SuppliableFStream<Tuple<S, Try<T>>> m_outChannel;
	@GuardedBy("m_guard") int m_runningWorkerCount = 0;
	@GuardedBy("m_guard") private final Set<StartableExecution<T>> m_activeJobs
													= Collections.newSetFromMap(new IdentityHashMap<>());

	/**
	 * @param src     입력 스트림. {@code null} 여부는 별도 검증하지 않으므로 호출자가 non-null임을
	 *                보장해야 한다.
	 * @param mapper  각 원소에 적용할 매핑 함수.
	 * @param options worker 수/executor 등 비동기 옵션. {@code null} 여부는 별도 검증하지 않으므로
	 *                호출자가 non-null임을 보장해야 한다.
	 *                {@link AsyncExecutionOptions#getKeepOrder()}는 본 클래스가 항상 비-보존이므로
	 *                참고되지 않으며, {@link AsyncExecutionOptions#getWorkerCount()}는 동시 실행 작업
	 *                수 및 내부 채널 버퍼 크기로 사용된다.
	 * @throws IllegalArgumentException {@code mapper}가 {@code null}인 경우.
	 */
	UnorderedMapAsyncStream(FStream<S> src, CheckedFunction<? super S, ? extends T> mapper,
								AsyncExecutionOptions options) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

		m_options = options;
		m_jobStream = src.map(input -> applyFunction(mapper, input));
		m_outChannel = new SuppliableFStream<>(options.getWorkerCount());
	}

	/**
	 * 입력 스트림과 출력 채널을 닫고, 진행 중인 모든 매핑 작업에 {@code cancel(true)}를 전파한다.
	 * close 과정에서 발생한 예외는 모두 무시된다.
	 */
	@Override
	protected void closeInGuard() throws Exception {
		// 입력 스트림을 닫는다. 이 때 발생하는 예외는 무시한다.
		Unchecked.runOrIgnore(m_jobStream::close);

		// 현재 진행 중인 작업이 있다면 모두 취소한다.
		List<StartableExecution<T>> jobs = m_guard.get(() -> {
			List<StartableExecution<T>> snap = new ArrayList<>(m_activeJobs);
			m_activeJobs.clear();
			return snap;
		});
		jobs.forEach(job -> Unchecked.runOrIgnore(() -> job.cancel(true)));

		m_guard.run(() -> Unchecked.runOrIgnore(m_outChannel::close));
	}

	/**
	 * worker 수만큼 작업을 미리 시작해 둔다. {@link AbstractFStream}에 의해 첫 {@link #next()} 직전에
	 * 한 번 호출된다.
	 */
	@Override
	protected void initialize() {
		// 허용된 동시 작업 수만큼 매핑 작업을 시작한다.
		// 각 작업이 끝나면 startNext()가 다음 작업을 시작하는 식으로 진행된다.
		for ( int i =0; i < m_options.getWorkerCount(); ++i ) {
			m_guard.run(() -> m_runningWorkerCount++);
			if ( !startNext() ) {
				// 입력이 worker 수보다 적어 더 이상 시작시킬 작업이 없는 경우 바로 루프를 빠져 나온다.
				break;
			}
		}
	}

	/**
	 * 출력 채널의 다음 결과를 반환한다.
	 * <p>
	 * 채널이 비어 있고 모든 worker가 종료되었으면 {@link FOption#empty()}를 반환하고, 아직 진행 중인
	 * worker가 있으면 결과가 도착할 때까지 대기한다.
	 */
	@Override
	public FOption<Tuple<S, Try<T>>> nextInGuard() {
		return m_outChannel.next();
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
	 * 입력 스트림에서 다음 원소를 꺼내 비동기 매핑 작업을 시작한다.
	 * <p>
	 * 작업 완료 콜백({@code whenFinishedAsync})에서 결과를 출력 채널로 공급한 뒤, 본 worker가 다시
	 * {@link #startNext()}를 호출해 다음 입력을 가져온다. 입력이 소진되면 worker 카운트를 감소시키고,
	 * 마지막 worker가 종료되는 시점에 채널의 {@code endOfSupply}로 소비자에게 종료를 알린다.
	 *
	 * @return 새 매핑 작업을 시작했으면 {@code true}, {@link #m_jobStream}에 더 이상 작업이 없거나
	 *         이미 닫혀 있어 시작하지 못했으면 {@code false}.
	 */
	private boolean startNext() {
		m_guard.lock();
		try {
			FOption<Tuple<S, StartableExecution<T>>> ojob;
			try {
				ojob = m_jobStream.next();
			}
			catch ( IllegalStateException closed ) {
				// closeInGuard()로 입력 스트림이 이미 닫힌 상태.
				// 새 작업을 시작하지 않고 worker 카운트만 정리한다.
				if ( --m_runningWorkerCount == 0 ) {
					Unchecked.runOrIgnore(m_outChannel::endOfSupply);
				}
				return false;
			}
			if ( ojob.isPresent() ) {
				Tuple<S, StartableExecution<T>> job = ojob.get();
				S input = job._1();
				StartableExecution<T> exec = job._2();

				// job-tracking을 위해 active worker 집합에 추가한다. 작업이 끝나면 콜백에서 제거한다.
				m_activeJobs.add(exec);
				// 새로 시작할 작업이 종료될때 수행할 작업을 등록한다.
				exec.whenFinishedAsync(ret -> {
					// job-tracking에서 제거한다.
					m_guard.get(() -> m_activeJobs.remove(exec));

					// supply는 채널이 full일 때 blocking이 가능하므로 m_guard 밖에서 호출한다.
					// m_guard 안에서 호출하면 채널이 full + 소비자가 close 시도 시
					// (closeInGuard도 m_guard를 획득해야 함) deadlock이 발생할 수 있다.
					try {
						m_outChannel.supply(Tuple.of(input, ret.asTry()));
					}
					catch ( IllegalStateException ignored ) {
						// close()가 호출되어 채널이 닫힌 경우,
						// worker가 작업을 마치고 결과를 공급하려 할 때
						// IllegalStateException이 발생할 수 있다.
						// 채널이 이미 종료된 경우는 무시한다.
					}
					catch ( InterruptedException e ) {
						getLogger().warn("worker thread interrupted while supplying result", e);
						Thread.currentThread().interrupt();
					}
					startNext();
				});
				exec.start();
				
				return true;
			}
			else {
				// 입력이 소진되어 더 이상 시작할 작업이 없다.
				// worker 카운트를 감소시키고, 마지막 worker가 종료되면 채널 종료.
				if ( --m_runningWorkerCount == 0 ) {
					m_outChannel.endOfSupply();
				}
				
				return false;
			}
		}
		finally {
			m_guard.unlock();
		}
	}
}