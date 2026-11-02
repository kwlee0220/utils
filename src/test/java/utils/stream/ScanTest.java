package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;


/**
 * {@link FStream#scan(Object, java.util.function.BiFunction)}을 검증한다.
 * <p>
 * 이 연산은 결과가 틀리던 {@code foldLeak}을 대체한다({@code [1,2,3,4].foldLeak(0, ...)}이 {@code [1,1,2,4]}를 냈다).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ScanTest {
	@Test
	public void emits_each_running_accumulation_without_the_initial_value() {
		List<Integer> sums = FStream.of(1, 2, 3, 4).scan(0, Integer::sum).toList();
		assertEquals(Arrays.asList(1, 3, 6, 10), sums);
	}

	@Test
	public void applies_the_initial_value_to_the_first_element() {
		List<Integer> sums = FStream.of(1, 2, 3).scan(100, Integer::sum).toList();
		assertEquals(Arrays.asList(101, 103, 106), sums);
	}

	@Test
	public void accumulator_type_may_differ_from_the_element_type() {
		List<String> joined = FStream.of(1, 2, 3).scan("", (s, n) -> s + n).toList();
		assertEquals(Arrays.asList("1", "12", "123"), joined);
	}

	@Test
	public void a_null_accumulation_is_carried_over_instead_of_restarting() {
		// 처음으로 돌아가면 "c" 단계가 초기값 "x"로 시작해 "xc"가 된다.
		List<String> out = FStream.of("a", "b", "c")
									.scan("x", (s, t) -> t.equals("b") ? null : String.valueOf(s) + t)
									.toList();
		assertEquals(Arrays.asList("xa", null, "nullc"), out);
	}

	@Test
	public void an_empty_stream_yields_an_empty_stream() {
		assertEquals(Collections.emptyList(), FStream.<Integer>empty().scan(0, Integer::sum).toList());
	}

	@Test
	public void closing_the_result_closes_the_source() throws Exception {
		AtomicBoolean closed = new AtomicBoolean(false);
		FStream<Integer> scanned = FStream.of(1, 2, 3).onClose(() -> closed.set(true)).scan(0, Integer::sum);
		scanned.close();
		assertTrue(closed.get());
	}

	@Test
	public void null_folder_is_rejected() {
		assertThrows(IllegalArgumentException.class, () -> FStream.of(1).scan(0, null));
	}
}
