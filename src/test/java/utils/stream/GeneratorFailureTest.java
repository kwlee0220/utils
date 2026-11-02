package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 * {@link FStream#generate(Generator, int)}의 생성기가 실패하는 경우를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Timeout(10)
public class GeneratorFailureTest {
	@Test
	public void an_illegal_state_from_the_generator_is_a_failure_not_an_end() {
		FStream<Integer> stream = FStream.generate(out -> {
			out.supply(1);
			throw new IllegalStateException("generation failed");
		}, 1);

		assertEquals(1, (int)stream.next().get());
		IllegalStateException e = assertThrows(IllegalStateException.class, stream::next);
		assertEquals("generation failed", e.getMessage());
	}

	@Test
	public void an_error_from_the_generator_reaches_the_consumer_instead_of_hanging() {
		FStream<Integer> stream = FStream.generate(out -> { throw new AssertionError("fatal"); }, 1);

		AssertionError e = assertThrows(AssertionError.class, stream::toList);
		assertEquals("fatal", e.getMessage());
	}

	@Test
	public void closing_the_consumer_still_ends_the_generator_normally() throws Exception {
		FStream<Integer> stream = FStream.generate(out -> {
			for ( int i = 0; ; ++i ) {
				out.supply(i);
			}
		}, 1);

		assertEquals(List.of(0, 1), stream.take(2).toList());
	}
}
