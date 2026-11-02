package utils.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import utils.func.FOption;


/**
 * 기본형 스트림({@link IntFStream}·{@link LongFStream}·{@link FloatFStream}·{@link DoubleFStream})의 생성과 수치
 * 연산을 검증한다.
 * <p>
 * 2026-11-02 이전에는 {@code LongFStream.of(1L, 2L)} 등이 박싱 버전 {@code of(Long...)}와 모호해 컴파일되지 않았고,
 * {@code FloatFStream.sum()}이 {@code float}로 누적했으며, {@code maxValue}/{@code minValue}는 Int에만,
 * {@code from}/{@code downcast}는 Float·Double에만 있었다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class PrimitiveFStreamTest {
	@Test
	public void literal_of_calls_compile_and_work() {
		assertEquals(List.of(1, 2), IntFStream.of(1, 2).toList());
		assertEquals(List.of(1L, 2L), LongFStream.of(1L, 2L).toList());
		assertEquals(List.of(1f, 2f), FloatFStream.of(1f, 2f).toList());
		assertEquals(List.of(1d, 2d), DoubleFStream.of(1d, 2d).toList());
	}

	@Test
	public void boxed_arrays_and_iterables_use_from() {
		assertEquals(List.of(1, 2), IntFStream.from(new Integer[] { 1, 2 }).toList());
		assertEquals(List.of(1L, 2L), LongFStream.from(new Long[] { 1L, 2L }).toList());
		assertEquals(List.of(1f, 2f), FloatFStream.from(new Float[] { 1f, 2f }).toList());
		assertEquals(List.of(1d, 2d), DoubleFStream.from(new Double[] { 1d, 2d }).toList());

		assertEquals(3, IntFStream.from(List.of(1, 2)).sum());
		assertEquals(3L, LongFStream.from(List.of(1L, 2L)).sum());
		assertEquals(3d, FloatFStream.from(List.of(1f, 2f)).sum());
		assertEquals(3d, DoubleFStream.from(List.of(1d, 2d)).sum());
	}

	@Test
	public void downcast_wraps_an_existing_stream() {
		assertEquals(FOption.of(3), IntFStream.downcast(FStream.of(3, 1, 2)).maxValue());
		assertEquals(FOption.of(1L), LongFStream.downcast(FStream.of(3L, 1L, 2L)).minValue());
	}

	@Test
	public void float_sum_accumulates_in_double() {
		float[] tenths = new float[10];
		Arrays.fill(tenths, 0.1f);
		double expected = 0;
		for ( float f: tenths ) {
			expected += f;
		}
		// float로 누적하면 1.0000001192가 된다.
		assertEquals(expected, FloatFStream.of(tenths).sum(), 0d);
	}

	@Test
	public void max_and_min_are_available_for_every_type() {
		assertEquals(FOption.of(3), IntFStream.of(2, 3, 1).maxValue());
		assertEquals(FOption.of(1), IntFStream.of(2, 3, 1).minValue());
		assertEquals(FOption.of(3L), LongFStream.of(2L, 3L, 1L).maxValue());
		assertEquals(FOption.of(1L), LongFStream.of(2L, 3L, 1L).minValue());
		assertEquals(FOption.of(3f), FloatFStream.of(2f, 3f, 1f).maxValue());
		assertEquals(FOption.of(1f), FloatFStream.of(2f, 3f, 1f).minValue());
		assertEquals(FOption.of(3d), DoubleFStream.of(2d, 3d, 1d).maxValue());
		assertEquals(FOption.of(1d), DoubleFStream.of(2d, 3d, 1d).minValue());
		assertTrue(DoubleFStream.of().maxValue().isAbsent());
	}

	@Test
	public void sums_averages_and_arrays() {
		assertEquals(6L, IntFStream.of(1, 2, 3).sum());
		assertEquals(4_000_000_000L, IntFStream.of(2_000_000_000, 2_000_000_000).sum());	// int 범위를 넘음
		assertEquals(FOption.of(2d), LongFStream.of(1L, 2L, 3L).average());
		assertEquals(FOption.of(2f), FloatFStream.of(1f, 2f, 3f).average());
		assertTrue(IntFStream.of().average().isAbsent());
		assertEquals(0d, DoubleFStream.of().sum());

		assertArrayEquals(new int[] { 1, 2 }, IntFStream.of(1, 2).toArray());
		assertArrayEquals(new long[] { 1, 2 }, LongFStream.of(1, 2).toArray());
		assertArrayEquals(new float[] { 1, 2 }, FloatFStream.of(1, 2).toArray());
		assertArrayEquals(new double[] { 1, 2 }, DoubleFStream.of(1, 2).toArray());
	}

	@Test
	public void int_and_long_ranges() {
		assertEquals(List.of(2, 3, 4), IntFStream.range(2, 5).toList());
		assertEquals(List.of(2, 3, 4), FStream.range(2, 5).toList());
		assertEquals(List.of(2L, 3L), LongFStream.range(2, 4).toList());
		assertThrows(IllegalArgumentException.class, () -> IntFStream.range(5, 2));
	}

	@Test
	public void null_elements_make_numeric_operations_throw_npe() {
		assertThrows(NullPointerException.class, () -> IntFStream.from(new Integer[] { 1, null }).sum());
		assertThrows(NullPointerException.class, () -> DoubleFStream.from(Arrays.asList(1d, null)).maxValue());
	}
}
