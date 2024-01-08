package utils.stream;

import static utils.Utilities.checkNotNullArgument;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.Lists;

import utils.Throwables;
import utils.func.CheckedFunction;
import utils.func.CheckedFunctionX;
import utils.func.FOption;
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
		private boolean m_closed = false;
		private boolean m_eos = false;
		private boolean m_initialized = false;
		
		abstract protected void closeInGuard() throws Exception;
		abstract protected FOption<T> nextInGuard();
		protected void initialize() { }

		@Override
		public final void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;
				m_eos = true;
				closeInGuard();
			}
		}

		@Override
		public FOption<T> next() {
			checkNotClosed();

			if ( m_eos ) {
				return FOption.empty();
			}
			if ( !m_initialized ) {
				initialize();
				m_initialized = true;
			}
			
			return nextInGuard().ifAbsent(() -> m_eos = true);
		}
		
		public boolean isClosed() {
			return m_closed;
		}
		
		public void checkNotClosed() {
			if ( m_closed ) {
				throw new IllegalStateException("already closed: " + this);
			}
		}
		
		protected void markEndOfStream() {
			m_eos = true;
		}
		
		public boolean isEndOfStream() {
			return m_eos;
		}
	}
	
	static abstract class SingleSourceStream<S,T> extends AbstractFStream<T> {
		private final FStream<S> m_src;
		
		abstract protected FOption<T> getNext(FStream<S> src);
		
		protected SingleSourceStream(FStream<S> src) {
			checkNotNullArgument(src, "source FStream");
			
			m_src = src;
		}
		
		@Override
		protected void closeInGuard() throws Exception {
			Unchecked.runOrIgnore(m_src::close);
		}
		
		@Override
		protected final FOption<T> nextInGuard() {
			return getNext(m_src);
		}
	}
	
	static class IterableFStream<T> extends AbstractFStream<T> {
		private final Iterable<? extends T> m_iterable;
		private Iterator<? extends T> m_iter;
		
		IterableFStream(Iterable<? extends T> iterable) {
			m_iterable = iterable;
		}

		@Override
		protected void closeInGuard() throws Exception { }

		@Override
		protected void initialize() {
			m_iter = m_iterable.iterator();
		}

		@Override
		protected FOption<T> nextInGuard() {
			if ( m_iter.hasNext() ) {
				return FOption.of(m_iter.next());
			}
			else {
				return FOption.empty();
			}
		}
	}
	
	static class TryMapStream<T,R> extends SingleSourceStream<T,Try<R>> {
		private final CheckedFunction<? super T,? extends R> m_mapper;
		
		TryMapStream(FStream<T> base, CheckedFunction<? super T,? extends R> mapper) {
			super(base);
			
			checkNotNullArgument(mapper, "mapper is null");
			
			m_mapper = mapper;
		}

		@Override
		public FOption<Try<R>> getNext(FStream<T> src) {
			FOption<T> onext;
			while ( (onext = src.next()).isPresent() ) {
				T next = onext.getUnchecked();
				try {
					return FOption.of(Try.success(m_mapper.apply(next)));
				}
				catch ( Throwable e ) {
					return FOption.of(Try.failure(e));
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
		public FOption<R> getNext(FStream<T> src) {
			FOption<T> onext;
			while ( (onext = src.next()).isPresent() ) {
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
	
	static class MappedStream<S,T> extends SingleSourceStream<S,T> {
		private final Function<? super S,? extends T> m_mapper;
		
		MappedStream(FStream<S> base, Function<? super S,? extends T> mapper) {
			super(base);
			checkNotNullArgument(mapper, "mapper is null");
			
			m_mapper = mapper;
		}

		@Override
		protected FOption<T> getNext(FStream<S> src) {
			return src.next().map(m_mapper);
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
		public FOption<T> getNext(FStream<T> src) {
			return src.next().ifPresent(m_effect);
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
		public FOption<T> getNext(FStream<T> src) {
			if ( m_current == null ) {	// 첫번째 call인 경우.
				m_current = src.next();
			}
			else {
				m_current = src.next()
								.map(v -> m_combine.apply(m_current.getUnchecked(), v));
			}
			return m_current;
		}
	}
	
	static class FoldLeftLeakFStream<S,T> extends SingleSourceStream<T,T> {
		private S m_state;
		private T m_last = null;
		private final BiFunction<? super S,? super T,? extends Tuple<S,T>> m_combine;
		
		FoldLeftLeakFStream(FStream<T> src, S initial,
							BiFunction<? super S,? super T,? extends Tuple<S,T>> combine) {
			super(src);
			
			m_state = initial;
			m_combine = combine;
		}

		@Override
		protected FOption<T> getNext(FStream<T> src) {
			if ( m_last == null ) {	// 첫번째 call인 경우.
				return src.next()
							.ifPresent(s -> m_last = s);
			}
			else {
				return src.next()
							.map(v -> m_combine.apply(m_state, m_last))
							.ifPresent(t -> {
								m_state = t._1;
								m_last = t._2;
							})
							.map(t -> t._2);
				
			}
		}
	}

	static class GeneratedStream<T> extends AbstractFStream<T> {
		private final Function<? super T,? extends T> m_inc;
		private T m_last;
		private boolean m_first = true;
		
		GeneratedStream(T init, Function<? super T,? extends T> inc) {
			m_last = init;
			m_inc = inc;
		}

		@Override
		protected void closeInGuard() throws Exception { }

		@Override
		public FOption<T> nextInGuard() {
			if ( m_first ) {
				m_first = false;
			}
			else {
				m_last = m_inc.apply(m_last);
			}
			return FOption.of(m_last);
		}
	}
	
	static class UnfoldStream<S,T> extends AbstractFStream<T> {
		private final Function<? super S,Tuple<? extends S,? extends T>> m_gen;
		private S m_state;
		
		UnfoldStream(S init, Function<? super S,Tuple<? extends S,? extends T>> gen) {
			m_state = init;
			m_gen = gen;
		}

		@Override
		protected void closeInGuard() throws Exception {
			IOUtils.closeQuietly(m_state);
		}

		@Override
		public FOption<T> nextInGuard() {
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
	
	static class UniqueFStream<T> extends SingleSourceStream<T,T> {
		private T m_last = null;
		
		UniqueFStream(FStream<T> base) {
			super(base);
		}

		@Override
		public FOption<T> getNext(FStream<T> src) {
			FOption<T> onext;
			while ( (onext = src.next()).isPresent() ) {
				T next = onext.get();
				if ( m_last == null || !m_last.equals(next) ) {
					m_last = next;
					return FOption.of(m_last);
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class UniqueKeyFStream<T,K> extends SingleSourceStream<T,T> {
		private final Function<? super T, ? extends K> m_keyer;
		private K m_last = null;
		
		UniqueKeyFStream(FStream<T> base, Function<? super T, ? extends K> keyer) {
			super(base);
			
			m_keyer = keyer;
		}

		@Override
		public FOption<T> getNext(FStream<T> src) {
			FOption<T> onext;
			while ( (onext = src.next()).isPresent() ) {
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
	
	static class FlatMapTry<S,T> extends SingleSourceStream<S,T> {
		private final Function<? super S,Try<T>> m_mapper;
		
		FlatMapTry(FStream<S> base, Function<? super S, Try<T>> mapper) {
			super(base);
			checkNotNullArgument(mapper, "mapper is null");
			
			m_mapper = mapper;
		}

		@Override
		public FOption<T> getNext(FStream<S> src) {
			FOption<S> next;
			while ( (next = src.next()).isPresent() ) {
				Try<T> mapped = m_mapper.apply(next.getUnchecked());
				if ( mapped.isSuccessful() ) {
					return FOption.of(mapped.getUnchecked());
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class SelectiveMapStream<T> extends SingleSourceStream<T,T> {
		private final Predicate<? super T> m_pred;
		private final Function<? super T,? extends T> m_mapper;
		
		SelectiveMapStream(FStream<T> src, Predicate<? super T> pred,
							Function<? super T,? extends T> mapper) {
			super(src);
			
			checkNotNullArgument(pred, "predicate is null");
			checkNotNullArgument(mapper, "mapper is null");

			m_pred = pred;
			m_mapper = mapper;
		}

		@Override
		public FOption<T> getNext(FStream<T> src) {
			FOption<T> next;
			while ( (next = src.next()).isPresent() ) {
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
	
	static class SplitFStream<T> extends SingleSourceStream<T,List<T>> {
		private final Predicate<? super T> m_delimiter;
		
		SplitFStream(FStream<T> src, Predicate<? super T> delimiter) {
			super(src);
			
			checkNotNullArgument(delimiter, "predicate is null");
			m_delimiter = delimiter;
		}

		@Override
		public FOption<List<T>> getNext(FStream<T> src) {
			List<T> buffer = Lists.newArrayList();
			while ( true ) {
				FOption<T> onext = src.next();
				if ( onext.isAbsent() ) {
					markEndOfStream();
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
	
	static class FlatMapDataSupplier<S,T> extends AbstractFStream<T> {
		private final S m_input;
		private final Function<? super S, ? extends FStream<T>> m_mapper;
		private FStream<T> m_outStream;
		
		FlatMapDataSupplier(S input, Function<? super S, ? extends FStream<T>> mapper) {
			m_input = input;
			m_mapper = mapper;
		}
		
		@Override
		protected void initialize() {
			m_outStream = m_mapper.apply(m_input);
		}

		@Override
		protected void closeInGuard() throws Exception {
			if ( m_outStream != null ) {
				m_outStream.close();
			}
		}

		@Override
		protected FOption<T> nextInGuard() {
			return m_outStream.next();
		}
	}
}
