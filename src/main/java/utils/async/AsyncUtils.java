package utils.async;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.Lists;

import org.jetbrains.annotations.Nullable;

import utils.func.Nullables;

/**
 * 비동기 작업과 관련된 정적 유틸리티.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncUtils {
	private static final Duration OVERHEAD = Duration.ofMillis(10);
	
	/**
	 * 기본 생성자. 이 클래스는 정적 메소드만 제공한다.
	 */
	public AsyncUtils() { }
	
	/**
	 * 목록의 모든 원소가 조건을 만족할 때까지 주기적으로 조건을 검사한다.
	 * <p>
	 * 매 회차마다 아직 만족하지 않은 원소들에 대해 조건 검사를 {@link CompletableFuture}로 동시에 수행하고,
	 * 모든 검사가 끝날 때까지 대기한다.
	 * <p>
	 * <b>주의:</b> 현재 구현은 조건을 만족한 원소를 남은 목록에서 제거하지 못한다
	 * (원소 대신 {@link CompletableFuture} 객체로 {@code remove}를 호출한다). 따라서 목록이 비어 있지 않으면
	 * 모든 원소가 조건을 만족하더라도 정상 반환되지 않고, {@code timeout}이 주어진 경우 {@link TimeoutException}으로,
	 * 그렇지 않으면 무한히 반복한다. 또한 회차 사이의 대기 시간이 (경과 시간 - {@code pollInterval} - 10ms)로
	 * 계산되어 대부분 음수가 되므로 사실상 대기 없이 반복한다.
	 *
	 * @param <T>	원소 타입.
	 * @param list	검사 대상 원소 목록.
	 * @param pred	원소별 검사 조건.
	 * @param pollInterval	검사 주기.
	 * @param timeout	제한 시간. {@code null}이면 제한이 없다.
	 * @param exector	조건 검사를 수행할 executor. {@code null}이면 {@link CompletableFuture}의 기본 executor를 사용한다.
	 * @throws InterruptedException	대기 중 쓰레드가 인터럽트된 경우.
	 * @throws ExecutionException	조건 검사 중 예외가 발생한 경우.
	 * @throws TimeoutException	제한 시간이 지난 경우.
	 */
	public static <T> void pollUntilAllTrue(List<T> list, Predicate<T> pred, Duration pollInterval,
												@Nullable Duration timeout, @Nullable Executor exector)
		throws InterruptedException, ExecutionException, TimeoutException {
		Instant due = Nullables.map(timeout, to -> (Instant)to.addTo(Instant.now()));
		List<T> remains = Lists.newArrayList(list);
		
		while ( remains.size() > 0 ) {
			Instant started = Instant.now();
			if ( due != null && Duration.between(started, due).isNegative() ) {
				throw new TimeoutException("timeout=" + timeout);
			}
			
			List<CompletableFuture<Boolean>> jobs = Lists.newArrayList();
			for ( T element: remains ) {
				Supplier<Boolean> poll = () -> pred.test(element);
				CompletableFuture<Boolean> job = Nullables.mapOrElseGet(exector,
																	ext -> CompletableFuture.supplyAsync(poll, ext),
																	() -> CompletableFuture.supplyAsync(poll));
				jobs.add(job);
			}
			CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).get();
			for ( CompletableFuture<Boolean> job: jobs ) {
				if ( job.get() ) {
					remains.remove(job);
				}
			}
			
			Duration sleepTime = Duration.between(started, Instant.now())
										.minus(pollInterval)
										.minus(OVERHEAD);
			TimeUnit.MILLISECONDS.sleep(sleepTime.toMillis());
		}
	}
}
