package utils.stream;

import java.util.concurrent.Executor;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;


/**
 * 비동기 스트림 연산({@link FStream#mapAsync(utils.func.CheckedFunction, AsyncExecutionOptions) mapAsync},
 * {@link FStream#mapAsyncToTry(utils.func.CheckedFunction, AsyncExecutionOptions) mapAsyncToTry},
 * {@link FStream#flatMapAsync(utils.func.CheckedFunction, AsyncExecutionOptions) flatMapAsync})의 실행 옵션.
 * <p>
 * 불변 객체다. {@code withXxx()}는 이 객체를 바꾸지 않고 해당 값만 다른 새 객체를 반환한다.
 * 기본값은 {@code keepOrder=false}, {@code workerCount=max(1, 프로세서 수 - 2)}, {@code executor=null}(공용
 * {@link java.util.concurrent.ForkJoinPool})이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncExecutionOptions {
	private final boolean m_keepOrder;
	private final @Nullable Executor m_executor;
	private final int m_workerCount;

	/**
	 * 기본값으로 이루어진 옵션을 생성한다.
	 *
	 * @return	옵션 객체.
	 */
	public static AsyncExecutionOptions create() {
		return new AsyncExecutionOptions();
	}

	/**
	 * 워커 수만 지정한 옵션을 생성한다. {@code create().withWorkerCount(count)}와 같다.
	 *
	 * @param count	워커 수. 1 이상이어야 한다.
	 * @return	옵션 객체.
	 * @throws IllegalArgumentException	{@code count}가 1보다 작은 경우.
	 */
	public static AsyncExecutionOptions WORKER_COUNT(int count) {
		return new AsyncExecutionOptions().withWorkerCount(count);
	}

	/**
	 * 결과를 입력 순서대로 내도록 지정한 옵션을 생성한다. {@code create().withKeepOrder(true)}와 같다.
	 *
	 * @return	옵션 객체.
	 */
	public static AsyncExecutionOptions KEEP_ORDER() {
		return new AsyncExecutionOptions().withKeepOrder(true);
	}

	/**
	 * 결과 순서 보존 여부를 지정한 옵션을 생성한다. {@code create().withKeepOrder(flag)}와 같다.
	 *
	 * @param flag	{@code true}이면 결과를 입력 순서대로 낸다.
	 * @return	옵션 객체.
	 */
	public static AsyncExecutionOptions KEEP_ORDER(boolean flag) {
		return new AsyncExecutionOptions().withKeepOrder(flag);
	}

	private AsyncExecutionOptions() {
		this(false, null, Math.max(1, Runtime.getRuntime().availableProcessors()-2));
	}

	private AsyncExecutionOptions(boolean keepOrder, @Nullable Executor executor, int workerCount) {
		Preconditions.checkArgument(workerCount >= 1, "workerCount should be at least 1: %s", workerCount);

		m_keepOrder = keepOrder;
		m_executor = executor;
		m_workerCount = workerCount;
	}

	/**
	 * 결과를 입력 순서대로 내는지 여부를 반환한다.
	 *
	 * @return	순서를 보존하면 {@code true}.
	 */
	public boolean getKeepOrder() {
		return m_keepOrder;
	}

	/**
	 * 동시에 진행할 작업 수를 반환한다.
	 *
	 * @return	워커 수.
	 */
	public int getWorkerCount() {
		return m_workerCount;
	}

	/**
	 * 작업을 실행할 {@link Executor}를 반환한다.
	 *
	 * @return	실행기. {@code null}이면 공용 {@link java.util.concurrent.ForkJoinPool}을 쓴다.
	 */
	public @Nullable Executor getExecutor() {
		return m_executor;
	}

	/**
	 * 결과 순서 보존 여부만 바꾼 새 옵션을 반환한다.
	 *
	 * @param flag	{@code true}이면 결과를 입력 순서대로 낸다.
	 * @return	새 옵션 객체.
	 */
	public AsyncExecutionOptions withKeepOrder(boolean flag) {
		return new AsyncExecutionOptions(flag, m_executor, m_workerCount);
	}

	/**
	 * 실행기만 바꾼 새 옵션을 반환한다.
	 *
	 * @param executor	작업을 실행할 {@link Executor}. {@code null}이면 공용 {@link java.util.concurrent.ForkJoinPool}을
	 * 					쓴다.
	 * @return	새 옵션 객체.
	 */
	public AsyncExecutionOptions withExecutor(@Nullable Executor executor) {
		return new AsyncExecutionOptions(m_keepOrder, executor, m_workerCount);
	}

	/**
	 * 워커 수만 바꾼 새 옵션을 반환한다.
	 *
	 * @param workerCount	동시에 진행할 작업 수. 1 이상이어야 한다.
	 * @return	새 옵션 객체.
	 * @throws IllegalArgumentException	{@code workerCount}가 1보다 작은 경우.
	 */
	public AsyncExecutionOptions withWorkerCount(int workerCount) {
		return new AsyncExecutionOptions(m_keepOrder, m_executor, workerCount);
	}
}
