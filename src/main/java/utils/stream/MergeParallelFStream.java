package utils.stream;

import java.util.concurrent.Executor;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.async.Executions;
import utils.thread.Guard;
import utils.async.StartableExecution;
import utils.func.FOption;
import utils.func.Unchecked;
import utils.stream.FStreams.AbstractFStream;


/**
 * 입력 스트림들의 스트림({@code FStream<FStream<T>>})을 여러 워커 쓰레드로
 * 병렬 소비하여 하나의 출력 스트림으로 병합하는 {@link FStream} 구현체.
 * <p>
 * 본 클래스는 {@link FStream#mergeParallel(FStream, int, Executor)}의 구현체이며
 * 외부에서 직접 인스턴스화하지 않고 해당 정적 메소드를 통해 생성한다.
 * <p>
 * 동작 개요:
 * <ul>
 *   <li>{@code workerCount}개의 워커가 입력 스트림 팩토리({@code fact})로부터 내부 스트림을
 *       하나씩 받아 소비한다. 각 워커는 받은 내부 스트림의 원소를 모두 출력 채널로 push한다.</li>
 *   <li>한 워커가 맡은 내부 스트림 소비를 마치면, 팩토리에서 다음 내부 스트림을 받아 이어
 *       소비한다(워커 재사용).</li>
 *   <li>팩토리가 더 이상 내부 스트림을 내놓지 않게 되면 해당 워커는 종료한다. 모든 워커가
 *       종료된 시점에 출력 채널이 닫혀 EOS가 신호된다.</li>
 * </ul>
 * 워커가 동시에 push하므로 <b>출력 순서는 정의되지 않으며</b>, 서로 다른 입력 스트림의
 * 원소가 임의로 섞여 나타날 수 있다.
 * <p>
 * 출력 채널은 워커 수와 동일한 용량을 가진 {@link SuppliableFStream}으로, 소비자가 느리면
 * 자연스러운 backpressure가 발생한다.
 * <p>
 * 워커 실행은 생성자에 주어진 {@link Executor}에 위임된다. {@code null}이면 매 작업마다
 * 새 {@link Thread}가 생성되어 수행된다.
 *
 * @param <T> 출력 스트림의 원소 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
class MergeParallelFStream<T> extends AbstractFStream<T> {
	private final FStream<? extends FStream<? extends T>> m_fact;
	private final int m_workerCount;
	private final @Nullable Executor m_executor;

	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private final SuppliableFStream<T> m_outChannel;
	@GuardedBy("m_guard") int m_runningWorkerCount = 0;

	/**
	 * 병합 스트림을 생성한다.
	 *
	 * @param fact			병합할 입력 스트림들을 공급하는 팩토리 스트림. {@code null} 불가.
	 * @param workerCount	병렬로 입력 스트림을 소비할 워커 쓰레드의 수. 양의 정수여야 한다.
	 * @param executor		워커 실행에 사용할 {@link Executor}. {@code null}이면 매 작업마다
	 * 						새 {@link Thread}가 생성되어 수행된다.
	 * @throws IllegalArgumentException	{@code fact}가 {@code null}이거나 {@code workerCount}가
	 * 									양의 정수가 아닌 경우.
	 */
	MergeParallelFStream(FStream<? extends FStream<? extends T>> fact,
							int workerCount, @Nullable Executor executor) {
		Preconditions.checkNotNullArgument(fact, "mapper is null");
		Preconditions.checkArgument(workerCount > 0, "workerCount should be positive: " + workerCount);

		m_fact = fact;
		m_workerCount = workerCount;
		m_executor = executor;
		m_outChannel = new SuppliableFStream<>(workerCount);
	}
	
	/**
	 * 출력 채널과 입력 팩토리 스트림을 닫는다.
	 * <p>
	 * 출력 채널을 닫으면 진행 중이거나 대기 중인 워커들이 push에 실패하거나 EOS를 인지하여
	 * 협력적으로 종료된다.
	 */
	@Override
	protected void closeInGuard() throws Exception {
		m_guard.run(() -> Unchecked.runOrIgnore(m_outChannel::close));
		Unchecked.runOrIgnore(m_fact::close);
	}

	/**
	 * 최초 호출 시 {@code workerCount}만큼의 워커를 한꺼번에 시작한다.
	 * 각 워커는 팩토리에서 내부 스트림을 받아 소비를 시작하며, 끝나면 다음 스트림으로 이어진다.
	 */
	@Override
	protected void initialize() {
		for ( int i =0; i < m_workerCount; ++i ) {
			++m_runningWorkerCount;
			startNext();
		}
	}

	/**
	 * 출력 채널에서 다음 원소를 가져온다.
	 * <p>
	 * 워커들이 비동기로 push하므로 호출자는 다음 원소가 도착할 때까지 블록될 수 있다.
	 */
	@Override
	public FOption<T> nextInGuard() {
		return m_outChannel.next();
	}

	/**
	 * 팩토리에서 다음 내부 스트림을 가져와 워커 실행을 시작한다.
	 * <p>
	 * 가져온 내부 스트림이 있으면 {@link Executor}(또는 신규 {@link Thread})에 작업을 제출하여
	 * 해당 스트림의 모든 원소를 출력 채널로 push한다. 작업이 끝나면 {@code whenFinished}
	 * 콜백을 통해 본 메소드를 재귀적으로 호출하여 워커를 다음 내부 스트림으로 이어붙인다.
	 * <p>
	 * 팩토리가 더 이상 내부 스트림을 내놓지 않으면 활성 워커 카운터를 감소시키고, 마지막
	 * 워커였다면 출력 채널에 EOS를 신호한다.
	 */
	private void startNext() {
		m_guard.lock();
		try {
			m_fact.next()
				.ifPresent(strm -> {
					StartableExecution<Void> exec = Executions.toExecution(() -> {
						try {
							strm.forEachOrThrow(m_outChannel::supply);
						}
						catch ( InterruptedException e ) {
							getLogger().warn("interrupted while consuming stream: {}", strm, e);
							Thread.currentThread().interrupt();
						}
					}, m_executor);
					exec.whenFinished(ret -> startNext());
					exec.start();
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