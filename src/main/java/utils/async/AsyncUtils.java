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

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncUtils {
	private static final Duration OVERHEAD = Duration.ofMillis(10);
	
	public static <T> void pollUntilAllTrue(List<T> list, Predicate<T> pred, Duration pollInterval,
												@Nullable Duration timeout, @Nullable Executor exector)
		throws InterruptedException, ExecutionException, TimeoutException {
		Instant due = FOption.map(timeout, to -> (Instant)to.addTo(Instant.now()));
		List<T> remains = Lists.newArrayList(list);
		
		while ( remains.size() > 0 ) {
			Instant started = Instant.now();
			if ( due != null && Duration.between(started, due).isNegative() ) {
				throw new TimeoutException("timeout=" + timeout);
			}
			
			List<CompletableFuture<Boolean>> jobs = Lists.newArrayList();
			for ( T element: remains ) {
				Supplier<Boolean> poll = () -> pred.test(element);
				CompletableFuture<Boolean> job = FOption.mapOrSupply(exector,
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
