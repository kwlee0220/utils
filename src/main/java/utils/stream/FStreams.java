package utils.stream;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.vavr.Tuple2;
import io.vavr.control.Try;
import utils.Utilities;
import utils.async.ThreadInterruptedException;
import utils.func.FOption;
import utils.func.MultipleSupplier;
import utils.io.IOUtils;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FStreams {
	@SuppressWarnings("rawtypes")
	static final EmptyStream EMPTY = new EmptyStream<>();
	
	private static class EmptyStream<T> implements FStream<T> {
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
		private boolean m_closed = false;
		
		IteratorStream(Iterator<? extends T> iter) {
			m_iter = iter;
		}

		@Override
		public void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;
				if ( m_iter instanceof AutoCloseable ) {
					IOUtils.closeQuietly((AutoCloseable)m_iter);
				}
			}
		}

		@Override
		public FOption<T> next() {
			return m_iter.hasNext() && !m_closed ? FOption.of(m_iter.next()) : FOption.empty();
		}
	}
	
	static abstract class SingleSourceStream<S,T> implements FStream<T> {
		protected final FStream<S> m_src;
		private boolean m_closed = false;
		
		protected SingleSourceStream(FStream<S> src) {
			Utilities.checkNotNullArgument(src, "source FStream");
			
			m_src = src;
		}
		
		@Override
		public void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;
				Try.run(m_src::close);
			}
		}
	}
	
	static class FilteredStream<T> extends SingleSourceStream<T,T> {
		private final Predicate<? super T> m_pred;
		
		FilteredStream(FStream<T> base, Predicate<? super T> pred) {
			super(base);
			
			m_pred = pred;
		}

		@Override
		public FOption<T> next() {
			for ( FOption<T> next = m_src.next(); next.isPresent(); next = m_src.next() ) {
				if ( m_pred.test(next.getUnchecked()) ) {
					return next;
				}
			}
			
			return FOption.empty();
		}
		
		@Override
		public String toString() {
			return String.format("filter");
		}
	}
	
	static class MappedStream<S,T> extends SingleSourceStream<S,T> {
		private final Function<? super S,? extends T> m_mapper;
		
		MappedStream(FStream<S> base, Function<? super S,? extends T> mapper) {
			super(base);
			
			m_mapper = mapper;
		}

		@Override
		public FOption<T> next() {
			return m_src.next().map(m_mapper);
		}
		
		@Override
		public String toString() {
			return String.format("map");
		}
	}
	
	static class MapToIntStream<T> extends MappedStream<T,Integer> implements IntFStream {
		MapToIntStream(FStream<T> base, Function<? super T,Integer> mapper) {
			super(base, mapper);
		}
		
		@Override
		public String toString() {
			return String.format("mapToInt");
		}
	}
	
	static class MapToLongStream<T> extends MappedStream<T,Long> implements LongFStream {
		MapToLongStream(FStream<T> base, Function<? super T,Long> mapper) {
			super(base, mapper);
		}
		
		@Override
		public String toString() {
			return String.format("mapToLong");
		}
	}
	
	static class MapToDoubleStream<T> extends MappedStream<T,Double> implements DoubleFStream {
		MapToDoubleStream(FStream<T> base, Function<? super T,Double> mapper) {
			super(base, mapper);
		}
		
		@Override
		public String toString() {
			return String.format("mapToDouble");
		}
	}
	
	static class PeekedStream<T> extends SingleSourceStream<T,T> {
		private final Consumer<? super T> m_effect;
		
		PeekedStream(FStream<T> base, Consumer<? super T> effect) {
			super(base);

			m_effect = effect;
		}

		@Override
		public FOption<T> next() {
			return m_src.next().ifPresent(m_effect);
		}
		
		@Override
		public String toString() {
			return String.format("peek");
		}
	}
	
	static class TakenStream<T> extends SingleSourceStream<T,T> {
		private long m_remains;
		
		TakenStream(FStream<T> src, long count) {
			super(src);

			m_remains = count;
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

	static class DroppedStream<T> extends SingleSourceStream<T,T> {
		private final long m_count;
		private boolean m_dropped = false;
		
		DroppedStream(FStream<T> src, long count) {
			super(src);

			m_count = count;
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
	
	static class TakeWhileStream<T,S> extends SingleSourceStream<T,T> {
		private Predicate<? super T> m_pred;
		private boolean m_eos = false;
		
		TakeWhileStream(FStream<T> src, Predicate<? super T> pred) {
			super(src);
			
			m_pred = pred;
		}

		@Override
		public void close() throws Exception {
			m_eos = true;
			super.close();
		}

		@Override
		public FOption<T> next() {
			if ( m_eos ) {
				return FOption.empty();
			}
			else {
				return m_src.next()
							.filter(m_pred)
							.ifAbsent(() -> m_eos = true);
			}
		}
	}

	static class DropWhileStream<T,S> extends SingleSourceStream<T,T> {
		private final Predicate<? super T> m_pred;
		private boolean m_started = false;
		
		DropWhileStream(FStream<T> src, Predicate<? super T> pred) {
			super(src);
			
			m_pred = pred;
		}

		@Override
		public FOption<T> next() {
			if ( !m_started ) {
				m_started = true;
	
				FOption<T> next;
				while ( (next = m_src.next()).test(m_pred) );
				return next;
			}
			else {
				return m_src.next();
			}
		}
	}
	
	static class ScannedStream<T> extends SingleSourceStream<T,T> {
		private final BinaryOperator<T> m_combine;
		private FOption<T> m_current = null;
		
		ScannedStream(FStream<T> src, BinaryOperator<T> combine) {
			super(src);
			m_combine = combine;
		}

		@Override
		public FOption<T> next() {
			if ( m_current == null ) {	// 첫번째 call인 경우.
				return m_current = m_src.next();
			}
			else {
				m_current = m_src.next()
								.map(v -> m_combine.apply(m_current.getUnchecked(), v));
				return m_current;
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
	
	static class UnfoldStream<S,T> implements FStream<T> {
		private final Function<? super S,Tuple2<? extends S,T>> m_gen;
		private S m_seed;
		private boolean m_closed = false;
		
		UnfoldStream(S init, Function<? super S,Tuple2<? extends S,T>> gen) {
			m_seed = init;
			m_gen = gen;
		}

		@Override
		public void close() throws Exception {
			m_closed = true;
			
			IOUtils.closeQuietly(m_seed);
		}

		@Override
		public FOption<T> next() {
			if ( m_closed ) {
				return FOption.empty();
			}
			
			Tuple2<? extends S,? extends T> unfolded = m_gen.apply(m_seed);
			if ( unfolded != null ) {
				m_seed = unfolded._1;
				
				return FOption.of(unfolded._2);
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

	static class DelayedStream<T> implements FStream<T> {
		private final FStream<T> m_src;
		private final long m_delay;
		private final TimeUnit m_tu;
		
		DelayedStream(FStream<T> src, long delay, TimeUnit tu) {
			m_src = src;
			m_delay = delay;
			m_tu = tu;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<T> next() {
			while ( true ) {
				FOption<T> next = m_src.next();
				if ( next.isPresent() ) {
					try {
						m_tu.sleep(m_delay);
					}
					catch ( InterruptedException e ) {
						throw new ThreadInterruptedException();
					}
				}
				
				return next;
			}
		}
	}

	static class SupplierStream<T> implements FStream<T> {
		private final MultipleSupplier<? extends T> m_supplier;
		private boolean m_closed = false;
		
		SupplierStream(MultipleSupplier<? extends T> supplier) {
			m_supplier = supplier;
		}

		@Override
		public void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;
				IOUtils.close(m_supplier);
			}
		}

		@Override
		public FOption<T> next() {
			return (m_closed) ? FOption.empty(): FOption.narrow(m_supplier.get());
		}
	}
}
