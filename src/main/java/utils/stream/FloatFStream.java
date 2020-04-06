package utils.stream;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.primitives.Floats;

import utils.Utilities;
import utils.func.FOption;
import utils.func.Tuple;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FloatFStream extends FStream<Float> {
	public static FloatFStream of(float... values) {
		return new ToFloatDowncaster(FStream.from(Floats.asList(values)));
	}
	public static FloatFStream of(Float... values) {
		return new ToFloatDowncaster(FStream.of(values));
	}
	public static FloatFStream from(List<Float> values) {
		return new ToFloatDowncaster(FStream.from(values));
	}
	public static FloatFStream downcast(FStream<Float> strm) {
		return new ToFloatDowncaster(strm);
	}
	
	public default <T> FStream<T> mapToObj(Function<Float,? extends T> mapper) {
		Objects.requireNonNull(mapper);
		
		return map(mapper);
	}

	@Override
	public default FloatFStream take(long count) {
		Utilities.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new ToFloatDowncaster(take(count));
	}
	
	public default double sum() {
		return reduce((v1,v2) -> v1+v2);
	}
	
	public default FOption<Float> average() {
		Tuple<Double,Long> state = foldLeft(Tuple.of(0d,0L),
											(a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? FOption.of(state._1 / state._2).map(Double::floatValue)
								: FOption.empty();
	}
	
	public default float[] toArray() {
		return Floats.toArray(toList());
	}
	
	static class ToFloatDowncaster implements FloatFStream {
		private final FStream<Float> m_src;
		
		ToFloatDowncaster(FStream<Float> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Float> next() {
			return m_src.next();
		}
	}
}
