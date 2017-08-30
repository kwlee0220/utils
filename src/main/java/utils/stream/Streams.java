package utils.stream;

import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;

import io.vavr.Tuple2;
import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Streams {
	@SuppressWarnings("rawtypes")
	static final Stream EMPTY = () -> { return Option.none(); };
	
	static <T> Option<T> skipWhile(Stream<T> stream, Predicate<T> pred) {
		Option<T> next;
		while ( (next = stream.next()).filter(pred).isDefined() );
		return next;
	}
	
	static class TakenStream<T> implements Stream<T> {
		private final Stream<T> m_src;
		private long m_remains;
		
		TakenStream(Stream<T> src, long count) {
			m_src = src;
			m_remains = count;
		}

		@Override
		public Option<T> next() {
			if ( m_remains <= 0 ) {
				return Option.none();
			}
			else {
				--m_remains;
				return m_src.next();
			}
		}
	}

	static class DroppedStream<T> implements Stream<T> {
		private final Stream<T> m_src;
		private long m_remains;
		
		DroppedStream(Stream<T> src, long count) {
			m_src = src;
			m_remains = count;
		}
	
		@Override
		public Option<T> next() {
			while ( m_remains > 0 ) {
				m_src.next();
				--m_remains;
			}
			
			return m_src.next();
		}
	}
	
	static class TakeWhileStream<T,S> implements Stream<T> {
		private final Stream<T> m_src;
		private Predicate<T> m_pred;
		private Option<T> m_last = null;
		
		TakeWhileStream(Stream<T> src, Predicate<T> pred) {
			m_src = src;
			m_pred = pred;
		}

		@Override
		public Option<T> next() {
			if ( m_last == null ) {
				Option<T> next = m_src.next().filter(m_pred);
				next.onEmpty(() -> m_last = next);
				return next;
			}
			else {
				return m_last;
			}
		}
	}

	static class DropWhileStream<T,S> implements Stream<T> {
		private final Stream<T> m_src;
		private Predicate<T> m_pred;
		
		DropWhileStream(Stream<T> src, Predicate<T> pred) {
			m_src = src;
			m_pred = pred;
		}

		@Override
		public Option<T> next() {
			if ( m_pred != null) {
				Option<T> next = Streams.skipWhile(m_src, m_pred);
				
				m_pred = null;
				return next;
			}
			else {
				return m_src.next();
			}
		}
	}

	static class GeneratedStream<T> implements Stream<T> {
		private final Function<T,T> m_inc;
		private Option<T> m_next;
		
		GeneratedStream(T init, Function<T,T> inc) {
			m_next = Option.of(init);
			m_inc = inc;
		}

		@Override
		public Option<T> next() {
			Option<T> next = m_next;
			m_next = m_next.map(m_inc);
			return next;
		}
	}
	
	static class RangedStream implements Stream<Integer> {
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
	
	static class AppendedStream<T,S> implements Stream<T> {
		private final Stream<T> m_first;
		private final Stream<T> m_second;
		private Stream<T> m_current;
		
		AppendedStream(Stream<T> first, Stream<T> second) {
			m_first = first;
			m_second = second;
			m_current = m_first;
		}

		@Override
		public Option<T> next() {
			Option<T> next = m_current.next();
			if ( next.isEmpty() ) {
				return (m_current == m_first) ? (m_current = m_second).next() : next;
			}
			else {
				return next;
			}
		}
	}
	
	static class UnfoldStream<T,S> implements Stream<T> {
		private final Function<S,Option<Tuple2<T,S>>> m_gen;
		private Option<S> m_token;
		
		UnfoldStream(S init, Function<S,Option<Tuple2<T,S>>> gen) {
			m_token = Option.of(init);
			m_gen = gen;
		}

		@Override
		public Option<T> next() {
			return m_token.flatMap(m_gen).map(tup -> tup.apply((t,s) -> {
				m_token = Option.some(s);
				return t;
			}));
		}
	}

}
