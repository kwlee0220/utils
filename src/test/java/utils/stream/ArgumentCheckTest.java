package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import utils.KeyValue;


/**
 * 인자 검증 시점과 오류 메시지를 검증한다.
 * <p>
 * 2026-11-02 이전에는 {@code buffer(0, 1)}이 내부 클래스의 메시지로 실패했고, {@code take(-1)} 등의 메시지가
 * {@code "count >= 0, but {} [-1]"}처럼 깨졌으며, {@code match(null)}·{@code foldByKey}·{@code collectByKey}의
 * {@code null} 인자는 첫 원소를 처리할 때에야(빈 스트림이면 아예) 드러났다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ArgumentCheckTest {
	@Test
	public void buffer_rejects_a_non_positive_count_at_the_call() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
													() -> FStream.of(1).buffer(0, 1));
		assertEquals("count > 0, but: 0", e.getMessage());
	}

	@Test
	public void count_messages_are_formatted() {
		List<Runnable> calls = List.of(() -> FStream.of(1).take(-1), () -> FStream.of(1).drop(-1),
										() -> FStream.of(1).dropLast(-1), () -> FStream.of(1).takeLast(-1),
										() -> FStream.repeat(1, -1));
		for ( Runnable call: calls ) {
			IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call::run);
			assertEquals("count >= 0, but: -1", e.getMessage());
		}
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
													() -> IntFStream.range(3, 1));
		assertEquals("invalid range: start=3 end=1", e.getMessage());
	}

	@Test
	public void null_arguments_are_rejected_at_the_call_even_for_empty_streams() {
		assertThrows(IllegalArgumentException.class,
					() -> KeyValueFStream.from(java.util.Map.<String,Integer>of()).match(null));
		assertThrows(IllegalArgumentException.class,
					() -> FStream.<String>empty().lookup((java.util.Map<String,String>)null));
		assertThrows(IllegalArgumentException.class,
					() -> FStream.<String>empty().foldByKey(null, k -> 0, (s, v) -> s));
		assertThrows(IllegalArgumentException.class,
					() -> FStream.<String>empty().collectByKey(s -> s, null, (a, v) -> { }));
	}

	@Test
	public void a_null_mapped_key_fails_like_key_value_map_key() {
		KeyValueFStream<String,Integer> kvs = KeyValueFStream.of(KeyValue.of("a", 1));
		NullPointerException e = assertThrows(NullPointerException.class,
												() -> kvs.mapKey(k -> null).toList());
		assertEquals("mapped key is null", e.getMessage());

		KeyValueFStream<String,Integer> kvs2 = KeyValueFStream.of(KeyValue.of("a", 1));
		assertThrows(NullPointerException.class, () -> kvs2.mapKeyWithValue((k, v) -> null).toList());
		assertThrows(NullPointerException.class, () -> KeyValue.of("a", 1).mapKey(k -> null));
	}

	@Test
	public void valid_arguments_still_work() {
		// 마지막에는 길이가 모자란 윈도우도 낸다.
		assertEquals(List.of(List.of(1, 2), List.of(2, 3), List.of(3)),
					FStream.of(1, 2, 3).buffer(2, 1).toList());
		assertTrue(FStream.of(1).take(0).next().isAbsent());
		assertFalse(KeyValueFStream.of(KeyValue.of("a", 1)).match(java.util.Map.of("a", 2))
								.toList().isEmpty());
	}
}
