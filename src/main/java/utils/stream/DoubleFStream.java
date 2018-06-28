package utils.stream;

import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface DoubleFStream extends FStream<Double> {
	public default <T> FStream<T> mapToObj(Function<Double,? extends T> mapper) {
		Preconditions.checkNotNull(mapper);
		
		return new FStreamImpl<>(
			"DoubleFStream::mapToObj",
			() -> next().map(mapper),
			() -> close()
		);
	}
	
	public default double sum() {
		return reduce((v1,v2) -> v1+v2);
	}
	
	public default Option<Double> average() {
		Tuple2<Double,Long> state = foldLeft(Tuple.of(0d,0L),
											(a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? Option.some(state._1 / state._2)
								: Option.none();
	}
	
	public default double[] toArray() {
		return Doubles.toArray(toList());
	}
}
