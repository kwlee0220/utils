package utils.stream;

import java.util.Iterator;
import java.util.Random;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.vavr.Tuple2;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FStreams {
	static class EmptyStream<T> implements FStream<T> {
		@Override
		public void close() throws Exception { }

		@Override
		public FOption<T> next() {
			return FOption.empty();
		}
		
		@Override
		public String toString() {
			return "empty";
		}
	}
	
	static class IteratorStream<T> implements FStream<T> {
		private final Iterator<? extends T> m_iter;
		
		IteratorStream(Iterator<? extends T> iter) {
			m_iter = iter;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public FOption<T> next() {
			return m_iter.hasNext() ? FOption.of(m_iter.next()) : FOption.empty();
		}
	}
	
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
		public FOption<T> next() {
			FOption<T> next;
			while ( (next = m_base.next()).isPresent() && !m_pred.test(next.get()) );
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
		public FOption<S> next() {
			return m_base.next().map(m_mapper);
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
		public FOption<Integer> next() {
			return m_base.next().map(m_mapper);
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
		public FOption<Long> next() {
			return m_base.next().map(m_mapper);
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
		public FOption<Double> next() {
			return m_base.next().map(m_mapper);
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
		public FOption<T> next() {
			return m_base.next().ifPresent(m_effect);
		}
		
		@Override
		public String toString() {
			return String.format("peek");
		}
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
		public FOption<T> next() {
			if ( m_remains <= 0 ) {
				return FOption.empty();
			}
			else {
				--m_remains;
				return m_src.next();
			}
		}
	}

	static class DroppedStream<T> implements FStream<T> {
		private final FStream<T> m_src;
		private final long m_count;
		private boolean m_dropped = false;
		
		DroppedStream(FStream<T> src, long count) {
			m_src = src;
			m_count = count;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}
	
		@Override
		public FOption<T> next() {
			if ( !m_dropped ) {
				m_dropped = true;
				for ( int i =0; i < m_count; ++i ) {
					if ( m_src.next().isAbsent() ) {
						return FOption.empty();
					}
				}
			}
			
			return m_src.next();
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
		public FOption<T> next() {
			if ( m_eos ) {
				return FOption.empty();
			}
			
			FOption<T> next = m_src.next();
			if ( next.isAbsent() ) {
				m_eos = true;
				return FOption.empty();
			}
			
			if ( m_pred.test(next.get()) ) {
				return next;
			}
			else {
				m_eos = true;
				return FOption.empty();
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
		public FOption<T> next() {
			if ( !m_started ) {
				FOption<T> next;
				while ( (next = m_src.next()).isPresent() && m_pred.test(next.get()) );
				m_started = true;
				
				return next;
			}
			else {
				return m_src.next();
			}
		}
	}
	
	static class ScannedStream<T> implements FStream<T> {
		private final FStream<T> m_src;
		private final BinaryOperator<T> m_combine;
		private FOption<T> m_current = null;
		
		ScannedStream(FStream<T> src, BinaryOperator<T> combine) {
			m_src = src;
			m_combine = combine;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<T> next() {
			if ( m_current == null ) {	// 첫번째 call인 경우.
				return m_current = m_src.next();
			}
			else {
				FOption<T> next = m_src.next();
				if ( next.isPresent() ) {
					return m_current = FOption.of(m_combine.apply(m_current.get(), next.get()));
				}
				else {
					return FOption.empty();
				}
			}
		}
	}

	static class GeneratedStream<T> implements FStream<T> {
		private final Function<? super T,? extends T> m_inc;
		private T m_next;
		private volatile boolean m_closed = false;
		
		GeneratedStream(T init, Function<? super T,? extends T> inc) {
			m_next = init;
			m_inc = inc;
		}

		@Override
		public void close() throws Exception {
			m_closed = true;
		}

		@Override
		public FOption<T> next() {
			if ( m_closed ) {
				return FOption.empty();
			}
			
			T next = m_next;
			m_next = m_inc.apply(next);
			
			return FOption.of(next);
		}
	}
	
	static class UnfoldStream<T,S> implements FStream<T> {
		private final Function<S,Tuple2<T,S>> m_gen;
		private S m_seed;
		
		UnfoldStream(S init, Function<S,Tuple2<T,S>> gen) {
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
		public FOption<T> next() {
			Tuple2<? extends T,? extends S> unfolded = m_gen.apply(m_seed);
			if ( unfolded != null ) {
				m_seed = unfolded._2;
				
				return FOption.of(unfolded._1);
			}
			else {
				return FOption.empty();
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
		public FOption<T> next() {
			while ( true ) {
				FOption<T> next = m_src.next();
				if ( next.isAbsent() || m_randGen.nextDouble() < m_ratio ) {
					return next;
				}
			}
		}
	}
}
