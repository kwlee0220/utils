package utils.stream;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.primitives.Doubles;

import utils.Utilities;
import utils.func.FOption;
import utils.func.Tuple;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface DoubleFStream extends FStream<Double> {
	public static DoubleFStream of(double... values) {
		return new ToDoubleDowncaster(FStream.from(Arrays.stream(values).iterator()));
	}
	public static DoubleFStream of(Double... values) {
		return new ToDoubleDowncaster(FStream.of(values));
	}
	public static DoubleFStream from(List<Double> values) {
		return new ToDoubleDowncaster(FStream.from(values));
	}
	public static DoubleFStream downcast(FStream<Double> strm) {
		return new ToDoubleDowncaster(strm);
	}
	
	public default <T> FStream<T> mapToObj(Function<Double,? extends T> mapper) {
		Objects.requireNonNull(mapper);
		
		return map(mapper);
	}

	@Override
	public default DoubleFStream take(long count) {
		Utilities.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new ToDoubleDowncaster(take(count));
	}
	
	public default double sum() {
		return reduce((v1,v2) -> v1+v2);
	}
	
	public default FOption<Double> average() {
		Tuple<Double,Long> state = foldLeft(Tuple.of(0d,0L), (a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? FOption.of(state._1 / state._2)
								: FOption.empty();
	}
	
	public default double[] toArray() {
		return Doubles.toArray(toList());
	}
	
	static class ToDoubleDowncaster implements DoubleFStream {
		private final FStream<Double> m_src;
		
		ToDoubleDowncaster(FStream<Double> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Double> next() {
			return m_src.next();
		}
	}
}
