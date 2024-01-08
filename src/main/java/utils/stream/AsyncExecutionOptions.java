package utils.stream;

import static utils.Utilities.checkArgument;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public class AsyncExecutionOptions {
	private final boolean m_keepOrder;
	@Nullable private final Executor m_executor;
	private final int m_workerCount;
	private final long m_timeoutMillis;
	
	public static AsyncExecutionOptions create() {
		return new AsyncExecutionOptions();
	}
	public static AsyncExecutionOptions TIMEOUT(long millis, TimeUnit unit) {
		return new AsyncExecutionOptions().setTimeout(millis, unit);
	}
	public static AsyncExecutionOptions WORKER_COUNT(int count) {
		return new AsyncExecutionOptions().setWorkerCount(count);
	}
	public static AsyncExecutionOptions KEEP_ORDER() {
		return new AsyncExecutionOptions().setKeepOrder(true);
	}
	public static AsyncExecutionOptions KEEP_ORDER(boolean flag) {
		return new AsyncExecutionOptions().setKeepOrder(flag);
	}
	
	private AsyncExecutionOptions() {
		this(false, -1L, null,  Math.max(1, Runtime.getRuntime().availableProcessors()-2));
	}
	
	private AsyncExecutionOptions(boolean keepOrder, long timeoutMillis, @Nullable Executor executor,
									int workerCount) {
		checkArgument(workerCount >= 1);
		
		m_keepOrder = keepOrder;
		m_timeoutMillis = timeoutMillis;
		m_executor = executor;
		m_workerCount = workerCount;
	}
	
	public boolean getKeepOrder() {
		return m_keepOrder;
	}
	
	public int getWorkerCount() {
		return m_workerCount;
	}
	
	public long getTimeoutMillis() {
		return m_timeoutMillis;
	}
	
	public Executor getExecutor() {
		return m_executor;
	}
	
	public AsyncExecutionOptions setKeepOrder(boolean flag) {
		return new AsyncExecutionOptions(flag, m_timeoutMillis, m_executor, m_workerCount);
	}
	
	public AsyncExecutionOptions setTimeout(long timeout, TimeUnit unit) {
		long timeoutMillis = unit.toMillis(timeout);
		return new AsyncExecutionOptions(m_keepOrder, timeoutMillis, m_executor, m_workerCount);
	}
	
	public AsyncExecutionOptions setExecutor(Executor executor) {
		return new AsyncExecutionOptions(m_keepOrder, m_timeoutMillis, executor, m_workerCount);
	}
	
	public AsyncExecutionOptions setWorkerCount(int workerCount) {
		return new AsyncExecutionOptions(m_keepOrder, m_timeoutMillis, m_executor, workerCount);
	}
}