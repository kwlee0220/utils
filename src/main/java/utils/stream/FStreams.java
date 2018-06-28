package utils.stream;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;

import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FStreams {
	static <T> Option<T> skipWhile(FStream<T> stream, Predicate<? super T> pred) {
		Option<T> next;
		while ( (next = stream.next()).filter(pred).isDefined() );
		return next;
	}
	
	static class TakenStream<T> implements FStream<T> {
		private final FStream<T> m_src;
		private long m_remains;
		
		TakenStream(FStream<T> src, long count) {
			Preconditions.checkArgument(count >= 0, "count < 0");
			
			m_src = src;
			m_remains = count;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
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

	static class DroppedStream<T> implements FStream<T> {
		private final FStream<T> m_src;
		private long m_remains;
		
		DroppedStream(FStream<T> src, long count) {
			Preconditions.checkArgument(count >= 0, "count < 0");
			
			m_src = src;
			m_remains = count;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
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
	
	static class TakeWhileStream<T,S> implements FStream<T> {
		private final FStream<T> m_src;
		private Predicate<? super T> m_pred;
		private Option<T> m_last = null;
		
		TakeWhileStream(FStream<T> src, Predicate<? super T> pred) {
			Preconditions.checkNotNull(pred);
			
			m_src = src;
			m_pred = pred;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
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

	static class DropWhileStream<T,S> implements FStream<T> {
		private final FStream<T> m_src;
		private Predicate<? super T> m_pred;
		
		DropWhileStream(FStream<T> src, Predicate<? super T> pred) {
			Preconditions.checkNotNull(pred);
			
			m_src = src;
			m_pred = pred;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public Option<T> next() {
			if ( m_pred != null) {
				Option<T> next = FStreams.skipWhile(m_src, m_pred);
				
				m_pred = null;
				return next;
			}
			else {
				return m_src.next();
			}
		}
	}

	static class GeneratedStream<T> implements FStream<T> {
		private final Function<? super T,? extends T> m_inc;
		private Option<T> m_next;
		
		GeneratedStream(T init, Function<? super T,? extends T> inc) {
			m_next = Option.of(init);
			m_inc = inc;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public Option<T> next() {
			Option<T> next = m_next;
			m_next = m_next.map(m_inc);
			return next;
		}
	}
	
	static class RangedStream implements FStream<Integer> {
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
	
	static class AppendedStream<T,S> implements FStream<T> {
		private final FStream<? extends T> m_first;
		private final FStream<? extends T> m_second;
		private FStream<? extends T> m_current;
		private boolean m_closed = false;
		
		AppendedStream(FStream<? extends T> first, FStream<? extends T> second) {
			m_first = first;
			m_second = second;
			m_current = m_first;
		}

		@Override
		public void close() throws Exception {
			if ( !m_closed ) {
				Try<Void> ret1 = Try.run(() -> m_first.close());
				Try<Void> ret2 = Try.run(() -> m_second.close());
				m_closed = true;
				
				ret1.get();
				ret2.get();
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public Option<T> next() {
			Preconditions.checkState(!m_closed, "AppendedStream is closed already");
			
			Option<T> next = (Option<T>)m_current.next();
			if ( next.isEmpty() ) {
				return (m_current == m_first) ? (Option<T>)(m_current = m_second).next() : next;
			}
			else {
				return next;
			}
		}
	}
	
	static class UnfoldStream<T,S> implements FStream<T> {
		private final Function<? super S,Option<Tuple2<T,S>>> m_gen;
		private S m_seed;
		
		UnfoldStream(S init, Function<? super S,Option<Tuple2<T,S>>> gen) {
			m_seed = init;
			m_gen = gen;
		}

		@Override
		public void close() throws Exception {
			if ( m_seed instanceof AutoCloseable ) {
				((AutoCloseable)m_seed).close();
			}
		}

		@Override
		public Option<T> next() {
			return m_gen.apply(m_seed)
						.peek(t -> m_seed = t._2)
						.map(t -> t._1);
		}
	}
	
	static class IntArrayStream implements IntFStream {
		private final Iterator<Integer> m_iter;
		
		IntArrayStream(Iterator<Integer> iter) {
			Preconditions.checkNotNull(iter);
			
			m_iter = iter;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public Option<Integer> next() {
			return m_iter.hasNext() ? Option.some(m_iter.next()) : Option.none();
		}
	}
}
