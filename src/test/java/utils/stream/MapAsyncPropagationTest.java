package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 * 실패를 전파하는 {@link FStream#mapAsync}와, 실패를 결과에 담는 {@link FStream#mapAsyncToTry}의
 * {@link MappingResult}를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(10)
public class MapAsyncPropagationTest {
	private static AsyncExecutionOptions options(boolean keepOrder) {
		return AsyncExecutionOptions.create().withKeepOrder(keepOrder).withWorkerCount(2);
	}

	@Test
	public void map_async_returns_the_mapped_values() {
		assertEquals(List.of(10, 20, 30), FStream.of(1, 2, 3).mapAsync(i -> i * 10, options(true)).toList());
		assertEquals(60, FStream.of(1, 2, 3).mapAsync(i -> i * 10, options(false))
												.mapToInt(v -> v).sum());
		assertEquals(3, FStream.of(1, 2, 3).mapAsync(i -> i * 10).count());
	}

	@Test
	public void map_async_propagates_a_mapper_failure_unwrapped() {
		for ( boolean keepOrder: new boolean[] { true, false } ) {
			FStream<Integer> stream = FStream.of(1, 2, 3).mapAsync(i -> {
				if ( i == 2 ) {
					throw new IOException("bad " + i);
				}
				return i;
			}, options(keepOrder));

			IOException e = assertThrows(IOException.class, stream::toList);
			assertEquals("bad 2", e.getMessage());
		}
	}

	@Test
	public void map_async_to_try_keeps_the_input_and_the_failure() {
		List<MappingResult<Integer,Integer>> results = FStream.of(1, 2).mapAsyncToTry(i -> {
			if ( i == 2 ) {
				throw new IllegalStateException("boom");
			}
			return i * 10;
		}, options(true)).toList();

		MappingResult<Integer,Integer> ok = results.get(0);
		assertEquals(1, (int)ok.input());
		assertTrue(ok.isSuccessful());
		assertEquals(10, (int)ok.get());

		MappingResult<Integer,Integer> failed = results.get(1);
		assertEquals(2, (int)failed.input());
		assertTrue(failed.isFailed());
		assertFalse(failed.isSuccessful());
		assertEquals("boom", failed.result().getCause().getMessage());
		assertThrows(IllegalStateException.class, failed::get);
	}
}
