package utils.stream;

import java.util.Arrays;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import utils.stream.FStreams.IntArrayStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface IntFStream extends FStream<Integer> {
	public static IntFStream of(int... values) {
		return new IntArrayStream(Arrays.stream(values).iterator());
	}
	
	public default <T> FStream<T> mapToObj(Function<Integer,? extends T> mapper) {
		Preconditions.checkNotNull(mapper);
		
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
	
	public default int[] toArray() {
		return Ints.toArray(toList());
	}
}
