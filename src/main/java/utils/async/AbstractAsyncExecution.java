package utils.async;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import utils.func.Lazy;
import utils.thread.ExecutorAware;


/**
 * {@link EventDrivenExecution}에 명시적인 시작({@link Startable#start()})과
 * 외부 주입 가능한 {@link Executor}를 결합한 추상 베이스.
 * <p>
 * 구체 서브클래스는 {@link Startable#start()}만 구현하면 되며, listener 관리/상태 전이/콜백 등
 * 라이프사이클 인프라는 부모인 {@link EventDrivenExecution}에서 상속받는다.
 * {@link ExecutorAware} 구현으로 컴포넌트 관리자가 작업 수행에 사용할 스레드 풀을 주입할 수 있다.
 *
 * @param <T>	연산 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractAsyncExecution<T> extends EventDrivenExecution<T>
													implements StartableExecution<T>, ExecutorAware {
	private Executor m_executor;
	
	private static Lazy<ScheduledThreadPoolExecutor> s_defaultExecutor = Lazy.of(() -> createExecutor());
	private static int s_execThreadCount = Math.max(2, Runtime.getRuntime().availableProcessors());
	private static ScheduledThreadPoolExecutor createExecutor() {
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
				s_execThreadCount, r -> {
					Thread t = new Thread(r, "Execution-Worker");
					t.setDaemon(true);
					return t;
				});
		executor.setRemoveOnCancelPolicy(true);
		return executor;
	}
	
	public ScheduledThreadPoolExecutor getThreadPoolExecutor() {
		return s_defaultExecutor.get();
	}
	
	public static void setExecutorThreadCount(int count) {
		if ( s_defaultExecutor.isLoaded() ) {
			throw new IllegalStateException("executor already created");
		}
		s_execThreadCount = count;
	}

	@Override
	public Executor getExecutor() {
		return m_executor;
	}

	@Override
	public void setExecutor(Executor executor) {
		m_executor = executor;
	}
}
