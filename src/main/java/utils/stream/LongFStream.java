package utils.stream;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.primitives.Longs;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import utils.stream.LongFStreamImpl.LongArrayStream;
import utils.stream.LongFStreamImpl.TakenLongStream;

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

	@Override
	public default LongFStream take(long count) {
		return new TakenLongStream(this, count);
	}
	
	@Override
	public default Option<Long> first() {
		try ( LongFStream taken = take(1) ) {
			return taken.next();
		}
		catch ( Exception ignored ) {
			throw new FStreamException("" + ignored);
		}
	}
	
	public default long[] toArray() {
		return Longs.toArray(toList());
	}
}
