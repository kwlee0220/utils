package utils.thread;

import java.util.ArrayDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;
import utils.Preconditions;
import utils.RuntimeExecutionException;
import utils.RuntimeInterruptedException;


/**
 * 전용 스레드 없이, 작업을 넣은 스레드가 큐를 비우는 방식으로 작업을 하나씩 순서대로 실행하는 실행기.
 * <p>
 * 작업은 FIFO 큐에 들어가고, 한 번에 하나의 스레드(이하 dispatcher)만 큐에서 작업을 꺼내 실행한다.
 * 따라서 작업은 넣은 순서대로, 서로 겹치지 않고(run-to-completion) 실행된다. 별도의 스레드는 만들지 않는다.
 * <ul>
 *   <li>처리 중인 스레드가 없을 때 작업을 넣은 스레드가 dispatcher가 되어, 큐가 빌 때까지 작업을 실행한다.
 *       이때 다른 스레드가 그 사이에 넣은 작업도 함께 실행한다.</li>
 *   <li>다른 스레드가 처리 중일 때 넣은 작업은 큐에만 들어가고, 그 dispatcher가 실행한다.</li>
 *   <li>작업 안에서(즉 dispatcher 스레드에서) 다시 넣은 작업은 현재 작업이 끝난 뒤 같은 스레드가 실행한다.</li>
 * </ul>
 * 처리 중인 스레드가 없으면 작업은 호출한 스레드에서 바로 실행되므로, 경쟁이 없는 경우에는 일반 메소드
 * 호출과 같은 흐름으로 동작한다.
 *
 * <h2>작업을 넣는 방법</h2>
 * <table border="1">
 *   <caption>메소드별 동작</caption>
 *   <tr><th>메소드</th><th>다른 스레드가 처리 중일 때</th><th>결과와 예외</th></tr>
 *   <tr><td>{@link #execute(Runnable)}</td><td>큐에 넣고 바로 반환</td>
 *       <td>결과 없음. 예외는 로그로만 남는다.</td></tr>
 *   <tr><td>{@link #submit(Callable)}, {@link #submit(Runnable)}</td><td>큐에 넣고 바로 반환</td>
 *       <td>반환된 future로 전달된다.</td></tr>
 *   <tr><td>{@link #invokeOrDefer(Callable)}</td><td>작업이 실행될 때까지 기다림
 *       (기다리면 교착되는 경우는 큐에 넣고 바로 반환 = 지연)</td>
 *       <td>{@link Invocation}으로 전달된다. 지연되지 않았으면 {@link Invocation#get()}이 결과를 반환하거나
 *           작업이 던진 예외를 그대로 던진다. 지연되었으면 {@link Invocation#completion()}으로 받는다.</td></tr>
 *   <tr><td>{@link #runAsDispatcher(Runnable)}</td><td>처리가 끝날 때까지 기다린 뒤 dispatcher 역할을 맡아 실행
 *       (큐를 거치지 않는다)</td>
 *       <td>예외는 호출자에게 던진다.</td></tr>
 * </table>
 * {@link #runAsDispatcher(Runnable)}는 다른 메소드와 달리 작업을 큐에 넣지 않는 고급 API이므로 다음 두 가지에
 * 주의한다. 자세한 내용은 그 메소드의 설명을 본다.
 * <ul>
 *   <li>다른 스레드들이 계속 작업을 넣으면 dispatcher 역할을 얻지 못해 끝없이 기다릴 수 있다.</li>
 *   <li>작업 안에서 호출하면 큐를 거치지 않고 현재 작업 안에 중첩되어 바로 실행된다.</li>
 * </ul>
 *
 * <h2>교착 회피</h2>
 * {@link #invokeOrDefer(Callable)}은 다음 두 경우에는 기다리지 않고 큐에만 넣은 뒤 지연된(deferred)
 * {@link Invocation}을 반환한다. 작업은 취소되지 않고 현재 처리가 끝난 뒤 실행되므로, 지연된 호출을 다시
 * 보내면 작업이 두 번 실행된다.
 * <ul>
 *   <li>호출한 스레드가 dispatcher인 경우. 작업 안에서 호출했거나, dispatcher 스레드에서 실행되는 future
 *       후속 작업 안에서 호출한 경우다. 기다리면 자기가 실행할 작업을 자기가 기다리게 된다.</li>
 *   <li>생성자로 바깥 lock({@code outerLock})을 지정했고, 호출한 스레드가 그 lock을 보유한 채 다른 스레드가
 *       처리 중인 경우. 작업이 바깥 lock을 잡는다면, dispatcher는 그 lock을 기다리고 호출자는 작업을 기다리게
 *       된다. 작업이 lock을 잡은 채 실행되도록 설계한 경우(예: 상태 기계의 lock) 그 lock을 지정한다.</li>
 * </ul>
 *
 * <h2>취소</h2>
 * {@link #submit(Callable)}이 반환한 future나 {@link Invocation#completion()}을 취소하는 것은 결과를 받지 않겠다는
 * 뜻일 뿐, 작업을 멈추지 않는다. 다만 작업을 실행하기 직전에 future가 이미 완료(취소 포함)되어 있으면 그 작업은
 * 건너뛴다. 이 검사는 최선 노력(best-effort)이다. 검사 직후에 취소된 작업과 이미 실행 중인 작업은 끝까지 실행되며,
 * 그 결과는 버려진다.
 *
 * <h2>이 방식의 대가</h2>
 * <ol>
 *   <li><b>작업을 넣은 스레드가 오래 붙잡힐 수 있다.</b> dispatcher가 된 스레드는 큐가 빌 때까지 반환하지
 *       못한다. 다른 스레드들이 계속 작업을 넣으면 그 작업까지 실행하느라 호출이 오래 걸린다. 예를 들어 네트워크
 *       수신 스레드가 dispatcher가 되면, 다른 스레드의 작업을 실행하는 동안 다음 메시지를 받지 못한다.</li>
 *   <li><b>작업이 실행되는 스레드가 정해져 있지 않다.</b> 작업은 작업을 넣은 스레드 중 어느 스레드에서든 실행될 수
 *       있다. 특정 스레드에서만 실행해야 하는 코드(예: Swing EDT)나 {@link ThreadLocal}에 기대는 코드는 직접
 *       해당 스레드로 넘겨야 한다.</li>
 *   <li><b>future의 후속 작업이 dispatcher 스레드에서 실행될 수 있다.</b> {@link #submit(Callable)}이 반환한
 *       future에 작업이 끝나기 전에 붙인 {@code thenApply()} 같은 non-async 후속 작업은, future가 완료되는
 *       시점에 dispatcher 스레드에서 실행된다. 후속 작업이 무거우면 그만큼 큐 처리가 늦어진다. 무거운 후속 작업은
 *       {@code thenApplyAsync()} 등으로 다른 실행기에 넘긴다. 반대로 이미 완료된 future에 붙인 후속 작업은 붙인
 *       스레드에서 실행되고, 호출자가 future를 직접 완료(취소 포함)하면 그 스레드에서 실행된다. 따라서 후속 작업이
 *       본 실행기의 직렬 실행 범위에 든다고 가정하지 않는다. 후속 작업에서 작업과 공유하는 데이터를 다룰 때는
 *       따로 동기화하거나, 후속 작업 자체를 본 실행기에 넣는다.</li>
 * </ol>
 *
 * <h2>Guava {@code MoreExecutors.newSequentialExecutor(directExecutor())}와의 차이</h2>
 * Guava의 위 조합도 "처리 중인 스레드가 없으면 호출한 스레드가 큐를 비우고, 처리 중이면 큐에 넣고 바로 반환"하는
 * 같은 방식으로 동작한다. 순서만 지키고 넣은 뒤 바로 반환하면 되는 경우에는 Guava를 그대로 쓰는 것이 낫다.
 * 본 클래스는 Guava에 없는 다음 기능이 필요할 때 쓴다.
 * <table border="1">
 *   <caption>Guava와의 차이</caption>
 *   <tr><th>항목</th><th>Guava</th><th>본 클래스</th></tr>
 *   <tr><td>결과를 기다리는 호출</td><td>없음. {@code execute(Runnable)}만 제공한다.</td>
 *       <td>{@link #invokeOrDefer(Callable)}, {@link #submit(Callable)}</td></tr>
 *   <tr><td>재진입·바깥 lock 교착 회피</td>
 *       <td>처리 상태를 알 수 없어, 결과를 기다리는 호출을 직접 만들면 dispatcher 스레드(예: future 후속 작업)에서
 *           호출할 때 자기 자신을 기다리며 교착된다.</td>
 *       <td>dispatcher 스레드이거나 바깥 lock을 보유한 경우 기다리지 않고 지연시킨다.</td></tr>
 *   <tr><td>dispatcher 역할 선점</td><td>없음</td>
 *       <td>{@link #runAsDispatcher(Runnable)}: 초기화처럼 다른 작업과 겹치지 않게 호출한 스레드에서 실행할 작업에
 *           쓴다. 그동안 넣은 작업은 그 뒤에 실행된다.</td></tr>
 *   <tr><td>작업의 예외</td>
 *       <td>{@code RuntimeException}은 로그만 남긴다. {@code Error}는 그때 큐를 비우던 스레드(관계없는 호출자일
 *           수 있음)에게 던져지고, 남은 작업은 다음 {@code execute()}가 올 때까지 실행되지 않는다.</td>
 *       <td>{@link #execute(Runnable)}로 넣은 작업은 {@code Error}를 포함해 로그만 남기고 다음 작업을 계속
 *           실행한다. {@link #submit(Callable)}과 {@link #invokeOrDefer(Callable)}은 그 작업을 넣은 호출자에게
 *           (future 또는 {@link Invocation#get()}의 예외로) 전달한다.</td></tr>
 *   <tr><td>인터럽트 상태</td>
 *       <td>작업마다 실행 전에 인터럽트 플래그를 지우고, 큐를 다 비운 뒤 되돌린다. dispatcher는 호출자 스레드이므로
 *           호출자가 인터럽트된 상태여도 작업 안에서는 보이지 않는다.</td>
 *       <td>인터럽트 플래그를 건드리지 않는다.</td></tr>
 *   <tr><td>상태 조회</td><td>없음</td>
 *       <td>{@link #isDispatcherThread()}, {@link #getPendingTaskCount()}</td></tr>
 * </table>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class CallerRunsSerialExecutor implements Executor, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(CallerRunsSerialExecutor.class);

	private volatile Logger m_logger = s_logger;

	private final @Nullable Guard m_outerLock;
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private final ArrayDeque<Runnable> m_queue = new ArrayDeque<>();
	@GuardedBy("m_guard") private Thread m_dispatcher;

	/** 작업을 큐에 넣은 뒤 호출한 스레드가 할 일. */
	private enum Role {
		/** 이 스레드가 dispatcher가 되었으므로 큐를 비운다. */
		DRAIN,
		/** 다른 스레드가 처리 중이므로, 필요하면 기다린다. */
		WAIT,
		/** 기다리면 교착되므로 큐에 넣기만 한다. */
		QUEUED,
	}

	/**
	 * {@link #invokeOrDefer(Callable)} 호출 결과.
	 * <p>
	 * 지연되지 않았으면({@link #isDeferred()}가 {@code false}) 작업은 이미 끝났고, {@link #get()}으로 결과를
	 * 받거나 작업이 던진 예외를 그대로 받는다. 지연되었으면 작업은 현재 처리가 끝난 뒤 실행되며, 그 결과와 예외는
	 * {@link #completion()}으로 받는다.
	 *
	 * @param <T>	작업 결과 타입.
	 */
	public static final class Invocation<T> {
		private final boolean m_deferred;
		private final CompletableFuture<T> m_completion;
		private final @Nullable Throwable m_failure;

		private Invocation(boolean deferred, CompletableFuture<T> completion, @Nullable Throwable failure) {
			m_deferred = deferred;
			m_completion = completion;
			m_failure = failure;
		}

		/**
		 * 호출 당시 교착을 피하려고 작업의 완료를 기다리지 않고 반환했는지 여부를 반환한다.
		 * <p>
		 * 이 값은 호출한 시점에 정해지며, 이후 작업이 완료되어도 바뀌지 않는다. 작업의 현재 완료 여부는
		 * {@code completion().isDone()}으로 확인한다. 지연된 작업은 큐에 들어가 현재 처리가 끝난 뒤 실행된다.
		 * 작업을 실행하지 않았다는 뜻이 아니므로 다시 보내면 안 된다.
		 *
		 * @return	호출 당시 지연되었으면 {@code true}.
		 */
		public boolean isDeferred() {
			return m_deferred;
		}

		/**
		 * 작업의 완료를 받을 future를 반환한다.
		 * <p>
		 * 지연되지 않았으면 이미 완료되어 있다. 작업이 던진 예외는 이 future를 예외로 완료시킨다.
		 * {@link CompletableFuture#join()}이나 {@link CompletableFuture#get()}은 작업이 던진
		 * {@link CompletionException}을 future가 감싼 예외와 구분하지 못하므로, 지연되지 않은 호출의 예외를
		 * 그대로 받으려면 {@link #get()}을 사용한다.
		 *
		 * @return	작업 완료 future.
		 */
		public CompletableFuture<T> completion() {
			return m_completion;
		}

		/**
		 * 지연되지 않은 호출의 결과를 반환한다.
		 * <p>
		 * 지연된 호출에는 사용할 수 없다. 작업이 완료된 뒤에도 마찬가지이며, 그 결과는 {@link #completion()}으로 받는다.
		 *
		 * @return	작업 결과. 작업이 {@code null}을 반환했으면 {@code null}.
		 * @throws IllegalStateException	지연된 호출인 경우. 작업이 완료된 뒤에도 던진다.
		 * @throws RuntimeException	작업이 {@link RuntimeException}을 던진 경우, 그 예외 그대로.
		 * 							작업이 던진 {@link CompletionException}도 풀지 않고 그대로 던진다.
		 * @throws Error	작업이 {@link Error}를 던진 경우, 그 오류 그대로.
		 * @throws RuntimeExecutionException	작업이 checked 예외를 던진 경우. 원인으로 그 예외를 갖는다.
		 */
		public T get() {
			Preconditions.checkState(!m_deferred, "get() is unavailable for deferred invocations; use completion()");

			if ( m_failure != null ) {
				if ( m_failure instanceof RuntimeException re ) {
					throw re;
				}
				if ( m_failure instanceof Error error ) {
					throw error;
				}
				throw new RuntimeExecutionException(m_failure);
			}
			return m_completion.join();
		}

		@Override
		public String toString() {
			return String.format("Invocation[deferred=%s, completion=%s]", m_deferred, m_completion);
		}
	}

	/**
	 * 바깥 lock 없이 실행기를 생성한다.
	 */
	public CallerRunsSerialExecutor() {
		this(null);
	}

	/**
	 * 바깥 lock을 지정하여 실행기를 생성한다.
	 * <p>
	 * 이 lock을 보유한 스레드가 {@link #invokeOrDefer(Callable)}을 호출했는데 다른 스레드가 처리 중이면, 기다리지
	 * 않고 지연시킨다. 또한 이 lock을 보유한 채 {@link #runAsDispatcher(Runnable)}를 호출할 수 없다.
	 *
	 * @param outerLock	작업이 실행 중에 잡는 바깥 lock. {@code null}이면 바깥 lock이 없는 것으로 본다.
	 */
	public CallerRunsSerialExecutor(@Nullable Guard outerLock) {
		m_outerLock = outerLock;
	}

	/**
	 * 작업을 큐에 넣는다. 처리 중인 스레드가 없으면 이 스레드가 큐가 빌 때까지 작업을 실행한 뒤 반환한다.
	 * <p>
	 * 작업이 던진 예외({@link Error} 포함)는 로그로만 남고, 다음 작업은 계속 실행된다.
	 * 완료나 예외를 확인해야 하면 {@link #submit(Runnable)}을 사용한다.
	 *
	 * @param task	실행할 작업.
	 * @throws NullPointerException	{@code task}가 {@code null}인 경우.
	 */
	@Override
	public void execute(Runnable task) {
		Preconditions.checkNotNull(task, "task is null");

		// 예외를 로그로만 남기고 다음 작업이 계속 실행되도록 래핑한다.
		Runnable wrapped = () -> {
			try {
				task.run();
			}
			catch ( Throwable e ) {
				getLogger().error("task failed: {}", task, e);
			}
		};
		if ( enqueue(wrapped) == Role.DRAIN ) {
			drain();
		}
	}

	/**
	 * 작업을 큐에 넣고, 작업 결과를 받을 {@link CompletableFuture}를 반환한다.
	 * <p>
	 * 처리 중인 스레드가 없으면 이 스레드가 큐가 빌 때까지 작업을 실행한 뒤 반환하므로, 반환된 future는 이미
	 * 완료되어 있다. 다른 스레드가 처리 중이거나 작업 안에서 호출한 경우는 큐에 넣기만 하고 바로 반환한다.
	 * 작업이 던진 예외는 반환된 future를 예외로 완료시킨다.
	 * <p>
	 * future를 취소해도 작업은 멈추지 않는다. 실행 직전에 이미 취소되어 있으면 건너뛸 뿐이다(클래스 설명의
	 * "취소" 참고).
	 * <p>
	 * future에 붙인 non-async 후속 작업은, 작업이 끝나기 전에 붙였다면 future를 완료시키는 dispatcher
	 * 스레드에서 실행될 수 있다. 이미 완료된 뒤에 붙였거나 호출자가 future를 직접 완료(취소 포함)하면 다른 스레드에서
	 * 실행될 수 있다. 따라서 후속 작업이 본 실행기의 직렬 실행 범위에 든다고 가정하지 않는다.
	 * <b>작업 안에서 반환된 future의 완료를 기다리면 안 된다.</b> 그 작업은 현재 작업이 끝나야 실행되므로
	 * 교착된다.
	 *
	 * @param <T>	작업 결과 타입.
	 * @param task	실행할 작업.
	 * @return	작업 결과를 받을 future.
	 * @throws NullPointerException	{@code task}가 {@code null}인 경우.
	 */
	public <T> CompletableFuture<T> submit(Callable<T> task) {
		Preconditions.checkNotNull(task, "task is null");

		Task<T> wrapped = new Task<>(task);
		if ( enqueue(wrapped) == Role.DRAIN ) {
			drain();
		}
		return wrapped.m_completion;
	}

	/**
	 * 반환값이 없는 작업을 큐에 넣고, 작업 완료를 받을 {@link CompletableFuture}를 반환한다.
	 * <p>
	 * 동작은 {@link #submit(Callable)}과 같다. 작업이 정상 종료하면 future는 {@code null}로 완료된다.
	 *
	 * @param task	실행할 작업.
	 * @return	작업 완료를 받을 future.
	 * @throws NullPointerException	{@code task}가 {@code null}인 경우.
	 */
	public CompletableFuture<Void> submit(Runnable task) {
		Preconditions.checkNotNull(task, "task is null");

		return submit(new Callable<Void>() {
			@Override
			public Void call() {
				task.run();
				return null;
			}

			@Override
			public String toString() {
				return task.toString();
			}
		});
	}

	/**
	 * 작업을 큐에 넣고, 기다릴 수 있으면 작업이 끝날 때까지 기다린다. 기다리면 교착되는 경우는 큐에 넣기만 하고
	 * 바로 반환한다(지연).
	 * <p>
	 * 처리 중인 스레드가 없으면 이 스레드가 큐가 빌 때까지 작업을 실행한다. 다른 스레드가 처리 중이면 그 스레드가
	 * 이 작업을 실행할 때까지 기다린다. 기다리는 동안 인터럽트되어도 기다림을 멈추지 않는다. 이 경우 반환된
	 * {@link Invocation}은 지연되지 않은 것이며, {@link Invocation#get()}으로 결과를 받거나 작업이 던진 예외를
	 * 그대로 받는다.
	 * <p>
	 * 다음 경우에는 기다리면 교착되므로 지연된 {@link Invocation}을 반환한다. 작업은 현재 처리가 끝난 뒤 실행되며,
	 * 그 결과와 예외는 {@link Invocation#completion()}으로 받는다. <b>지연은 작업을 실행하지 않았다는 뜻이 아니다.
	 * 다시 보내면 작업이 두 번 실행된다.</b>
	 * <ul>
	 *   <li>이 스레드가 dispatcher인 경우(작업 안이나, dispatcher 스레드에서 실행되는 future 후속 작업 안).</li>
	 *   <li>이 스레드가 바깥 lock을 보유한 채 다른 스레드가 처리 중인 경우.</li>
	 * </ul>
	 *
	 * @param <T>	작업 결과 타입.
	 * @param task	실행할 작업.
	 * @return	호출 결과.
	 * @throws NullPointerException	{@code task}가 {@code null}인 경우.
	 */
	public <T> Invocation<T> invokeOrDefer(Callable<T> task) {
		Preconditions.checkNotNull(task, "task is null");

		Task<T> wrapped = new Task<>(task);
		switch ( enqueue(wrapped) ) {
			case QUEUED:
				getLogger().debug("deferred to avoid deadlock: {}", task);
				return new Invocation<>(true, wrapped.m_completion, null);
			case DRAIN:
				drain();
				break;
			case WAIT:
				break;
		}

		// 반환하기 전에는 아무도 future를 갖지 않으므로, future는 작업이 실행되어야만 완료된다.
		try {
			wrapped.m_completion.join();
		}
		catch ( CompletionException | CancellationException ignored ) {
			// 예외는 Invocation.get()에서 원래 예외로 다시 던진다.
		}
		return new Invocation<>(false, wrapped.m_completion, wrapped.m_failure);
	}

	/**
	 * dispatcher 역할을 맡아 주어진 작업을 호출한 스레드에서 실행하고, 그동안 쌓인 작업을 이어서 실행한 뒤 반환한다.
	 * <p>
	 * 다른 메소드와 달리 작업을 큐에 넣지 않는다. 호출한 스레드가 dispatcher 역할을 먼저 차지한 뒤 그 스레드에서
	 * 직접 작업을 실행한다. 따라서 작업은 항상 호출한 스레드에서 실행되고, 작업이 실행되는 동안 다른 스레드나 작업
	 * 자신이 넣은 작업은 큐에 쌓였다가 이 작업이 끝난 뒤 이 스레드가 실행한다.
	 * 초기화처럼 다른 작업과 겹치지 않게, 호출한 스레드에서 실행해야 하고, 실행 중에 들어온 작업은 끝난 뒤에
	 * 처리해야 하는 작업에 쓴다. 그 외의 일반적인 동기 호출에는 {@link #invokeOrDefer(Callable)}을 쓴다.
	 * <p>
	 * 다른 스레드가 처리 중이면 그 스레드가 dispatcher 역할을 내려놓을 때까지 기다린다.
	 * <b>dispatcher는 큐가 완전히 빌 때까지 역할을 내려놓지 않으므로, 다른 스레드들이 계속 작업을 넣으면 이
	 * 메소드는 끝없이 기다릴 수 있다(기아).</b> 기다리는 스레드에게 역할을 넘겨주는 장치는 없다. 이 메소드보다
	 * 늦게 넣은 작업이 먼저 실행될 수도 있다. 작업이 계속 들어오는 상황에서 호출해야 한다면
	 * {@link #invokeOrDefer(Callable)}을 쓴다. 그 작업은 큐에 들어가므로 앞선 작업이 끝나면 반드시 실행된다.
	 * <p>
	 * <b>이 스레드가 이미 dispatcher이면(작업 안이나, dispatcher 스레드에서 실행되는 future 후속 작업 안에서
	 * 호출한 경우) 기다리지 않고 그 자리에서 바로 실행한다.</b> 이때 작업은 현재 실행 중인 작업 안에 중첩되어
	 * 실행되므로, "한 번에 작업 하나씩"이라는 보장이 이 작업에 대해서는 깨진다. 즉 현재 작업이 끝나기 전에,
	 * 큐에 먼저 쌓여 있던 작업보다도 먼저 실행된다. 쌓인 작업은 바깥의 처리가 이어서 실행한다. 현재 작업이 끝난
	 * 뒤 순서대로 실행되기를 원하면 {@link #execute(Runnable)}나 {@link #submit(Callable)}으로 넣는다.
	 *
	 * @param task	실행할 작업.
	 * @throws NullPointerException	{@code task}가 {@code null}인 경우.
	 * @throws IllegalStateException	dispatcher가 아닌 스레드가 바깥 lock을 보유한 채 호출한 경우.
	 * 									다른 스레드가 처리 중이면 교착될 수 있으므로 항상 거부한다.
	 * @throws RuntimeInterruptedException	dispatcher 역할을 기다리는 중에 인터럽트된 경우.
	 * 										작업은 실행되지 않으며, 인터럽트 상태는 다시 설정된다.
	 * @throws RuntimeException	작업이 던진 예외. 쌓인 작업을 실행한 뒤 던진다.
	 * @throws Error	작업이 던진 오류. 쌓인 작업을 실행한 뒤 던진다.
	 */
	public void runAsDispatcher(Runnable task) {
		Preconditions.checkNotNull(task, "task is null");

		boolean acquired = acquireDispatcher();
		try {
			task.run();
		}
		finally {
			if ( acquired ) {
				drain();
			}
		}
	}

	/**
	 * 호출한 스레드가 현재 큐를 처리하는 스레드(dispatcher)인지 여부를 반환한다.
	 *
	 * @return	dispatcher이면 {@code true}.
	 */
	public boolean isDispatcherThread() {
		m_guard.lock();
		try {
			return m_dispatcher == Thread.currentThread();
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 큐에 쌓여 아직 실행을 시작하지 않은 작업의 수를 반환한다.
	 *
	 * @return	대기 중인 작업 수.
	 */
	public int getPendingTaskCount() {
		m_guard.lock();
		try {
			return m_queue.size();
		}
		finally {
			m_guard.unlock();
		}
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
	 * 결과를 future로 전달하는 작업.
	 * <p>
	 * 작업이 던진 예외를 {@link #m_failure}에도 따로 기록한다. future만으로는 작업이 던진
	 * {@link CompletionException}과 future가 감싼 예외를 구분할 수 없기 때문이다.
	 */
	private final class Task<T> implements Runnable {
		private final Callable<T> m_callable;
		private final CompletableFuture<T> m_completion = new CompletableFuture<>();
		// m_completion을 완료하기 전에 기록하므로, 완료를 확인한 스레드에게 보인다.
		private @Nullable Throwable m_failure;

		Task(Callable<T> callable) {
			m_callable = callable;
		}

		@Override
		public void run() {
			if ( m_completion.isDone() ) {
				getLogger().debug("skip a task whose future is already completed (cancelled?): {}", m_callable);
				return;
			}

			T value;
			try {
				value = m_callable.call();
			}
			catch ( Throwable e ) {
				m_failure = e;
				m_completion.completeExceptionally(e);
				return;
			}
			m_completion.complete(value);
		}

		@Override
		public String toString() {
			return m_callable.toString();
		}
	}

	/**
	 * 작업을 큐에 넣고, 호출한 스레드가 할 일을 정한다.
	 */
	private Role enqueue(Runnable task) {
		m_guard.lock();
		try {
			m_queue.add(task);

			// 큐에 작업을 넣고, 현재 dispatcher가 없다면 Role.DRAIN을 반환한다.
			Thread me = Thread.currentThread();
			if ( m_dispatcher == null ) {
				m_dispatcher = me;
				return Role.DRAIN;
			}
			// 처리 중인 스레드는 바깥 lock을 기다리고 있을 수 있다. 바깥 lock을 쥔 채 기다리면 교착된다.
			return (m_dispatcher == me || isOuterLockHeld()) ? Role.QUEUED : Role.WAIT;
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * dispatcher 역할을 맡는다. 다른 스레드가 맡고 있으면 끝날 때까지 기다린다.
	 *
	 * @return	새로 맡았으면 {@code true}, 이 스레드가 이미 맡고 있었으면 {@code false}.
	 */
	private boolean acquireDispatcher() {
		m_guard.lock();
		try {
			Thread me = Thread.currentThread();
			if ( m_dispatcher == me ) {
				return false;
			}
			Preconditions.checkState(!isOuterLockHeld(),
									"runAsDispatcher() cannot be called while holding the outer lock");
			while ( m_dispatcher != null ) {
				m_guard.awaitSignal();
			}
			m_dispatcher = me;
			return true;
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new RuntimeInterruptedException(e);
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 큐가 빌 때까지 작업을 차례로 실행한 뒤 dispatcher 역할을 내려놓는다.
	 * 큐에 들어간 작업은 모두 스스로 예외를 처리하므로, 이 메소드는 예외를 던지지 않는다.
	 */
	private void drain() {
		boolean released = false;
		try {
			while ( true ) {
				Runnable task;
				m_guard.lock();
				try {
					task = m_queue.poll();
					if ( task == null ) {
						m_dispatcher = null;
						m_guard.signalAll();
						released = true;
						return;
					}
				}
				finally {
					m_guard.unlock();
				}

				task.run();
			}
		}
		finally {
			if ( !released ) {
				// 예상하지 못한 오류로 빠져나가는 경우. 남은 작업은 다음에 작업을 넣는 스레드가 실행한다.
				m_guard.lock();
				try {
					m_dispatcher = null;
					m_guard.signalAll();
				}
				finally {
					m_guard.unlock();
				}
			}
		}
	}

	private boolean isOuterLockHeld() {
		return m_outerLock != null && m_outerLock.isHeldByCurrentThread();
	}
}
