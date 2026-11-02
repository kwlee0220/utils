package utils.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.async.Execution;
import utils.async.Executions;
import utils.async.StartableExecution;
import utils.func.FOption;
import utils.func.Result;
import utils.stream.FStream;
import utils.stream.SuppliableFStream;
import utils.stream.TimedFStream;


/**
 * 여러 작업({@link Execution})의 결과를 <b>완료된 순서대로</b> 꺼내는 {@link FStream}.
 * <p>
 * 원소는 {@code T}가 아니라 {@link Result Result&lt;T&gt;}다. 성공한 값뿐 아니라 실패 원인과 취소도 원소로 전달된다.
 * 먼저 등록한 작업이 오래 걸려도 나중에 등록한 작업의 결과를 먼저 꺼낼 수 있다. {@link #next()}는 꺼낼 결과가 없으면
 * 결과가 도착할 때까지 기다린다.
 * <p>
 * <b>작업 등록</b>은 시작을 누가 맡는지에 따라 나뉜다.
 * <ul>
 *   <li>{@link #register(Execution)} — 작업을 시작하지 않고 결과만 수집한다. 시작은 호출자가 맡는다. 이미 끝난
 *       작업도 받으며, 그 결과는 바로 수집된다.</li>
 *   <li>{@link #submit(StartableExecution)} — 아직 시작하지 않은 작업({@link Execution#isNotStarted()})을 받아 본
 *       스트림이 시작한다. 제출한 뒤에는 호출자가 직접 시작하지 않는다.</li>
 *   <li>{@link #submit(Callable, Executor)} — 주어진 작업으로 실행을 만들어 시작한다.</li>
 * </ul>
 * <p>
 * <b>용량</b>: 등록했지만 아직 {@code next()}로 꺼내지 않은 작업 수는 생성자에 준 용량을 넘지 않는다. 용량에 이르면
 * 등록 메소드가 빈자리가 생길 때까지 기다린다(호출자 쓰레드에서, 어떤 lock도 잡지 않은 채). 따라서 끝난 결과가 메모리에
 * 쌓이는 양은 용량으로 제한되고, 작업의 완료 콜백은 결과를 넣으며 막히지 않는다.
 * <p>
 * <b>종료</b>
 * <ul>
 *   <li>{@link #finishSubmissions()} — 추가 등록을 막는다. 이미 등록한 작업의 결과는 (아직 끝나지 않았더라도) 모두
 *       전달한 뒤 스트림이 끝난다. 모든 결과를 끝까지 읽으려면 이것을 쓴다.</li>
 *   <li>{@link #close()} — 수집을 중단한다. 아직 꺼내지 않은 결과와 이후 도착하는 결과를 버린다. 등록한 작업을
 *       취소하지는 않는다. 작업도 멈춰야 하면 호출자가 따로 취소한다.</li>
 * </ul>
 * <p>
 * (2026-10-31에 다시 설계했다. 이전에는 {@code SuppliableFStream}을 상속해 {@code supply()}가 공개되어 있었고,
 * {@code submit(Execution)}이 {@code isStarted()}로 시작 여부를 잘못 판단했으며, 미완료 결과를 버리는
 * {@code endOfSupply()}만 있었다. 완료 콜백이 작업의 lock을 쥔 채 가득 찬 buffer를 기다리기도 했다. 검토 #14.)
 *
 * @param <T>	작업 결과 값의 타입. 스트림 원소는 {@code Result<T>}다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CompletionFStream<T> implements TimedFStream<Result<T>> {
	private static final Logger s_logger = LoggerFactory.getLogger(CompletionFStream.class);

	private final int m_capacity;
	/** 완료된 결과를 담는 buffer. 등록 수 제한 덕분에 결과를 넣을 때 가득 차 있는 일이 없다. */
	private final SuppliableFStream<Result<T>> m_buffer;

	private final Guard m_guard = Guard.create();
	/** 등록했지만 아직 {@code next()}로 꺼내지 않은 작업 수. 용량을 넘지 않는다. */
	@GuardedBy("m_guard") private int m_outstanding = 0;
	/** 등록했지만 결과가 아직 buffer에 들어가지 않은 작업 수. */
	@GuardedBy("m_guard") private int m_undelivered = 0;
	@GuardedBy("m_guard") private boolean m_finishing = false;
	@GuardedBy("m_guard") private boolean m_closed = false;

	/**
	 * 주어진 용량의 {@code CompletionFStream}을 생성한다.
	 *
	 * @param capacity	등록했지만 아직 꺼내지 않은 작업의 최대 수 (양수). 결과를 담는 buffer의 크기이기도 하다.
	 * @throws IllegalArgumentException	{@code capacity}가 양수가 아닌 경우
	 */
	public CompletionFStream(int capacity) {
		Preconditions.checkArgument(capacity > 0, "capacity must be positive: %s", capacity);

		m_capacity = capacity;
		m_buffer = new SuppliableFStream<>(capacity);
	}

	/**
	 * 등록했지만 아직 꺼내지 않은 작업의 최대 수를 반환한다.
	 *
	 * @return	용량
	 */
	public int getCapacity() {
		return m_capacity;
	}

	/**
	 * 작업을 시작하지 않고 그 결과만 수집하도록 등록한다.
	 * <p>
	 * 작업의 시작은 호출자가 맡는다. 작업의 상태는 묻지 않는다. 이미 끝난 작업은 결과가 바로 수집되고, 시작 전에 취소된
	 * 작업은 취소 결과가 수집된다. 같은 작업을 두 번 등록하면 결과도 두 번 수집된다.
	 * <p>
	 * 아직 꺼내지 않은 작업 수가 용량에 이르면 빈자리가 생길 때까지 기다린다.
	 *
	 * @param exec	결과를 수집할 작업 (non-null)
	 * @return	{@code exec}
	 * @throws IllegalArgumentException	{@code exec}가 {@code null}인 경우
	 * @throws IllegalStateException	{@link #finishSubmissions()}나 {@link #close()}가 이미 호출된 경우(기다리는 중에
	 * 									호출된 경우 포함)
	 * @throws InterruptedException		빈자리를 기다리는 중에 인터럽트된 경우
	 */
	public Execution<T> register(Execution<T> exec) throws InterruptedException {
		Preconditions.checkNotNullArgument(exec, "Execution is null");

		reserveSlot();
		// 이미 끝난 작업이면 바로 deliver가 불린다. lock 밖에서 등록한다.
		exec.whenFinished(this::deliver);
		return exec;
	}

	/**
	 * 아직 시작하지 않은 작업을 등록하고 시작한다.
	 * <p>
	 * 제출한 뒤에는 본 스트림이 작업의 시작을 맡으므로 호출자가 직접 시작하지 않는다. 작업의 {@code start()}가 작업을
	 * 끝내지 않은 채 예외를 던지면 작업을 {@code cancel(true)}해 취소 결과가 수집되게 하고(그래야
	 * {@link #finishSubmissions()} 뒤에 스트림이 끝난다) 예외를 다시 던진다.
	 *
	 * @param exec	시작할 작업 (non-null, {@link Execution#isNotStarted()} 상태)
	 * @return	{@code exec}
	 * @throws IllegalArgumentException	{@code exec}가 {@code null}이거나 이미 시작(또는 종료)된 경우
	 * @throws IllegalStateException	{@link #finishSubmissions()}나 {@link #close()}가 이미 호출된 경우
	 * @throws InterruptedException		빈자리를 기다리는 중에 인터럽트된 경우
	 */
	public Execution<T> submit(StartableExecution<T> exec) throws InterruptedException {
		Preconditions.checkNotNullArgument(exec, "StartableExecution is null");
		// isStarted()는 "RUNNING에 도달한 적이 있는가"라서 STARTING, 시작 전 취소 등과 구분하지 못한다.
		Preconditions.checkArgument(exec.isNotStarted(), "execution is already started: %s", exec);

		register(exec);
		try {
			exec.start();
		}
		catch ( RuntimeException | Error e ) {
			if ( !exec.isDone() ) {
				try {
					exec.cancel(true);
				}
				catch ( RuntimeException ignored ) {
					s_logger.warn("failed to cancel an execution that failed to start: {}", exec, ignored);
				}
			}
			throw e;
		}
		return exec;
	}

	/**
	 * 주어진 작업으로 실행을 만들어 등록하고 시작한다.
	 *
	 * @param task		수행할 작업 (non-null)
	 * @param executor	작업을 수행할 {@link Executor}. {@code null}이면 {@code CompletableFuture}의 기본 비동기 풀을 쓴다.
	 * @return	작업의 실행
	 * @throws IllegalArgumentException	{@code task}가 {@code null}인 경우
	 * @throws IllegalStateException	{@link #finishSubmissions()}나 {@link #close()}가 이미 호출된 경우
	 * @throws InterruptedException		빈자리를 기다리는 중에 인터럽트된 경우
	 */
	public Execution<T> submit(Callable<T> task, @Nullable Executor executor) throws InterruptedException {
		Preconditions.checkNotNullArgument(task, "task is null");
		checkAccepting();

		return submit(Executions.<T>supplyCheckedAsync(task::call, executor));
	}

	/**
	 * 추가 등록을 막는다. 이미 등록한 작업의 결과는 아직 끝나지 않았더라도 모두 전달한 뒤 스트림이 끝난다
	 * ({@link #next()}가 빈 값을 반환한다). 여러 번 호출해도 된다. 빈자리를 기다리던 등록 호출은
	 * {@link IllegalStateException}으로 끝난다.
	 */
	public void finishSubmissions() {
		boolean endNow = m_guard.get(() -> {
			if ( m_finishing ) {
				return false;
			}
			m_finishing = true;
			return m_undelivered == 0;
		});
		if ( endNow ) {
			m_buffer.endOfSupply();
		}
	}

	/**
	 * {@link #finishSubmissions()}가 호출되었는지 여부를 반환한다.
	 *
	 * @return	추가 등록을 받지 않으면 {@code true}
	 */
	public boolean isSubmissionFinished() {
		return m_guard.read(() -> m_finishing);
	}

	/**
	 * 완료된 작업의 결과를 하나 꺼낸다. 꺼낼 결과가 없으면 도착할 때까지 기다린다.
	 *
	 * @return	완료된 작업의 결과. {@link #finishSubmissions()} 뒤 모든 결과를 꺼냈으면 빈 값.
	 */
	@Override
	public FOption<Result<T>> next() {
		return consumed(m_buffer.next());
	}

	/**
	 * 완료된 작업의 결과를 하나 꺼낸다. 주어진 시간 안에 결과가 오지 않으면 {@link TimeoutException}을 던진다.
	 *
	 * @param timeout	최대 대기 시간
	 * @param tu		시간 단위
	 * @return	완료된 작업의 결과. {@link #finishSubmissions()} 뒤 모든 결과를 꺼냈으면 빈 값.
	 * @throws TimeoutException	시간 안에 결과가 오지 않은 경우
	 */
	@Override
	public FOption<Result<T>> next(long timeout, TimeUnit tu) throws TimeoutException {
		return consumed(m_buffer.next(timeout, tu));
	}

	/**
	 * 수집을 중단한다. 아직 꺼내지 않은 결과와 이후 도착하는 결과를 버린다. 등록한 작업은 취소하지 않는다.
	 * 닫은 뒤의 {@link #next()}는 빈 값을 반환한다.
	 * 빈자리를 기다리던 등록 호출은 {@link IllegalStateException}으로 끝난다. 여러 번 호출해도 된다.
	 */
	@Override
	public void close() {
		m_guard.run(() -> m_closed = true);
		m_buffer.close();
	}

	/**
	 * {@link #close()}가 호출되었는지 여부를 반환한다.
	 *
	 * @return	닫혔으면 {@code true}
	 */
	public boolean isClosed() {
		return m_guard.read(() -> m_closed);
	}

	@Override
	public String toString() {
		return m_guard.read(() -> String.format("%s[capacity=%d, outstanding=%d, undelivered=%d, finishing=%s, closed=%s]",
												getClass().getSimpleName(), m_capacity, m_outstanding, m_undelivered,
												m_finishing, m_closed));
	}

	private void checkAccepting() {
		m_guard.run(() -> Preconditions.checkState(!m_closed && !m_finishing,
													"CompletionFStream is closed or finishing submissions"));
	}

	/**
	 * 등록할 자리를 하나 확보한다. 꺼내지 않은 작업 수가 용량에 이르면 빈자리가 생기거나 종료될 때까지 기다린다.
	 */
	private void reserveSlot() throws InterruptedException {
		m_guard.awaitCondition(() -> m_closed || m_finishing || m_outstanding < m_capacity)
				.andRun(() -> {
					Preconditions.checkState(!m_closed && !m_finishing,
											"CompletionFStream is closed or finishing submissions");
					++m_outstanding;
					++m_undelivered;
				});
	}

	/**
	 * 작업의 완료 콜백. 결과를 buffer에 넣는다. 작업의 lock을 쥔 채 불리므로 막히지 않아야 한다.
	 * <p>
	 * 등록 수가 용량으로 제한되어 buffer에는 언제나 빈자리가 있다. 그래도 기다리지 않도록 시간 제한 0으로 넣는다.
	 */
	private void deliver(Result<T> result) {
		try {
			m_buffer.supply(result, 0, TimeUnit.MILLISECONDS);
		}
		catch ( IllegalStateException closed ) {
			// 소비자가 close한 경우. 결과를 버린다.
		}
		catch ( TimeoutException e ) {
			// 등록 수 제한 때문에 일어나지 않아야 한다.
			s_logger.error("result buffer is unexpectedly full; dropping a result: {}", result);
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			s_logger.warn("interrupted while delivering a result; dropping it: {}", result);
		}

		boolean endNow = m_guard.get(() -> {
			--m_undelivered;
			return m_finishing && m_undelivered == 0;
		});
		if ( endNow ) {
			m_buffer.endOfSupply();
		}
	}

	/** 결과를 하나 꺼냈으면 자리를 하나 반환한다. */
	private FOption<Result<T>> consumed(FOption<Result<T>> next) {
		if ( next.isPresent() ) {
			m_guard.run(() -> --m_outstanding);
		}
		return next;
	}
}
