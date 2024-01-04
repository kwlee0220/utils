package utils.stream;

import static utils.Utilities.checkNotNullArgument;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.Lists;

import utils.Throwables;
import utils.async.ThreadInterruptedException;
import utils.func.CheckedFunction;
import utils.func.CheckedFunctionX;
import utils.func.FOption;
import utils.func.FailureCase;
import utils.func.FailureHandler;
import utils.func.Try;
import utils.func.Tuple;
import utils.func.Unchecked;
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
	
	public static abstract class AbstractFStream<T> implements FStream<T> {
		protected boolean m_closed = false;
		
		abstract protected void closeInGuard() throws Exception;

		@Override
		public void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;
				closeInGuard();
			}
		}
		
		public boolean isClosed() {
			return m_closed;
		}
		
		public void checkNotClosed() {
			if ( m_closed ) {
				throw new IllegalStateException("already closed: " + this);
			}
		}
	}
	
	static abstract class SingleSourceStream<S,T> extends AbstractFStream<T> {
		protected final FStream<S> m_src;
		
		protected SingleSourceStream(FStream<S> src) {
			checkNotNullArgument(src, "source FStream");
			
			m_src = src;
		}
		
		@Override
		protected void closeInGuard() throws Exception {
			Unchecked.runOrIgnore(m_src::close);
		}
	}
	
	static class MappedStream<S,T> extends SingleSourceStream<S,T> {
		private final Function<? super S,? extends T> m_mapper;
		
		MappedStream(FStream<S> base, Function<? super S,? extends T> mapper) {
			super(base);
			checkNotNullArgument(mapper, "mapper is null");
			
			m_mapper = mapper;
		}

		@Override
		public FOption<T> next() {
			return (!isClosed()) ? m_src.next().map(m_mapper) : FOption.empty();
		}
	}
	
	static class MapOrHandleStream<T,R> extends SingleSourceStream<T,R> {
		private final CheckedFunction<? super T,? extends R> m_mapper;
		private final FailureHandler<? super T> m_handler;
		
		MapOrHandleStream(FStream<T> base, CheckedFunction<? super T,? extends R> mapper,
					FailureHandler<? super T> handler) {
			super(base);
			
			checkNotNullArgument(mapper, "mapper is null");
			checkNotNullArgument(handler, "FailureHandler is null");
			
			m_mapper = mapper;
			m_handler = handler;
		}

		@Override
		public FOption<R> next() {
			FOption<T> onext;
			while ( (onext = m_src.next()).isPresent() ) {
				T next = onext.getUnchecked();
				try {
					return FOption.of(m_mapper.apply(next));
				}
				catch ( Throwable e ) {
					m_handler.handle(new FailureCase<>(next, e));
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class MapOrThrowStream<T,R,X extends Throwable> extends SingleSourceStream<T,R> {
		private final CheckedFunctionX<? super T,? extends R,X> m_mapper;
		
		MapOrThrowStream(FStream<T> base, CheckedFunctionX<? super T,? extends R,X> mapper) {
			super(base);
			
			checkNotNullArgument(mapper, "mapper is null");
			
			m_mapper = mapper;
		}

		@Override
		public FOption<R> next() {
			FOption<T> onext;
			while ( (onext = m_src.next()).isPresent() ) {
				try {
					T next = onext.getUnchecked();
					return FOption.of(m_mapper.apply(next));
				}
				catch ( Throwable e ) {
					Throwables.sneakyThrow(e);
					throw new AssertionError();
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class MapToIntStream<T> extends MappedStream<T,Integer> implements IntFStream {
		MapToIntStream(FStream<T> base, Function<? super T,Integer> mapper) {
			super(base, mapper);
		}
	}
	
	static class MapToLongStream<T> extends MappedStream<T,Long> implements LongFStream {
		MapToLongStream(FStream<T> base, Function<? super T,Long> mapper) {
			super(base, mapper);
		}
	}
	
	static class MapToFloatStream<T> extends MappedStream<T,Float> implements FloatFStream {
		MapToFloatStream(FStream<T> base, Function<? super T,Float> mapper) {
			super(base, mapper);
		}
	}
	
	static class MapToDoubleStream<T> extends MappedStream<T,Double> implements DoubleFStream {
		MapToDoubleStream(FStream<T> base, Function<? super T,Double> mapper) {
			super(base, mapper);
		}
	}
	
	static class MapToBooleanStream<T> extends MappedStream<T,Boolean> implements BooleanFStream {
		MapToBooleanStream(FStream<T> base, Function<? super T,Boolean> mapper) {
			super(base, mapper);
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
	
	static class ScannedStream<T> extends SingleSourceStream<T,T> {
		private final BiFunction<? super T,? super T,? extends T> m_combine;
		private FOption<T> m_current = null;
		
		ScannedStream(FStream<T> src, BiFunction<? super T,? super T,? extends T> combine) {
			super(src);
			m_combine = combine;
		}

		@Override
		public FOption<T> next() {
			if ( m_current == null ) {	// 첫번째 call인 경우.
				m_current = m_src.next();
			}
			else {
				m_current = m_src.next()
								.map(v -> m_combine.apply(m_current.getUnchecked(), v));
			}
			return m_current;
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
		private final Function<? super S,Tuple<? extends S,? extends T>> m_gen;
		private S m_state;
		private boolean m_closed = false;
		
		UnfoldStream(S init, Function<? super S,Tuple<? extends S,? extends T>> gen) {
			m_state = init;
			m_gen = gen;
		}

		@Override
		public void close() throws Exception {
			m_closed = true;
			
			IOUtils.closeQuietly(m_state);
		}

		@Override
		public FOption<T> next() {
			if ( m_closed ) {
				return FOption.empty();
			}
			
			Tuple<? extends S,? extends T> unfolded = m_gen.apply(m_state);
			if ( unfolded != null ) {
				m_state = unfolded._1;
				
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
	
	static final class CloserAttachedStream<T> implements FStream<T> {
		private final FStream<T> m_base;
		private final Runnable m_closingTask;
		private boolean m_closed = false;
		
		CloserAttachedStream(FStream<T> base, Runnable closingTask) {
			m_base = base;
			m_closingTask = closingTask;
		}

		@Override
		public void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;
				m_closingTask.run();
			}
		}

		@Override
		public FOption<T> next() {
			return (!m_closed) ? m_base.next() : FOption.empty();
		}
	}
	
	static class UniqueFStream<T> extends AbstractFStream<T> {
		private final FStream<T> m_src;
		private T m_last = null;
		
		UniqueFStream(FStream<T> base) {
			m_src = base;
		}

		@Override
		protected void closeInGuard() throws Exception {
			Unchecked.runOrIgnore(m_src::close);
		}

		@Override
		public FOption<T> next() {
			FOption<T> onext;
			while ( (onext = m_src.next()).isPresent() ) {
				T next = onext.get();
				if ( m_last == null || !m_last.equals(next) ) {
					m_last = next;
					return FOption.of(m_last);
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class UniqueKeyFStream<T,K> extends AbstractFStream<T> {
		private final FStream<T> m_src;
		private final Function<? super T, ? extends K> m_keyer;
		private K m_last = null;
		
		UniqueKeyFStream(FStream<T> base, Function<? super T, ? extends K> keyer) {
			m_src = base;
			m_keyer = keyer;
		}

		@Override
		protected void closeInGuard() throws Exception {
			Unchecked.runOrIgnore(m_src::close);
		}

		@Override
		public FOption<T> next() {
			FOption<T> onext;
			while ( (onext = m_src.next()).isPresent() ) {
				T next = onext.get();
				K key = m_keyer.apply(next);
				if ( m_last == null || !m_last.equals(key) ) {
					m_last = key;
					return FOption.of(next);
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class LazyStream<T> implements FStream<T> {
		private final Supplier<FStream<T>> m_supplier;
		private FStream<T> m_strm = null;
		private boolean m_closed = false;
		
		LazyStream(Supplier<FStream<T>> suppl) {
			m_supplier = suppl;
		}

		@Override
		public void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;
				if ( m_strm != null ) {
					m_strm.close();
				}
			}
		}

		@Override
		public FOption<T> next() {
			if ( m_closed ) {
				return FOption.empty();
			}
			
			if ( m_strm == null ) {
				m_strm = m_supplier.get();
			}
			
			return m_strm.next();
		}
	}
	
	static class FlatMapFOption<S,T> extends SingleSourceStream<S,T> {
		private final Function<? super S,FOption<T>> m_mapper;
		
		FlatMapFOption(FStream<S> base, Function<? super S, FOption<T>> mapper) {
			super(base);
			checkNotNullArgument(mapper, "mapper is null");
			
			m_mapper = mapper;
		}

		@Override
		public FOption<T> next() {
			FOption<S> next;
			while ( (next = m_src.next()).isPresent() ) {
				FOption<T> mapped = m_mapper.apply(next.getUnchecked());
				if ( mapped.isPresent() ) {
					return mapped;
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class FlatMapTry<S,T> extends SingleSourceStream<S,T> {
		private final Function<? super S,Try<T>> m_mapper;
		
		FlatMapTry(FStream<S> base, Function<? super S, Try<T>> mapper) {
			super(base);
			checkNotNullArgument(mapper, "mapper is null");
			
			m_mapper = mapper;
		}

		@Override
		public FOption<T> next() {
			FOption<S> next;
			while ( (next = m_src.next()).isPresent() ) {
				Try<T> mapped = m_mapper.apply(next.getUnchecked());
				if ( mapped.isSuccess() ) {
					return FOption.of(mapped.getUnchecked());
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class SelectiveMapStream<T> extends AbstractFStream<T> {
		private final FStream<T> m_src;
		private final Predicate<T> m_pred;
		private final Function<T,T> m_mapper;
		
		SelectiveMapStream(FStream<T> src, Predicate<T> pred, Function<T,T> mapper) {
			checkNotNullArgument(pred, "predicate is null");
			checkNotNullArgument(mapper, "mapper is null");

			m_src = src;
			m_pred = pred;
			m_mapper = mapper;
		}

		@Override
		protected void closeInGuard() throws Exception {
			Unchecked.runOrIgnore(m_src::close);
		}

		@Override
		public FOption<T> next() {
			FOption<T> next;
			while ( (next = m_src.next()).isPresent() ) {
				T obj = next.getUnchecked();
				if ( m_pred.test(obj) ) {
					return FOption.of(m_mapper.apply(obj));
				}
				else {
					return next;
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class IteratorFactoryFStream<T> extends AbstractFStream<T> {
		private final Supplier<? extends Iterator<? extends T>> m_fact;
		private Iterator<? extends T> m_cursor;
		
		IteratorFactoryFStream(Supplier<? extends Iterator<? extends T>> fact) {
			m_fact = fact;
			m_cursor = Collections.emptyIterator();
		}
		
		@Override
		protected void closeInGuard() throws Exception { }

		@Override
		public FOption<T> next() {
			while ( true ) {
				if ( !m_cursor.hasNext() ) {
					m_cursor = m_fact.get();
					assert m_cursor.hasNext();
				}
				
				return FOption.of(m_cursor.next());
			}
		}
	}
	
	static class SplitFStream<T> extends AbstractFStream<List<T>> {
		private final FStream<T> m_src;
		private final Predicate<? super T> m_delimiter;
		private boolean m_eos = false;
		
		SplitFStream(FStream<T> src, Predicate<? super T> delimiter) {
			checkNotNullArgument(delimiter, "predicate is null");
			m_src = src;
			m_delimiter = delimiter;
		}
		
		@Override
		protected void closeInGuard() throws Exception {
			Unchecked.runOrIgnore(m_src::close);
		}

		@Override
		public FOption<List<T>> next() {
			if ( m_eos ) {
				return FOption.empty();
			}
			
			List<T> buffer = Lists.newArrayList();
			while ( true ) {
				FOption<T> onext = m_src.next();
				if ( onext.isAbsent() ) {
					m_eos = true;
					return FOption.of(buffer);
				}
				
				T next = onext.get();
				boolean isDelim = m_delimiter.test(next);
				if ( isDelim ) {
					return FOption.of(buffer);
				}
				buffer.add(next);
			}
		}
	}
}
