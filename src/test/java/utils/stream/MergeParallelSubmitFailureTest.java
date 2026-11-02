package utils.stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 * {@link FStream#mergeParallel}의 워커 작업을 실행기가 거부하는 경우를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(10)
public class MergeParallelSubmitFailureTest {
	@Test
	public void a_rejected_worker_fails_the_merge_and_closes_its_sub_stream() {
		Executor rejecting = task -> { throw new RejectedExecutionException("rejected"); };
		AtomicBoolean closed = new AtomicBoolean(false);
		FStream<FStream<Integer>> fact = FStream.of(FStream.of(1, 2).onClose(() -> closed.set(true)));

		assertThrows(RejectedExecutionException.class,
					() -> FStream.mergeParallel(fact, 1, rejecting).toList());
		assertTrue(closed.get(), "the sub-stream that could not be started must be closed");
	}
}
