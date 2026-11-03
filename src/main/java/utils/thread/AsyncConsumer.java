package utils.thread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


/**
 * 전달받은 데이터를 {@link ExecutorService}에 넘겨 비동기로 처리하는 {@link Consumer}.
 * <p>
 * {@link #accept(Object)}는 데이터를 실행기에 제출만 하고 곧바로 반환하며, 실제 처리는 실행기의 쓰레드에서
 * 감싼 소비자가 수행한다. 실행기는 본 객체가 소유하는 것으로 보며 {@link #shutdown()}이 실행기를 종료시킨다.
 *
 * @param <T>	소비할 데이터 타입.
 * @author Kang-Woo Lee (ETRI)
 */
// FIXME: 삭제 대상
public class AsyncConsumer<T> implements Consumer<T> {
	private final Consumer<? super T> m_consumer;
	private final ExecutorService m_executor;
	
	/**
	 * 새로 만든 단일 쓰레드 실행기({@link Executors#newSingleThreadExecutor()})로 데이터를 처리하는
	 * 비동기 소비자를 생성한다.
	 * <p>
	 * 데이터는 {@link #accept(Object)}로 제출된 순서대로 한 번에 하나씩 처리된다.
	 *
	 * @param <T>		소비할 데이터 타입.
	 * @param consumer	실제 처리를 수행할 소비자.
	 * @return	생성된 비동기 소비자.
	 */
	public static <T> AsyncConsumer<T> singleWorker(Consumer<? super T> consumer) {
		return new AsyncConsumer<>(consumer, Executors.newSingleThreadExecutor());
	}
	
	/**
	 * 주어진 실행기로 데이터를 처리하는 비동기 소비자를 생성한다.
	 * <p>
	 * <b>주의:</b> 이름과 달리 실행기의 쓰레드 수를 확인하지 않는다. 단일 작업자로 동작하려면 호출자가
	 * 단일 쓰레드 실행기를 넘겨야 한다. {@link #from(Consumer, ExecutorService)}와 동작이 같다.
	 *
	 * @param <T>		소비할 데이터 타입.
	 * @param consumer	실제 처리를 수행할 소비자.
	 * @param executor	데이터 처리에 사용할 실행기. {@link #shutdown()} 호출 시 함께 종료된다.
	 * @return	생성된 비동기 소비자.
	 */
	public static <T> AsyncConsumer<T> singleWorker(Consumer<? super T> consumer,
													ExecutorService executor) {
		return new AsyncConsumer<>(consumer, executor);
	}
	
	/**
	 * 새로 만든 고정 크기 쓰레드 풀({@link Executors#newFixedThreadPool(int)})로 데이터를 처리하는
	 * 비동기 소비자를 생성한다.
	 * <p>
	 * 여러 쓰레드가 동시에 소비자를 호출하므로 소비자는 상태가 없거나 thread-safe해야 하며,
	 * 처리 순서는 보장되지 않는다.
	 *
	 * @param <T>				소비할 데이터 타입.
	 * @param statelessConsumer	실제 처리를 수행할 (상태 없는) 소비자.
	 * @param nThreads			작업 쓰레드 수.
	 * @return	생성된 비동기 소비자.
	 * @throws IllegalArgumentException	{@code nThreads}가 0 이하인 경우.
	 */
	public static <T> AsyncConsumer<T> multipleWorkers(Consumer<? super T> statelessConsumer,
														int nThreads) {
		return new AsyncConsumer<>(statelessConsumer, Executors.newFixedThreadPool(nThreads));
	}
	
	/**
	 * 주어진 실행기로 데이터를 처리하는 비동기 소비자를 생성한다.
	 *
	 * @param <T>				소비할 데이터 타입.
	 * @param statelessConsumer	실제 처리를 수행할 소비자. 실행기가 여러 쓰레드를 쓰면 thread-safe해야 한다.
	 * @param executor			데이터 처리에 사용할 실행기. {@link #shutdown()} 호출 시 함께 종료된다.
	 * @return	생성된 비동기 소비자.
	 */
	public static <T> AsyncConsumer<T> from(Consumer<? super T> statelessConsumer,
											ExecutorService executor) {
		return new AsyncConsumer<>(statelessConsumer, executor);
	}
	
	/**
	 * 비동기 소비자를 생성한다. 인자의 {@code null} 여부는 검사하지 않는다.
	 *
	 * @param consumer	실제 처리를 수행할 소비자.
	 * @param executor	데이터 처리에 사용할 실행기.
	 */
	protected AsyncConsumer(Consumer<? super T> consumer, ExecutorService executor) {
		m_consumer = consumer;
		m_executor = executor;
	}
	
	/**
	 * 데이터 처리에 사용하는 실행기를 반환한다.
	 *
	 * @return	실행기.
	 */
	public ExecutorService getExecutor() {
		return m_executor;
	}
	
	/**
	 * 소비자 쓰레드를 종료시킨다.
	 * <p>
	 * 실제 쓰레드가 종료되기 전에 반환될 수 있다.
	 */
	public final void shutdown() {
		m_executor.shutdown();
	}
	
	/**
	 * 소비자 쓰레드가 처리할 데이타를 추가한다.
	 * 
	 * @param data	추가할 데이타.
	 * @throws java.util.concurrent.RejectedExecutionException	실행기가 이미 종료되어 작업을 받지 않는 경우.
	 */
	@Override
	public final void accept(T data) {
		m_executor.execute(()->m_consumer.accept(data));
	}
	
	/**
	 * 소비자 쓰레드가 처리할 데이타를 추가하고, 처리 완료를 알리는 {@link CompletableFuture}를 반환한다.
	 * <p>
	 * 소비자가 예외를 던지면 반환된 future가 그 예외로 실패 완료된다.
	 *
	 * @param data	추가할 데이타.
	 * @return	데이타 처리가 끝나면 완료되는 future.
	 * @throws java.util.concurrent.RejectedExecutionException	실행기가 이미 종료되어 작업을 받지 않는 경우.
	 */
	public final CompletableFuture<Void> schedule(T data) {
		return CompletableFuture.runAsync(()->m_consumer.accept(data), m_executor);
	}
}
