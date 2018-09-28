package utils.stream;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface IntFStream extends FStream<Integer> {
	public static IntFStream of(int... values) {
		return new IntArrayStream(Arrays.stream(values).iterator());
	}
	
	public default <T> FStream<T> mapToObj(Function<Integer,? extends T> mapper) {
		Objects.requireNonNull(mapper);
		
		return new FStreamImpl<>(
			"IntFStream::mapToObj",
			() -> next().map(mapper),
			() -> close()
		);
	}

	@Override
	public default IntFStream take(long count) {
		return new TakenStream(this, count);
	}
	
	@Override
	public default Option<Integer> first() {
		try ( IntFStream taken = take(1) ) {
			return taken.next();
		}
		catch ( Exception ignored ) {
			throw new FStreamException("" + ignored);
		}
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
	
	static class IntArrayStream implements IntFStream {
		private final Iterator<Integer> m_iter;
		
		IntArrayStream(Iterator<Integer> iter) {
			Objects.requireNonNull(iter);
			
			m_iter = iter;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public Option<Integer> next() {
			return m_iter.hasNext() ? Option.some(m_iter.next()) : Option.none();
		}
	}
	
	static class RangedStream implements IntFStream {
		private int m_next;
		private final int m_end;
		private boolean m_closed;
		
		RangedStream(int start, int end, boolean closed) {
			Preconditions.checkArgument(start <= end,
										String.format("invalid range: start=%d end=%d", start, end));	
			
			m_next = start;
			m_end = end;
			m_closed = closed;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public Option<Integer> next() {
			if ( m_next < m_end ) {
				return Option.some(m_next++);
			}
			else if ( m_next == m_end && m_closed ) {
				return Option.some(m_next++);
			}
			
			return Option.none();
		}
	}
	
	static class TakenStream implements IntFStream {
		private final IntFStream m_src;
		private long m_remains;
		
		TakenStream(IntFStream src, long count) {
			Preconditions.checkArgument(count >= 0, "count < 0");
			
			m_src = src;
			m_remains = count;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public Option<Integer> next() {
			if ( m_remains <= 0 ) {
				return Option.none();
			}
			else {
				--m_remains;
				return m_src.next();
			}
		}
	}
}
