package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;


/**
 * 이미 만든 스트림들을 이어 붙인 {@link FStream#concat(FStream...)}·{@link FStream#concatWith}가 결과를 닫을 때
 * 받은 스트림을 모두 닫는지 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ConcatCloseTest {
	private static FStream<Integer> tracked(AtomicBoolean closed, Integer... values) {
		return FStream.of(values).onClose(() -> closed.set(true));
	}

	@Test
	public void closing_midway_closes_the_streams_not_yet_reached() {
		AtomicBoolean first = new AtomicBoolean(false);
		AtomicBoolean second = new AtomicBoolean(false);

		assertEquals(List.of(1), FStream.concat(tracked(first, 1, 2), tracked(second, 3)).take(1).toList());
		assertTrue(first.get());
		assertTrue(second.get(), "the second stream must be closed although it was never read");
	}

	@Test
	public void closing_before_the_first_next_closes_every_stream() throws Exception {
		AtomicBoolean first = new AtomicBoolean(false);
		AtomicBoolean second = new AtomicBoolean(false);

		FStream.concat(tracked(first, 1), tracked(second, 2)).close();
		assertTrue(first.get());
		assertTrue(second.get());
	}

	@Test
	public void concat_with_closes_both_streams() throws Exception {
		AtomicBoolean self = new AtomicBoolean(false);
		AtomicBoolean follower = new AtomicBoolean(false);

		tracked(self, 1).concatWith(tracked(follower, 2)).close();
		assertTrue(self.get());
		assertTrue(follower.get());

		AtomicBoolean head = new AtomicBoolean(false);
		tracked(head, 1).concatWith(List.of(2)).close();
		assertTrue(head.get());
	}

	@Test
	public void concatenation_order_is_unchanged() {
		assertEquals(List.of(1, 2, 3, 4),
					FStream.concat(FStream.of(1, 2), FStream.of(3), FStream.of(4)).toList());
		assertEquals(List.of(1, 2, 3), FStream.of(1).concatWith(FStream.of(2)).concatWith(3).toList());
	}

	@Test
	public void flat_map_throws_after_close() throws Exception {
		AtomicBoolean source = new AtomicBoolean(false);
		FStream<Integer> stream = tracked(source, 1, 2).flatMap(FStream::of);
		stream.close();
		assertTrue(source.get());
		assertThrows(IllegalStateException.class, stream::next);
		stream.close();		// 두 번 닫아도 된다
	}
}
