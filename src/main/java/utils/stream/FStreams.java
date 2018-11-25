package utils.stream;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;

import io.vavr.Tuple2;
import io.vavr.control.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FStreams {
	static class FilteredStream<T> implements FStream<T> {
		private final FStream<T> m_base;
		private final Predicate<? super T> m_pred;
		
		FilteredStream(FStream<T> base, Predicate<? super T> pred) {
			m_base = base;
			m_pred = pred;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public T next() {
			T next;
			while ( (next = m_base.next()) != null && !m_pred.test(next) );
			return next;
		}
		
		@Override
		public String toString() {
			return String.format("filter");
		}
	}
	
	static class MappedStream<T,S> implements FStream<S> {
		private final FStream<T> m_base;
		private final Function<? super T,? extends S> m_mapper;
		
		MappedStream(FStream<T> base, Function<? super T,? extends S> mapper) {
			m_base = base;
			m_mapper = mapper;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public S next() {
			T next = m_base.next();
			return next != null ? m_mapper.apply(next) : null;
		}
		
		@Override
		public String toString() {
			return String.format("map");
		}
	}
	
	static class MapToIntStream<T> implements IntFStream {
		private final FStream<T> m_base;
		private final Function<? super T,Integer> m_mapper;
		
		MapToIntStream(FStream<T> base, Function<? super T,Integer> mapper) {
			m_base = base;
			m_mapper = mapper;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public Integer next() {
			T next = m_base.next();
			return next != null ? m_mapper.apply(next) : null;
		}
		
		@Override
		public String toString() {
			return String.format("mapToInt");
		}
	}
	
	static class MapToLongStream<T> implements LongFStream {
		private final FStream<T> m_base;
		private final Function<? super T,Long> m_mapper;
		
		MapToLongStream(FStream<T> base, Function<? super T,Long> mapper) {
			m_base = base;
			m_mapper = mapper;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public Long next() {
			T next = m_base.next();
			return next != null ? m_mapper.apply(next) : null;
		}
		
		@Override
		public String toString() {
			return String.format("mapToLong");
		}
	}
	
	static class MapToDoubleStream<T> implements DoubleFStream {
		private final FStream<T> m_base;
		private final Function<? super T,Double> m_mapper;
		
		MapToDoubleStream(FStream<T> base, Function<? super T,Double> mapper) {
			m_base = base;
			m_mapper = mapper;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public Double next() {
			T next = m_base.next();
			return next != null ? m_mapper.apply(next) : null;
		}
		
		@Override
		public String toString() {
			return String.format("mapToDouble");
		}
	}
	
	static class PeekedStream<T> implements FStream<T> {
		private final FStream<T> m_base;
		private final Consumer<? super T> m_effect;
		
		PeekedStream(FStream<T> base, Consumer<? super T> effect) {
			m_base = base;
			m_effect = effect;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public T next() {
			T next = m_base.next();
			if ( next != null ) {
				m_effect.accept(next);
			}
			return next;
		}
		
		@Override
		public String toString() {
			return String.format("peek");
		}
	}
	
	static <T> T skipWhile(FStream<T> base, Predicate<? super T> pred) {
		T next;
		while ( (next = base.next()) != null && pred.test(next) );
		
		return next;
	}
	
	static class TakenStream<T> implements FStream<T> {
		private final FStream<T> m_src;
		private long m_remains;
		
		TakenStream(FStream<T> src, long count) {
			m_src = src;
			m_remains = count;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public T next() {
			if ( m_remains <= 0 ) {
				return null;
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
			m_src = src;
			m_remains = count;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}
	
		@Override
		public T next() {
			while ( m_remains > 0 && m_src.next() != null ) {
				--m_remains;
			}
			
			// m_src.next()가 null을 반환한 경우 처리
			return (m_remains > 0) ? null : m_src.next(); 
		}
	}
	
	static class TakeWhileStream<T,S> implements FStream<T> {
		private final FStream<T> m_src;
		private Predicate<? super T> m_pred;
		private boolean m_eos = false;
		
		TakeWhileStream(FStream<T> src, Predicate<? super T> pred) {
			
			m_src = src;
			m_pred = pred;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public T next() {
			if ( m_eos ) {
				return null;
			}
			
			T next = m_src.next();
			if ( next == null ) {
				m_eos = true;
				return null;
			}
			
			if ( m_pred.test(next) ) {
				return next;
			}
			else {
				m_eos = true;
				return null;
			}
		}
	}

	static class DropWhileStream<T,S> implements FStream<T> {
		private final FStream<T> m_src;
		private final Predicate<? super T> m_pred;
		private boolean m_started = false;
		
		DropWhileStream(FStream<T> src, Predicate<? super T> pred) {
			m_src = src;
			m_pred = pred;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public T next() {
			if ( !m_started ) {
				T next;
				while ( (next = m_src.next()) != null && m_pred.test(next) );
				m_started = true;
				
				return next;
			}
			else {
				return m_src.next();
			}
		}
	}

	static class GeneratedStream<T> implements FStream<T> {
		private final Function<? super T,? extends T> m_inc;
		private T m_next;
		
		GeneratedStream(T init, Function<? super T,? extends T> inc) {
			m_next = init;
			m_inc = inc;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public T next() {
			if ( m_next != null ) {
				T next = m_next;
				m_next = m_inc.apply(next);
				
				return next;
			}
			else {
				return null;
			}
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

		@Override
		public T next() {
			Preconditions.checkState(!m_closed, "AppendedStream is closed already");
			
			T next = m_current.next();
			if ( next == null ) {
				return (m_current == m_first) ? (m_current = m_second).next() : null;
			}
			else {
				return next;
			}
		}
	}
	
	static class UnfoldStream<T,S> implements FStream<T> {
		private final Function<? super S,Tuple2<T,S>> m_gen;
		private S m_seed;
		
		UnfoldStream(S init, Function<? super S,Tuple2<T,S>> gen) {
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
		public T next() {
			Tuple2<T,S> unfolded = m_gen.apply(m_seed);
			if ( unfolded != null ) {
				m_seed = unfolded._2;
				
				return unfolded._1;
			}
			else {
				return null;
			}
		}
	}

	static class SampledStream<T> implements FStream<T> {
		private final FStream<T> m_src;
		private final double m_ratio;
		private final Random m_randGen = new Random(System.currentTimeMillis());
		
		SampledStream(FStream<T> src, double ratio) {
			m_src = src;
			m_ratio = ratio;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public T next() {
			while ( true ) {
				T next = m_src.next();
				if ( next == null || m_randGen.nextDouble() < m_ratio ) {
					return next;
				}
			}
		}
	}
}
