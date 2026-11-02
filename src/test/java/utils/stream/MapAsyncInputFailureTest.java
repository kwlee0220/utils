package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import utils.func.FOption;


/**
 * 비동기 매핑({@code mapAsyncToTry}·{@code flatMapAsync})에서 입력 스트림이 실패하는 경우를 검증한다.
 * <p>
 * 입력 실패는 특정 원소에 묶을 수 없으므로, 이미 나온 결과를 넘긴 뒤 스트림 전체의 실패로(감싸지 않은 원래 예외로)
 * 전파되어야 한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(10)
public class MapAsyncInputFailureTest {
	private static AsyncExecutionOptions oneWorker(boolean keepOrder) {
		return AsyncExecutionOptions.create().withKeepOrder(keepOrder).withWorkerCount(1);
	}

	// 두 번째 원소를 꺼낼 때 주어진 예외를 던지는 입력 스트림.
	private static FStream<Integer> failingAtSecond(RuntimeException error) {
		return FStream.of(1, 2, 3).map(i -> {
			if ( i == 2 ) {
				throw error;
			}
			return i;
		});
	}

	@Test
	public void unordered_map_async_propagates_an_input_failure_instead_of_hanging() {
		FStream<MappingResult<Integer,Integer>> stream
			= failingAtSecond(new IllegalArgumentException("boom"))
				.mapAsyncToTry(i -> i * 10, oneWorker(false));
		FOption<MappingResult<Integer,Integer>> first = stream.next();
		assertEquals(10, (int)first.get().result().get());

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, stream::next);
		assertEquals("boom", e.getMessage());
	}

	@Test
	public void unordered_map_async_does_not_mistake_an_input_illegal_state_for_close() {
		FStream<MappingResult<Integer,Integer>> stream
			= failingAtSecond(new IllegalStateException("bad input"))
				.mapAsyncToTry(i -> i * 10, oneWorker(false));
		IllegalStateException e = assertThrows(IllegalStateException.class, stream::toList);
		assertEquals("bad input", e.getMessage());
	}

	@Test
	public void ordered_map_async_delivers_the_current_result_before_the_input_failure() {
		FStream<MappingResult<Integer,Integer>> stream
			= failingAtSecond(new IllegalArgumentException("boom"))
				.mapAsyncToTry(i -> i * 10, oneWorker(true));
		assertEquals(10, (int)stream.next().get().result().get());
		assertThrows(IllegalArgumentException.class, stream::next);
	}

	@Test
	public void ordered_flat_map_async_does_not_leak_the_sub_stream_when_the_input_fails() {
		AtomicBoolean closed = new AtomicBoolean(false);
		FStream<Integer> stream = failingAtSecond(new IllegalArgumentException("boom"))
									.flatMapAsync(i -> FStream.of(i).onClose(() -> closed.set(true)),
												oneWorker(true));

		// 첫 하위 스트림은 소비자에게 넘어와 끝까지 읽히고 닫힌 뒤, 입력 실패가 전파된다.
		assertEquals(1, (int)stream.next().get());
		assertThrows(IllegalArgumentException.class, stream::next);
		assertTrue(closed.get(), "the sub-stream created before the input failure must be closed");
	}
}
