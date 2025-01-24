package utils.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncConsumer<T> implements Consumer<T> {
	private final Consumer<? super T> m_consumer;
	private final ExecutorService m_executor;
	
	public static <T> AsyncConsumer<T> singleWorker(Consumer<? super T> consumer) {
		return new AsyncConsumer<>(consumer, Executors.newSingleThreadExecutor());
	}
	
	public static <T> AsyncConsumer<T> singleWorker(Consumer<? super T> consumer,
													ExecutorService executor) {
		return new AsyncConsumer<>(consumer, executor);
	}
	
	public static <T> AsyncConsumer<T> multipleWorkers(Consumer<? super T> statelessConsumer,
														int nThreads) {
		return new AsyncConsumer<>(statelessConsumer, Executors.newFixedThreadPool(nThreads));
	}
	
	public static <T> AsyncConsumer<T> from(Consumer<? super T> statelessConsumer,
											ExecutorService executor) {
		return new AsyncConsumer<>(statelessConsumer, executor);
	}
	
	protected AsyncConsumer(Consumer<? super T> consumer, ExecutorService executor) {
		m_consumer = consumer;
		m_executor = executor;
	}
	
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
	 */
	@Override
	public final void accept(T data) {
		m_executor.execute(()->m_consumer.accept(data));
	}
	
	public final CompletableFuture<Void> schedule(T data) {
		return CompletableFuture.runAsync(()->m_consumer.accept(data), m_executor);
	}
}
