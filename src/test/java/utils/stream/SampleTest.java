package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;


/**
 * {@link FStream#sample(long, double)}·{@link FStream#sample(long, double, long)}의 목표 개수를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SampleTest {
	@Test
	public void picks_exactly_round_total_times_ratio() {
		for ( int i = 0; i < 20; ++i ) {
			assertEquals(30, FStream.range(0, 100).sample(100, 0.3).count());
		}
		assertEquals(100, FStream.range(0, 100).sample(100, 1.0).count());
	}

	@Test
	public void ratio_zero_picks_nothing() {
		assertEquals(0, FStream.range(0, 100).sample(100, 0).count());
		assertEquals(0, FStream.range(0, 3).sample(3, 0.1).count());
		assertEquals(0, FStream.<Integer>empty().sample(0, 0.5).count());
	}

	@Test
	public void min_samples_sets_a_floor_capped_by_total() {
		assertEquals(1, FStream.range(0, 3).sample(3, 0.1, 1).count());
		assertEquals(3, FStream.range(0, 3).sample(3, 0.1, 10).count());
		assertEquals(30, FStream.range(0, 100).sample(100, 0.3, 1).count());
		assertEquals(0, FStream.<Integer>empty().sample(0, 0.5, 1).count());
	}

	@Test
	public void rejects_out_of_range_arguments() {
		assertThrows(IllegalArgumentException.class, () -> FStream.of(1).sample(1, 1.5));
		assertThrows(IllegalArgumentException.class, () -> FStream.of(1).sample(1, -0.1));
		assertThrows(IllegalArgumentException.class, () -> FStream.of(1).sample(-1, 0.5));
		assertThrows(IllegalArgumentException.class, () -> FStream.of(1).sample(1, 0.5, -1));
	}
}
