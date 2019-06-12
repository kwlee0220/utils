package utils.stream;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.reactivex.Observable;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import utils.CSV;
import utils.KeyValue;
import utils.Utilities;
import utils.func.FLists;
import utils.func.FOption;
import utils.io.IOUtils;
import utils.stream.FStreams.MapToDoubleStream;
import utils.stream.FStreams.MapToIntStream;
import utils.stream.FStreams.MapToLongStream;
import utils.stream.FStreams.MappedStream;
import utils.stream.FStreams.PeekedStream;
import utils.stream.FStreams.SingleSourceStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FStream<T> extends Iterable<T>, AutoCloseable {
	/**
	 * 스트림에 포함된 다음 데이터를 반환한다.
	 * <p>
	 * 더 이상의 데이터가 없는 경우, 또는 이미 close된 경우에는 {@link FOption#empty()}을 반환함.
	 * 
	 * @return	스트림 데이터. 없는 경우는 {@link FOption#empty()}.
	 */
	public FOption<T> next();
	
	/**
	 * empty 스트림 객체를 반환한다.
	 * 
	 * @return	스트림 객체.
	 */
	@SuppressWarnings("unchecked")
	public static <T> FStream<T> empty() {
		return FStreams.EMPTY;
	}
	
	public default Try<Void> closeQuietly() {
		return Try.run(this::close);
	}
	
	/**
	 * 주어진 {@link Iterator}객체로부터 FStream 객체를 생성한다.
	 * 
	 * @param <T> Iterator에서 반환하는 데이터 타입
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(final Iterator<? extends T> iter) {
		Utilities.checkNotNullArgument(iter, "Iterator is null");
		
		return new FStream<T>() {
			private boolean m_closed = false;

			@Override
			public void close() throws Exception {
				if ( !m_closed ) {
					m_closed = true;
					IOUtils.closeQuietly(iter);
				}
			}

			@Override
			public FOption<T> next() {
				return iter.hasNext() && !m_closed ? FOption.of(iter.next()) : FOption.empty();
			}
		};
	}
	
	/**
	 * 주어진 {@link Iterable}객체로부터 FStream 객체를 생성한다.
	 * 
	 * @param <T> Iterable에서 반환하는 데이터 타입
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(Iterable<? extends T> values) {
		Utilities.checkNotNullArgument(values, "Iterable is null");
		
		return from(values.iterator());
	}
	
	/**
	 * 주어진 데이터 객체 배열 값을 갖는 FStream 객체를 생성한다.
	 * 
	 * @param <T> 배열 원소 데이터 타입
	 * @return FStream 객체
	 */
	@SafeVarargs
	public static <T> FStream<T> of(T... values) {
		Utilities.checkNotNullArgument(values, "null values");
		
		return from(Arrays.asList(values));
	}
	
	/**
	 * 주어진 {@link Try}객체로부터 FStream 객체를 생성한다.
	 * Try가 성공적인 경우는 {@link Try#get()}의 반환 값으로 구성된 단일 원소의 스트림이
	 * 생성되고, 오류인 경우는 {@link #empty()} 스트림이 생성된다.
	 * 
	 * @param <T> Try기 갖고 있는 데이터 타입
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(Try<? extends T> trial) {
		Utilities.checkNotNullArgument(trial, "Try is null");
		
		return Try.<T>narrow(trial).map(FStream::of).getOrElse(FStream::empty);
	}
	
	/**
	 * 주어진 {@link Stream}객체로부터 FStream 객체를 생성한다.
	 * 
	 * @param <T> Stream에서 반환하는 데이터 타입
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(Stream<? extends T> stream) {
		Utilities.checkNotNullArgument(stream, "Stream is null");
		
		return from(stream.iterator());
	}
	
	/**
	 * 주어진 {@link Observable}객체로부터 FStream 객체를 생성한다.
	 * 
	 * @param <T> Observable에서 반환하는 데이터 타입
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(Observable<? extends T> ob) {
		Utilities.checkNotNullArgument(ob, "Observable is null");
		
		return new ObservableStream<>(ob);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> FStream<T> narrow(FStream<? extends T> stream) {
		Utilities.checkNotNullArgument(stream, "stream is null");
		
		return (FStream<T>)stream;
	}
	
	/**
	 * 주어진 길의 내부 버퍼를 사용하는 {@link SuppliableFStream} 객체를 생성한다.
	 * 
	 * @param <T>	생성된 스트림 데이터의 타입
	 * @param length	생성될 {@link SuppliableFStream}가 내부적으로 사용할 버퍼 길이.
	 * @return SuppliableFStream 객체
	 */
	public static <T> SuppliableFStream<T> pipe(int length) {
		Utilities.checkArgument(length > 0, "length > 0: but=" + length);
		
		return new SuppliableFStream<>(length);
	}
	
	/**
	 * 주어진 상태 변수와 데이터 생성 함수를 활용하여 생성된 데이터로 구성된
	 * 무한 스트림 객체를 생성한다.
	 * <p>
	 * 데이터 생성 함수는 상태 변수를 인자로 받아서, 스트림 데이터와 변경된 상태 값을
	 * 반환하여야 한다. 함수의 반환 값이 {@code null}인 경우는 더 이상으로 생성할 데이터가
	 * 없다는 것을 의미한다.
	 * 
	 * @param <S>	상태 객체 타입
	 * @param <T>	데이터 생성 함수에 의해 생성되는 데이터의 타입
	 * @param init	상태 객체 초기 값
	 * @param gen	데이터 생성 함수
	 * @return FStream 객체
	 */
	public static <S,T> FStream<T> unfold(S init,
										Function<? super S,Tuple2<? extends S,? extends T>> gen) {
		Utilities.checkNotNullArgument(init, "initial value is null");
		Utilities.checkNotNullArgument(gen, "next value generator is null");
		
		return new FStreams.UnfoldStream<>(init, gen);
	}
	
	public static <T> FStream<T> generate(T init, Function<? super T,? extends T> inc) {
		Utilities.checkNotNullArgument(init, "initial value is null");
		Utilities.checkNotNullArgument(inc, "next value generator is null");
		
		return new FStreams.GeneratedStream<>(init, inc);
	}
	
	public static IntFStream range(int start, int end) {
		return new IntFStream.RangedStream(start, end, false);
	}
	public static IntFStream rangeClosed(int start, int end) {
		return new IntFStream.RangedStream(start, end, true);
	}
	
	/**
	 * 본 스트림에서 주어진 조건을 만족하는 데이터로만 구성된 스트림을 생성한다.
	 * 
	 * @param <T>	본 스트림이 갖는 데이터의 타입
	 * @param pred	조건 객체
	 * @return FStream 객체
	 */
	public default FStream<T> filter(final Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		return new SingleSourceStream<T,T>(this) {
			@Override
			public FOption<T> next() {
				for ( FOption<T> next = m_src.next(); next.isPresent(); next = m_src.next() ) {
					if ( pred.test(next.getUnchecked()) ) {
						return next;
					}
				}
				
				return FOption.empty();
			}
		};
	}
	
	/**
	 * 본 스트림에 포함된 각 데이터에서 변화된 데이터로 구성된 스트림을 생성한다.
	 * 
	 * @param <T>	본 스트림이 갖는 데이터의 타입
	 * @param <S>	매핑된 데이터의 타입
	 * @param mapper	매핑 함수 객체.
	 * @return FStream 객체
	 */
	public default <S> FStream<S> map(Function<? super T,? extends S> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return new MappedStream<>(this, mapper);
	}
	
	public default FStream<T> mapIf(boolean flag,
							Function<FStream<? extends T>,FStream<? extends T>> mapper) {
		if ( flag ) {
			return narrow(mapper.apply(this));
		}
		else {
			return this;
		}
	}
	
	/**
	 * 스트림에 포함된 모든 데이터를 이용하여 {@link Consumer#accept(Object)}를 호출한다.
	 * <p>
	 * 모든 데이터에 대한 호출이 완료되면 스트림의 {@link #closeQuietly()}를 호출한다.
	 * 
	 * @param <T>	본 스트림이 갖는 데이터의 타입
	 * @param effect	Consumer 객체.
	 */
	public default void forEach(Consumer<? super T> effect) {
		Utilities.checkNotNullArgument(effect, "effect is null");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				effect.accept(next.get());
			}
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림의 첫 count개의 데이터로 구성된 FStream 객체를 생성한다.
	 * 
	 * @param count	데이터 갯수.
	 * @return	'count' 개의  데이터로 구성된 스트림 객체.
	 */
	public default FStream<T> take(long count) {
		Utilities.checkArgument(count >= 0, "count >= 0: but: " + count);
		
		return new SingleSourceStream<T,T>(this) {
			private long m_remains = count;
			
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
		};
	}
	
	public default FStream<T> drop(final long count) {
		Utilities.checkArgument(count >= 0, "count >= 0: but: " + count);
		
		return new SingleSourceStream<T,T>(this) {
			private boolean m_dropped = false;
			
			@Override
			public FOption<T> next() {
				if ( !m_dropped ) {
					m_dropped = true;
					for ( int i =0; i < count; ++i ) {
						if ( m_src.next().isAbsent() ) {
							return FOption.empty();
						}
					}
				}
				
				return m_src.next();
			}
		};
	}

	public default FStream<T> takeWhile(final Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		return new SingleSourceStream<T,T>(this) {
			private boolean m_eos = false;

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
								.filter(pred)
								.ifAbsent(() -> m_eos = true);
				}
			}
		};
	}
	
	public default FStream<T> dropWhile(final Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		return new SingleSourceStream<T,T>(this) {
			private boolean m_started = false;

			@Override
			public FOption<T> next() {
				if ( !m_started ) {
					m_started = true;
		
					FOption<T> next;
					while ( (next = m_src.next()).test(pred) );
					return next;
				}
				else {
					return m_src.next();
				}
			}
		};
	}
	
	public default FStream<T> sample(double ratio) {
		Utilities.checkArgument(ratio >= 0, "ratio >= 0");
		
		return new FStreams.SampledStream<>(this, ratio);
	}
	
	public default FStream<T> sample(long total, double ratio) {
		Utilities.checkArgument(total >= 0, "total >= 0");
		Utilities.checkArgument(ratio >= 0, "ratio >= 0");
		
		return new AdaptiveSamplingStream<>(this, total, ratio);
	}
	
	public default FStream<T> shuffle() {
		return new ShuffledFStream<>(this);
	}
	
	public default <V> FStream<V> cast(Class<? extends V> cls) {
		Utilities.checkNotNullArgument(cls, "target class is null");
		
		return map(cls::cast);
	}
	
	public default <V> FStream<V> castSafely(Class<? extends V> cls) {
		Utilities.checkNotNullArgument(cls, "target class is null");
		
		return filter(cls::isInstance).map(cls::cast);
	}
	
	public default <V extends T> FStream<V> ofExactClass(Class<V> cls) {
		Utilities.checkNotNullArgument(cls, "target class is null");
		
		return filter(v -> v.getClass().equals(cls)).map(cls::cast);
	}
	
	public default FStream<T> peek(Consumer<? super T> effect) {
		Utilities.checkNotNullArgument(effect, "effect is null");
		
		return new PeekedStream<>(this, effect);
	}
	
	public default <V> FStream<V> flatMap(Function<? super T,? extends FStream<V>> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return concat(map(mapper));
	}
	
	public default <V> FStream<V> flatMapOption(Function<? super T,FOption<V>> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");

		return flatMap(t -> mapper.apply(t).fstream());
	}
	
	public default <V> FStream<V> flatMapIterable(Function<? super T,? extends Iterable<V>> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return flatMap(t -> FStream.from(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapArray(Function<? super T,V[]> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapStream(Function<? super T,Stream<V>> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return flatMap(t -> FStream.from(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapTry(Function<? super T,Try<V>> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");

		return flatMap(t -> FStream.from(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapParallel(Function<? super T,? extends FStream<V>> mapper,
													int parallelLevel) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		Utilities.checkArgument(parallelLevel > 0, "parallelLevel > 0, but: " + parallelLevel);
		
		return mergeParallel(map(t -> FStream.lazy(() -> mapper.apply(t))), parallelLevel);
	}
	
	public default boolean exists() {
		return findFirst().isPresent();
	}
	
	public default boolean exists(Predicate<? super T> pred) {
		return findNext(pred).isPresent();
	}
	
	public default boolean forAll(Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate");
		
		return foldLeft(true, false,
						(a,t) -> Try.ofSupplier(() -> pred.test(t)).getOrElse(false));
	}
	
	public default FStream<T> scan(BinaryOperator<T> combiner) {
		Utilities.checkNotNullArgument(combiner, "combiner is null");
		
		return new FStreams.ScannedStream<>(this, combiner);
	}
	
	public default <S> S foldLeft(S accum, BiFunction<? super S,? super T,? extends S> folder) {
		Utilities.checkNotNullArgument(folder, "folder is null");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				accum = folder.apply(accum, next.get());
			}
			
			return accum;
		}
		finally {
			closeQuietly();
		}
	}
	
	public default <S> S foldLeft(S accum, S stopper,
								BiFunction<? super S,? super T,? extends S> folder) {
		Utilities.checkNotNullArgument(accum, "accum is null");
		Utilities.checkNotNullArgument(folder, "folder is null");
		
		try {
			if ( accum.equals(stopper) ) {
				return accum;
			}
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				accum = folder.apply(accum, next.get());
				if ( accum.equals(stopper) ) {
					return accum;
				}
			}
			
			return accum;
		}
		finally {
			closeQuietly();
		}
	}
	
	public default T reduce(BiFunction<? super T,? super T,? extends T> reducer) {
		Utilities.checkNotNullArgument(reducer, "reducer is null");
		
		FOption<T> initial = next();
		if ( initial.isAbsent() ) {
			throw new IllegalStateException("Stream is empty");
		}
		
		return foldLeft(initial.get(), (a,t) -> reducer.apply(a, t));
	}
	
	public default <S> S collectLeft(S accum, BiConsumer<? super S,? super T> collect) {
		Utilities.checkNotNullArgument(accum, "accum is null");
		Utilities.checkNotNullArgument(collect, "collect is null");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				collect.accept(accum, next.get());
			}
			
			return accum;
		}
		finally {
			closeQuietly();
		}
	}
	
	public default <S> S foldRight(S accum, BiFunction<? super T,? super S,? extends S> folder) {
		return FLists.foldRight(toList(), accum, folder);
	}
	
	public default long count() {
		long count = 0;
		while ( next().isPresent() ) {
			++count;
		}
		
		return count;
	}
	
	public default FOption<T> findFirst() {
		try {
			return next();
		}
		finally {
			closeQuietly();
		}
	}

	public default FOption<T> findNext(Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() && !pred.test(next.get()) );
			return next;
		}
		finally {
			closeQuietly();
		}
	}
	
	public default FOption<T> last() {
		try {
			FOption<T> last = FOption.empty();
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				last = next;
			}
			
			return last;
		}
		finally {
			closeQuietly();
		}
	}
	
	public static <T> FStream<T> concat(FStream<? extends FStream<? extends T>> fact) {
		Utilities.checkNotNullArgument(fact, "source FStream factory");
		
		return new ConcatedStream<>(fact);
	}
	
	public default FStream<T> concatWith(FStream<? extends T> tail) {
		Utilities.checkNotNullArgument(tail, "tail is null");
		
		return concat(FStream.of(this, tail));
	}
	
	public default FStream<T> concatWith(T tail) {
		Utilities.checkNotNullArgument(tail, "tail is null");
		
		return concatWith(FStream.of(tail));
	}
	
	public static <T> FStream<T> concat(FStream<? extends T> head, FStream<? extends T> tail) {
		Utilities.checkNotNullArgument(head, "head is null");
		Utilities.checkNotNullArgument(tail, "tail is null");
		
		return concat(FStream.of(head, tail));
	}
	
	public static <T> FStream<T> concat(FStream<? extends T> head, T tail) {
		Utilities.checkNotNullArgument(head, "head is null");
		Utilities.checkNotNullArgument(tail, "tail is null");
		
		return concat(head, FStream.of(tail));
	}
	
	public static <T> FStream<T> mergeParallel(FStream<? extends FStream<? extends T>> gen,
												int parallelLevel) {
		Utilities.checkNotNullArgument(gen, "generator is null");
		Utilities.checkArgument(parallelLevel > 0, "parallelLevel > 0, but: " + parallelLevel);
		
		return new ParallelMergedStream<>(gen, parallelLevel);
	}

	public default FStream<Tuple2<T,Integer>> zipWithIndex(int start) {
		return zipWith(range(start, Integer.MAX_VALUE));
	}
	public default FStream<Tuple2<T,Integer>> zipWithIndex() {
		return zipWithIndex(0);
	}
	
	public default <U> FStream<Tuple2<T,U>> zipWith(FStream<? extends U> other) {
		Utilities.checkNotNullArgument(other, "zip FStream is null");
		
		return new ZippedFStream<>(this, other);
	}
	
	public default FStream<List<T>> buffer(int count, int skip) {
		Utilities.checkArgument(count >= 0, "count >= 0, but: " + count);
		Utilities.checkArgument(skip > 0, "skip > 0, but: " + skip);
		
		return new BufferedStream<>(this, count, skip);
	}
	
	public default Iterator<T> iterator() {
		return new FStreamIterator<>(this);
	}
	
	public default <C extends Collection<T>> C toCollection(C coll) {
		return collectLeft(coll, (l,t) -> l.add(t));
	}
	
	public default ArrayList<T> toList() {
		return toCollection(Lists.newArrayList());
	}
	
	public default HashSet<T> toSet() {
		return toCollection(Sets.newHashSet());
	}
	
	public default <K,V> Map<K,V> toMap(Map<K,V> map,
										Function<? super T,? extends K> toKey,
										Function<? super T,? extends V> toValue) {
		return collectLeft(map, (m,kv) -> m.put(toKey.apply(kv), toValue.apply(kv)));
	}
	
	public default <K,V> Map<K,V> toMap(Function<? super T,? extends K> toKey,
										Function<? super T,? extends V> toValue) {
		return toMap(Maps.newHashMap(), toKey, toValue);
	}
	
	public default IntFStream toIntFStream() {
		return new IntFStream.FStreamAdaptor(this.cast(Integer.class));
	}
	
	public default <S> S[] toArray(Class<S> componentType) {
		List<T> list = toList();
		@SuppressWarnings("unchecked")
		S[] array = (S[])Array.newInstance(componentType, list.size());
		return list.toArray(array);
	}
	
	public default PrependableFStream<T> toPrependable() {
		return new PrependableFStream<>(this);
	}
	
	public default <K> KVFStream<K,T> toKVFStream(Function<? super T,? extends K> keyGen) {
		return KVFStream.downcast(map(t -> KeyValue.of(keyGen.apply(t), t)));
	}
	
	public default <K,V> KVFStream<K,V> toKVFStream(Function<? super T,? extends K> keyGen,
													Function<? super T,? extends V> valueGen) {
		return KVFStream.downcast(map(t -> KeyValue.of(keyGen.apply(t), valueGen.apply(t))));
	}
	
	public default Stream<T> stream() {
		return Utilities.stream(iterator());
	}
	
	/**
	 * 주어진 키에 해당하는 데이터별로 reduce작업을 수행한다.
	 * <p>
	 * 수행된 결과는 key별로 {@link Map}에 저장되어 반환된다.
	 * 
	 * @param <K>	{@code keyer}를 통해 생성되는 키 타입 클래스.
	 * @param keyer	입력 데이터에서 키를 뽑아내는 함수.
	 * @param reducer	reduce 함수.
	 * @return	키 별로 reduce된 결과를 담은 Map 객체.
	 */
	public default <K> Map<K,T> reduceByKey(Function<? super T,? extends K> keyer,
											BiFunction<? super T,? super T,? extends T> reducer) {
		Utilities.checkNotNullArgument(keyer, "keyer is null");
		Utilities.checkNotNullArgument(reducer, "reducer is null");
		
		return collectLeft(Maps.newHashMap(), (accums,v) ->
			accums.compute(keyer.apply(v), (k,old) -> (old != null) ? reducer.apply(old, v) : v));
	}
	
	public default <K,S> Map<K,S> foldLeftByKey(Function<? super T,? extends K> keyer,
													Function<? super K,? extends S> accumInitializer,
													BiFunction<? super S,? super T,? extends S> folder) {
		return collectLeft(Maps.newHashMap(),
						(accums,v) -> accums.compute(keyer.apply(v),
													(k,accum) -> {
														if ( accum != null ) {
															accum = accumInitializer.apply(k);
														}
														return folder.apply(accum, v);
													})
		);
	}
	
	public default <K> KeyedGroups<K,T> groupByKey(Function<? super T,? extends K> keyer) {
		return collectLeft(KeyedGroups.create(), (g,t) -> g.add(keyer.apply(t), t));
	}
	
	public default <K,V> KeyedGroups<K,V> groupByKey(Function<? super T,? extends K> keySelector,
											Function<? super T,? extends V> valueSelector) {
		return collectLeft(KeyedGroups.create(),
							(g,t) -> g.add(keySelector.apply(t), valueSelector.apply(t)));
	}
	
	public default <K,V> KeyedGroups<K,V> groupByKeyValue(Function<? super T,KeyValue<K,V>> selector) {
		return collectLeft(KeyedGroups.create(),
							(groups,t) -> {
								KeyValue<K,V> kv = selector.apply(t);
								groups.add(kv.key(), kv.value());
							});
	}
	
	public default <K,V> KeyedGroups<K,V> multiGroupBy(Function<? super T,FStream<K>> keysSelector,
													Function<? super T,FStream<V>> valuesSelector) {
		return flatMap(t -> keysSelector.apply(t).map(k -> Tuple.of(k,t)))
				.collectLeft(KeyedGroups.create(), (groups,t) -> {
					valuesSelector.apply(t._2)
								.forEach(v -> groups.add(t._1,v));
				});
	}
	
	public default FStream<T> sort(Comparator<? super T> cmp) {
		List<T> list = toList();
		list.sort(cmp);
		return from(list);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default FStream<T> sort() {
		return sort((t1,t2) -> ((Comparable)t1).compareTo(t2));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default FStream<T> quasiSort(int queueLength) {
		return new QuasiSortedFStream<>(this, queueLength, (v1,v2) -> ((Comparable)v1).compareTo(v2));
	}
	public default FStream<T> quasiSort(int queueLength, Comparator<T> cmptor) {
		return new QuasiSortedFStream<>(this, queueLength, cmptor);
	}
	
    public default FStream<T> takeTopK(int k, Comparator<? super T> cmp) {
        return new TopKPickedFStream<>(this, k, cmp);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public default FStream<T> takeTopK(int k) {
        return new TopKPickedFStream<>(this, k, (t1,t2) -> ((Comparable)t1).compareTo(t2));
    }
	
	public default List<T> max(Comparator<? super T> cmp) {
		return foldLeft(new ArrayList<T>(),
						(max,v) -> {
							if ( max.isEmpty() ) {
								return Lists.newArrayList(v);
							}
							
							int ret = cmp.compare(v, max.get(0));
							if ( ret > 0 ) {
								max = Lists.newArrayList(v);
							}
							else if ( ret == 0 ) {
								max.add(v);
							}
							return max;
						});
	}
	
	public default List<T> min(Comparator<? super T> cmp) {
		return foldLeft(new ArrayList<T>(),
				(min,v) -> {
					if ( min.isEmpty() ) {
						return Lists.newArrayList(v);
					}
					
					int ret = cmp.compare(v, min.get(0));
					if ( ret < 0 ) {
						min = Lists.newArrayList(v);
					}
					else if ( ret == 0 ) {
						min.add(v);
					}
					return min;
				});
	}

	@SuppressWarnings("unchecked")
	public default List<T> max() {
		return foldLeft(new ArrayList<T>(),
				(max,v) -> {
					if ( max.isEmpty() ) {
						return Lists.newArrayList(v);
					}
					
					int ret = ((Comparable<T>)v).compareTo(max.get(0));
					if ( ret > 0 ) {
						max = Lists.newArrayList(v);
					}
					else if ( ret == 0 ) {
						max.add(v);
					}
					return max;
				});
	}

	@SuppressWarnings("unchecked")
	public default List<T> min() {
		return foldLeft(new ArrayList<T>(),
				(min,v) -> {
					if ( min.isEmpty() ) {
						return Lists.newArrayList(v);
					}
					
					int ret = ((Comparable<T>)v).compareTo(min.get(0));
					if ( ret < 0 ) {
						min = Lists.newArrayList(v);
					}
					else if ( ret == 0 ) {
						min.add(v);
					}
					return min;
				});
	}
	
	public default String join(String delim, String begin, String end) {
		return zipWithIndex()
				.foldLeft(new StringBuilder(begin),
							(b,t) -> (t._2 > 0) ? b.append(delim).append(t._1.toString())
												: b.append(t._1.toString()))
					.append(end)
					.toString();
	}
	
	public default String join(String delim) {
		return join(delim, "", "");
	}
	public default String join(char delim) {
		return join(""+delim, "", "");
	}
	
	public default String join(CSV csv) {
		return csv.toString(map(Object::toString));
	}

	public default boolean startsWith(FStream<T> subList) {
		Utilities.checkNotNullArgument(subList, "subList is null");
		
		FOption<T> subNext = subList.next();
		FOption<T> next = next();
		while ( subNext.isPresent() && next.isPresent() ) {
			if ( !subNext.get().equals(next.get()) ) {
				return false;
			}
			
			subNext = subList.next();
			next = next();
		}
		
		return (next.isPresent() && subNext.isAbsent());
	}
	
	public default FStream<T> distinct() {
		return FStream.from(toCollection(Sets.newHashSet()));
	}
	
	public default <K> FStream<T> distinct(Function<T,K> keyer) {
		return new FStreams.DistinctStream<>(this, keyer);
	}
	
	public default <K> FStream<KeyedFStream<K,T>> groupBy(Function<? super T,? extends K> grouper) {
		return new GroupByStream<>(this, grouper);
	}
	
	public default PrefetchStream<T> prefetched(int count) {
		return new PrefetchStream<>(this, count);
	}
	
	public default FStream<T> delay(long delay, TimeUnit tu) {
		return new FStreams.DelayedStream<>(this, delay, tu);
	}
	
	public static <T> FStream<T> lazy(Supplier<FStream<T>> suppl) {
		return new FStreams.LazyStream<>(suppl);
	}
	
	public static IntFStream of(int[] values) {
		return IntFStream.of(values);
	}
	
	public static LongFStream of(long[] values) {
		return LongFStream.of(values);
	}
	
	public static DoubleFStream of(double[] values) {
		return DoubleFStream.of(values);
	}
	
	public default IntFStream mapToInt(Function<? super T, Integer> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return new MapToIntStream<>(this, mapper);
	}
	
	public default LongFStream mapToLong(Function<? super T, Long> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return new MapToLongStream<>(this, mapper);
	}
	
	public default DoubleFStream mapToDouble(Function<? super T, Double> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return new MapToDoubleStream<>(this, mapper);
	}
}
