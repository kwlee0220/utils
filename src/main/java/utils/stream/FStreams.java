package utils.stream;


import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import utils.LoggerSettable;
import utils.Preconditions;
import utils.Throwables;
import utils.Tuple;
import utils.func.CheckedFunctionX;
import utils.func.FOption;
import utils.func.Nullables;
import utils.func.Try;
import utils.func.Unchecked;
import utils.io.IOUtils;

/**
 * {@link FStream} 연산자들의 구현 클래스를 모아 둔 클래스.
 * <p>
 * 외부에 공개된 멤버는 사용자 정의 스트림의 기반 클래스인 {@link AbstractFStream}뿐이며,
 * 나머지 구현 클래스들은 패키지 내부에서 {@link FStream}의 메소드를 통해서만 사용된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FStreams {
	/**
	 * {@code FStreams} 객체를 생성한다.
	 * <p>
	 * 이 클래스는 정적 멤버만 제공하므로 직접 생성할 필요가 없다.
	 */
	public FStreams() { }

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
	
	/**
	 * 초기화·소진·닫힘 상태를 관리하는 사용자 정의 스트림의 기반 클래스.
	 * <p>
	 * 하위 클래스는 {@link #nextInGuard()}와 {@link #closeInGuard()}를 구현하고, 필요하면
	 * {@link #initialize()}를 재정의한다. {@link #close()}는 {@code final}이며 중복 호출을 무시한다.
	 * 닫힌 뒤 {@link #next()}를 호출하면 {@link IllegalStateException}이 발생한다.
	 * <p>
	 * 메소드 이름의 'InGuard'와 달리 이 클래스는 동기화를 수행하지 않으므로 여러 스레드에서
	 * 동시에 사용하면 안 된다.
	 *
	 * @param <T>	원소 타입.
	 */
	public static abstract class AbstractFStream<T> implements FStream<T>, LoggerSettable {
		private final static Logger s_logger = LoggerFactory.getLogger(AbstractFStream.class);
		
		private boolean m_closed = false;
		private boolean m_eos = false;
		private boolean m_initialized = false;
		private Logger m_logger = s_logger;

		/**
		 * {@code AbstractFStream} 객체를 생성한다.
		 */
		public AbstractFStream() { }
		
		/**
		 * 스트림이 사용하는 자원을 해제한다.
		 * <p>
		 * {@link #close()}가 처음 호출될 때 한 번만 호출된다.
		 *
		 * @throws Exception	자원 해제 중 오류가 발생한 경우.
		 */
		abstract protected void closeInGuard() throws Exception;

		/**
		 * 다음 원소를 반환한다.
		 * <p>
		 * 스트림이 닫히지 않았고 끝에 도달하지 않은 경우에만 {@link #next()}에서 호출된다.
		 * {@link FOption#empty()}를 반환하면 스트림의 끝으로 표시되어 이후 다시 호출되지 않는다.
		 *
		 * @return	다음 원소. 더 이상 원소가 없으면 {@link FOption#empty()}.
		 */
		abstract protected FOption<T> nextInGuard();

		/**
		 * 첫 {@link #nextInGuard()} 호출 직전에 한 번 호출되는 초기화 메소드.
		 * <p>
		 * 기본 구현은 아무것도 하지 않는다. 이 메소드가 예외를 던지면 초기화 완료로 표시되지 않으므로
		 * 다음 {@link #next()} 호출 때 다시 호출된다.
		 */
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
		
		/**
		 * 스트림이 닫혔는지 여부를 반환한다.
		 *
		 * @return	{@link #close()}가 호출되었으면 {@code true}.
		 */
		public boolean isClosed() {
			return m_closed;
		}
		
		/**
		 * 스트림이 닫히지 않았는지 확인한다.
		 *
		 * @throws IllegalStateException	스트림이 이미 닫힌 경우.
		 */
		public void checkNotClosed() {
			if ( m_closed ) {
				throw new IllegalStateException("already closed: " + this);
			}
		}
		
		/**
		 * 스트림을 끝에 도달한 상태로 표시한다.
		 * <p>
		 * 이후 {@link #next()}는 {@link #nextInGuard()}를 호출하지 않고 {@link FOption#empty()}를 반환한다.
		 */
		protected void markEndOfStream() {
			m_eos = true;
		}
		
		/**
		 * 스트림이 끝에 도달했는지 여부를 반환한다.
		 *
		 * @return	끝에 도달했거나 닫힌 경우 {@code true}.
		 */
		public boolean isEndOfStream() {
			return m_eos;
		}
		
		@Override
		public Logger getLogger() {
			return Nullables.orElse(m_logger, s_logger);
		}
		
		@Override
		public void setLogger(Logger logger) {
			m_logger = logger;
		}
	}
	
	static abstract class SingleSourceStream<S,T> extends AbstractFStream<T> {
		private final FStream<S> m_src;
		
		abstract protected FOption<T> getNext(FStream<S> src);
		
		protected SingleSourceStream(FStream<S> src) {
			Preconditions.checkNotNullArgument(src, "source FStream");
			
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
	
	static class MapOrThrowStream<T,R,X extends Throwable> extends SingleSourceStream<T,R> {
		private final CheckedFunctionX<? super T,? extends R,X> m_mapper;
		
		MapOrThrowStream(FStream<T> base, CheckedFunctionX<? super T,? extends R,X> mapper) {
			super(base);
			
			Preconditions.checkNotNullArgument(mapper, "mapper is null");
			
			m_mapper = mapper;
		}

		@Override
		public FOption<R> getNext(FStream<T> src) {
			FOption<T> onext;
			while ( (onext = src.next()).isPresent() ) {
				try {
					T next = onext.getOrNull();
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
			Preconditions.checkNotNullArgument(mapper, "mapper is null");
			
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
								.map(v -> m_combine.apply(m_current.getOrNull(), v));
			}
			return m_current;
		}
	}
	
	static class FoldScannedStream<T,S> extends SingleSourceStream<T,S> {
		private @Nullable S m_accum;
		private final BiFunction<? super S,? super T,? extends S> m_folder;
		
		FoldScannedStream(FStream<T> src, @Nullable S initial,
							BiFunction<? super S,? super T,? extends S> folder) {
			super(src);
			
			m_accum = initial;
			m_folder = folder;
		}

		@Override
		protected FOption<S> getNext(FStream<T> src) {
			// 첫 호출 여부를 따로 기억할 필요가 없다.
			// 초기값이 첫 원소에도 적용되고, 누적값이 null이어도 그대로 이어진다.
			FOption<T> next = src.next();
			if ( next.isAbsent() ) {
				return FOption.empty();
			}
			m_accum = m_folder.apply(m_accum, next.getOrNull());
			return FOption.of(m_accum);
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

		// 원본을 먼저 닫고, 원본 닫기가 실패해도 등록한 작업은 실행한다. 둘 다 실패하면 작업의 예외를
		// 원본 예외의 suppressed로 붙여 원본 예외를 던진다.
		@Override
		public void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;

				Throwable failure = null;
				try {
					m_base.close();
				}
				catch ( Throwable e ) {
					failure = e;
				}
				try {
					m_closingTask.run();
				}
				catch ( Throwable e ) {
					if ( failure == null ) {
						failure = e;
					}
					else {
						failure.addSuppressed(e);
					}
				}

				if ( failure instanceof Exception ex ) {
					throw ex;
				}
				else if ( failure instanceof Error err ) {
					throw err;
				}
			}
		}

		@Override
		public FOption<T> next() {
			if ( m_closed ) {
				throw new IllegalStateException("already closed: " + this);
			}
			return m_base.next();
		}
	}
	
	static class DistinctUntilChangedFStream<T> extends SingleSourceStream<T,T> {
		private T m_last = null;
		private boolean m_first = true;	// m_last가 아직 없음(null 원소와 구분한다)
		
		DistinctUntilChangedFStream(FStream<T> base) {
			super(base);
		}

		@Override
		public FOption<T> getNext(FStream<T> src) {
			FOption<T> onext;
			while ( (onext = src.next()).isPresent() ) {
				T next = onext.get();
				if ( m_first || !Objects.equals(m_last, next) ) {
					m_first = false;
					m_last = next;
					return FOption.of(m_last);
				}
			}
			
			return FOption.empty();
		}
	}
	
	static class DistinctKeyUntilChangedFStream<T,K> extends SingleSourceStream<T,T> {
		private final Function<? super T, ? extends K> m_keyer;
		private K m_last = null;
		private boolean m_first = true;	// m_last가 아직 없음(null 키와 구분한다)
		
		DistinctKeyUntilChangedFStream(FStream<T> base, Function<? super T, ? extends K> keyer) {
			super(base);
			
			m_keyer = keyer;
		}

		@Override
		public FOption<T> getNext(FStream<T> src) {
			FOption<T> onext;
			while ( (onext = src.next()).isPresent() ) {
				T next = onext.get();
				K key = m_keyer.apply(next);
				if ( m_first || !Objects.equals(m_last, key) ) {
					m_first = false;
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
		private final Function<? super S,? extends Try<? extends T>> m_mapper;
		
		FlatMapTry(FStream<S> base, Function<? super S, ? extends Try<? extends T>> mapper) {
			super(base);
			Preconditions.checkNotNullArgument(mapper, "mapper is null");
			
			m_mapper = mapper;
		}

		@Override
		public FOption<T> getNext(FStream<S> src) {
			FOption<S> next;
			while ( (next = src.next()).isPresent() ) {
				Try<? extends T> mapped = m_mapper.apply(next.getOrNull());
				if ( mapped.isSuccessful() ) {
					T value = mapped.getUnchecked();
					return FOption.of(value);
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
			
			Preconditions.checkNotNullArgument(pred, "predicate is null");
			Preconditions.checkNotNullArgument(mapper, "mapper is null");

			m_pred = pred;
			m_mapper = mapper;
		}

		@Override
		public FOption<T> getNext(FStream<T> src) {
			FOption<T> next;
			while ( (next = src.next()).isPresent() ) {
				T obj = next.getOrNull();
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
		private boolean m_first = true;
		
		SplitFStream(FStream<T> src, Predicate<? super T> delimiter) {
			super(src);
			
			Preconditions.checkNotNullArgument(delimiter, "predicate is null");
			m_delimiter = delimiter;
		}

		@Override
		public FOption<List<T>> getNext(FStream<T> src) {
			List<T> buffer = Lists.newArrayList();
			boolean first = m_first;
			m_first = false;
			while ( true ) {
				FOption<T> onext = src.next();
				if ( onext.isAbsent() ) {
					markEndOfStream();
					// 빈 입력은 빈 스트림이다.
					return (first && buffer.isEmpty()) ? FOption.empty() : FOption.of(buffer);
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
