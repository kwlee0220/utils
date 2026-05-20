package utils.stream;

import java.util.Arrays;
import java.util.function.Function;

import com.google.common.primitives.Longs;

import utils.Preconditions;
import utils.Tuple;
import utils.func.FOption;
import utils.stream.FStreams.AbstractFStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface LongFStream extends FStream<Long> {
	public static LongFStream of(long... values) {
		return new FStreamAdaptor(FStream.from(Arrays.stream(values).iterator()));
	}
	public static LongFStream of(Long... values) {
		return new FStreamAdaptor(FStream.from(Arrays.stream(values).iterator()));
	}

	/**
	 * 주어진 long 값 ({@code start})부터 시작해서 1씩 증가하여 주어진 long 값 {@code end} -1 까지의
	 * 값을 반환하는 {@link LongFStream}을 반환한다.
	 * <p>
	 * 스트림에는 {@code start}는 포함하지만 {@code end}는 포함하지 않는다.
	 *
	 * @param start	시작 값.
	 * @param end	종료 바로 다음 값.
	 * @return	스트림 객체.
	 */
	public static LongFStream range(long start, long end) {
		return new RangedStream(start, end);
	}
	
	public default <T> FStream<T> mapToObj(Function<Long,? extends T> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper must not be null");
		
		return map(mapper);
	}
	
	public default long sum() {
		return fold(0L, (s,v) -> s+v);
	}
	
	public default FOption<Double> average() {
		Tuple<Long,Long> state = fold(Tuple.of(0L,0L),
											(a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? FOption.of(state._1 / (double)state._2)
								: FOption.empty();
	}

	@Override
	public default LongFStream take(long count) {
		Preconditions.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new FStreamAdaptor(FStream.super.take(count));
	}
	
	public default long[] toArray() {
		return Longs.toArray(toList());
	}
	
	static class RangedStream extends AbstractFStream<Long> implements LongFStream {
		private long m_next;
		private final long m_end;

		RangedStream(long start, long end) {
			Preconditions.checkArgument(start <= end,
									String.format("invalid range: start=%d end=%d", start, end));

			m_next = start;
			m_end = end;
		}

		@Override
		protected void closeInGuard() throws Exception { }

		@Override
		protected FOption<Long> nextInGuard() {
			if ( m_next < m_end ) {
				return FOption.of(m_next++);
			}
			else {
				return FOption.empty();
			}
		}
	}

	static class FStreamAdaptor implements LongFStream {
		private final FStream<Long> m_src;
		
		FStreamAdaptor(FStream<Long> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Long> next() {
			return m_src.next();
		}
	}
}
