package utils.stream;

import java.util.Objects;
import java.util.function.Function;

import com.google.common.primitives.Booleans;

import utils.Utilities;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface BooleanFStream extends FStream<Boolean> {
	public static BooleanFStream of(boolean... values) {
		return new FStreamAdaptor(FStream.from(Booleans.asList(values)));
	}
	
	public default <T> FStream<T> mapToObj(Function<Boolean,? extends T> mapper) {
		Objects.requireNonNull(mapper);
		
		return map(mapper);
	}
	
	public default boolean andAll() {
		return reduce((v1,v2) -> Boolean.logicalAnd(v1, v2));
	}
	
	public default boolean orAll() {
		return reduce((v1,v2) -> Boolean.logicalOr(v1, v2));
	}

	@Override
	public default BooleanFStream take(long count) {
		Utilities.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new FStreamAdaptor(take(count));
	}
	
	public default boolean[] toArray() {
		return Booleans.toArray(toList());
	}
	
	static class FStreamAdaptor implements BooleanFStream {
		private final FStream<Boolean> m_src;
		
		FStreamAdaptor(FStream<Boolean> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Boolean> next() {
			return m_src.next();
		}
	}
}
