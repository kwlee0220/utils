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
	
	/**
	 * 하위 클래스를 위한 기본 생성자. 작업 수행용 {@link Executor}는 설정되지 않은 상태로 시작한다.
	 */
	public AbstractAsyncExecution() { }
	
	/**
	 * 모든 {@link AbstractAsyncExecution} 객체가 공유하는 기본 스레드 풀을 반환한다.
	 * <p>
	 * 처음 호출될 때 데몬 스레드를 쓰는 {@link ScheduledThreadPoolExecutor}를 생성한다.
	 * {@link #setExecutor(Executor)}로 주입한 executor와는 무관하다.
	 *
	 * @return	공유 기본 스레드 풀.
	 */
	public ScheduledThreadPoolExecutor getThreadPoolExecutor() {
		return s_defaultExecutor.get();
	}
	
	/**
	 * 공유 기본 스레드 풀({@link #getThreadPoolExecutor()})의 스레드 수를 설정한다.
	 * <p>
	 * 기본값은 {@code max(2, 가용 프로세서 수)}이며, 스레드 풀이 생성되기 전에만 바꿀 수 있다.
	 * 인자 값은 검사하지 않는다.
	 *
	 * @param count	스레드 수.
	 * @throws IllegalStateException	스레드 풀이 이미 생성된 경우.
	 */
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
