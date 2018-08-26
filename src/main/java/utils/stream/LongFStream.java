package utils.stream;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Longs;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface LongFStream extends FStream<Long> {
	public static LongFStream of(long... values) {
		return new LongArrayStream(Arrays.stream(values).iterator());
	}
	
	public default <T> FStream<T> mapToObj(Function<Long,? extends T> mapper) {
		Objects.requireNonNull(mapper);
		
		return new FStreamImpl<>(
			"LongFStream::mapToObj",
			() -> next().map(mapper),
			() -> close()
		);
	}
	
	public default long sum() {
		return foldLeft(0L, (s,v) -> s+v);
	}
	
	public default Option<Double> average() {
		Tuple2<Long,Long> state = foldLeft(Tuple.of(0L,0L),
											(a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? Option.some(state._1 / (double)state._2)
								: Option.none();
	}
	
	public default long[] toArray() {
		return Longs.toArray(toList());
	}
	
	static class LongArrayStream implements LongFStream {
		private final Iterator<Long> m_iter;
		
		LongArrayStream(Iterator<Long> iter) {
			Objects.requireNonNull(iter);
			
			m_iter = iter;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public Option<Long> next() {
			return m_iter.hasNext() ? Option.some(m_iter.next()) : Option.none();
		}
	}
}
