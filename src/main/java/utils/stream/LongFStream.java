package utils.stream;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.primitives.Longs;

import utils.Utilities;
import utils.func.FOption;
import utils.func.Tuple;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface LongFStream extends FStream<Long> {
	public static LongFStream of(long... values) {
		return new FStreamAdaptor(FStream.from(Arrays.stream(values).iterator()));
	}
	
	public default <T> FStream<T> mapToObj(Function<Long,? extends T> mapper) {
		Objects.requireNonNull(mapper);
		
		return map(mapper);
	}
	
	public default long sum() {
		return foldLeft(0L, (s,v) -> s+v);
	}
	
	public default FOption<Double> average() {
		Tuple<Long,Long> state = foldLeft(Tuple.of(0L,0L),
											(a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? FOption.of(state._1 / (double)state._2)
								: FOption.empty();
	}

	@Override
	public default LongFStream take(long count) {
		Utilities.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new FStreamAdaptor(take(count));
	}
	
	public default long[] toArray() {
		return Longs.toArray(toList());
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
