package utils.stream;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import utils.Tuple;
import utils.Utilities;
import utils.func.FOption;
import utils.stream.FStreams.AbstractFStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface IntFStream extends FStream<Integer> {
	public static IntFStream of(int... values) {
		return new FStreamAdaptor(FStream.from(Arrays.stream(values).iterator()));
	}
	
	public default <T> FStream<T> mapToObj(Function<Integer,? extends T> mapper) {
		Objects.requireNonNull(mapper);
		
		return map(mapper);
	}

	@Override
	public default IntFStream take(long count) {
		Utilities.checkArgument(count >= 0, "count >= 0: but: " + count);
		
		return new FStreamAdaptor(take(count));
	}
	
	public default int maxValue() {
		return reduce(Integer::max);
	}
	
	public default int minValue() {
		return reduce(Integer::min);
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
	
	public default int[] toArray() {
		return Ints.toArray(toList());
	}
	
	static class RangedStream extends AbstractFStream<Integer> implements IntFStream {
		private int m_next;
		private final int m_end;
		
		RangedStream(int start, int end) {
			Preconditions.checkArgument(start <= end,
										String.format("invalid range: start=%d end=%s", start, end));	
			
			m_next = start;
			m_end = end;
		}

		@Override
		protected void closeInGuard() throws Exception { }

		@Override
		protected FOption<Integer> nextInGuard() {
			if ( m_next < m_end ) {
				return FOption.of(m_next++);
			}
			else {
				return FOption.empty();
			}
		}
	}
	
	static class FStreamAdaptor implements IntFStream {
		private final FStream<Integer> m_src;
		
		FStreamAdaptor(FStream<Integer> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Integer> next() {
			return m_src.next();
		}
	}
}
