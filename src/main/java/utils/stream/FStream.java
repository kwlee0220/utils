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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.reactivex.Observable;
import io.vavr.CheckedConsumer;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import utils.Utilities;
import utils.func.FLists;
import utils.func.MultipleSupplier;
import utils.stream.FStreams.FilteredStream;
import utils.stream.FStreams.MapToDoubleStream;
import utils.stream.FStreams.MapToIntStream;
import utils.stream.FStreams.MapToLongStream;
import utils.stream.FStreams.MappedStream;
import utils.stream.FStreams.PeekedStream;
import utils.stream.KVFStreams.KeyGeneratedKVFStream;
import utils.stream.KVFStreams.KeyValueGeneratedKVFStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FStream<T> extends AutoCloseable {
	/**
	 * 스트림에 포함된 다음 데이터를 반환한다.
	 * <p>
	 * 더 이상의 데이터가 없는 경우는 {@code null}을 반환함.
	 * 
	 * @param <T> 데이터 타입
	 * @return	다음 데이터. 없는 경우는 {@code null}.
	 */
	public T next();
	
	public static <T> FStream<T> empty() {
		return new FStreamImpl<>("empty", Option::none);
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
	
	@SafeVarargs
	public static <T> FStream<T> of(T... values) {
		return of(Arrays.asList(values));
	}
	
	public static <T> FStream<T> of(Option<? extends T> opt) {
		Objects.requireNonNull(opt);
		
		return opt.map(t -> FStream.of((T)t))
					.getOrElse(FStream.empty());
	}
	
	public static <T> FStream<T> of(Iterable<? extends T> values) {
		Objects.requireNonNull(values);
		
		return of(values.iterator());
	}
	
	public static <T> FStream<T> of(Iterator<? extends T> iter) {
		Objects.requireNonNull(iter);
		
		return new FStreamImpl<>("fromIterator",
								() -> iter.hasNext() ? Option.some(iter.next()) : Option.none());
	}
	
	public static <T> FStream<T> of(MultipleSupplier<? extends T> supplier) {
		Objects.requireNonNull(supplier);
		
		return of(Utilities.toIterator(supplier));
	}
	
	public static <T> FStream<T> of(Stream<? extends T> stream) {
		Objects.requireNonNull(stream);
		
		return of(stream.iterator());
	}
	
	public static <S,T> FStream<T> unfold(S init,
									Function<? super S,Tuple2<T,S>> generator) {
		Objects.requireNonNull(init);
		Objects.requireNonNull(generator);
		
		return new FStreams.UnfoldStream<T, S>(init, generator);
	}
	
	public static <T> FStream<T> generate(T init, Function<? super T,? extends T> inc) {
		Preconditions.checkArgument(init != null, "init is null");
		Preconditions.checkArgument(inc != null, "inc is null");
		
		return new FStreams.GeneratedStream<>(init, inc);
	}
	
	public default Try<Void> closeQuietly() {
		return Try.run(()->close());
	}
	
	public static IntFStream range(int start, int end) {
		return new IntFStream.RangedStream(start, end, false);
	}
	public static IntFStream rangeClosed(int start, int end) {
		return new IntFStream.RangedStream(start, end, true);
	}
	
	public default FStream<T> take(long count) {
		Preconditions.checkArgument(count >= 0, "count < 0");
		
		return new FStreams.TakenStream<>(this, count);
	}
	public default FStream<T> drop(long count) {
		Preconditions.checkArgument(count >= 0, "count < 0");
		
		return new FStreams.DroppedStream<>(this, count);
	}

	public default FStream<T> takeWhile(Predicate<? super T> pred) {
		Objects.requireNonNull(pred);
		
		return new FStreams.TakeWhileStream<>(this, pred);
	}
	public default FStream<T> dropWhile(Predicate<? super T> pred) {
		Objects.requireNonNull(pred);
		
		return new FStreams.DropWhileStream<>(this, pred);
	}
	
	public default FStream<T> sample(double ratio) {
		Preconditions.checkArgument(ratio >= 0, "sample ration must be larger or equal to zero");
		
		return new FStreams.SampledStream<>(this, ratio);
	}
	
	public default FStream<T> filter(Predicate<? super T> pred) {
		Objects.requireNonNull(pred);
		
		return new FilteredStream<>(this, pred);
	}
	
	public default <S> FStream<S> map(Function<? super T,? extends S> mapper) {
		Objects.requireNonNull(mapper);
		
		return new MappedStream<>(this, mapper);
	}
	
	public default FStream<T> transformIf(boolean flag, Function<FStream<T>,FStream<T>> mapper) {
		if ( flag ) {
			return mapper.apply(this);
		}
		else {
			return this;
		}
	}
	
	public default IntFStream mapToInt(Function<? super T, Integer> mapper) {
		Objects.requireNonNull(mapper);
		
		return new MapToIntStream<>(this, mapper);
	}
	
	public default LongFStream mapToLong(Function<? super T, Long> mapper) {
		Objects.requireNonNull(mapper);
		
		return new MapToLongStream<>(this, mapper);
	}
	
	public default DoubleFStream mapToDouble(Function<? super T, Double> mapper) {
		Objects.requireNonNull(mapper);
		
		return new MapToDoubleStream<>(this, mapper);
	}
	
	public default <V> FStream<V> cast(Class<? extends V> cls) {
		Objects.requireNonNull(cls, "target class is null");
		
		return map(cls::cast);
	}
	
	public default <V> FStream<V> castSafely(Class<? extends V> cls) {
		Objects.requireNonNull(cls, "target class is null");
		
		return filter(cls::isInstance).map(cls::cast);
	}
	
	public default <V> FStream<V> ofExactClass(Class<? extends V> cls) {
		Objects.requireNonNull(cls, "target class is null");
		
		return filter(v -> v.getClass().equals(cls)).map(cls::cast);
	}
	
	public default FStream<T> peek(Consumer<? super T> effect) {
		Objects.requireNonNull(effect, "peek effect is null");
		
		return new PeekedStream<>(this, effect);
	}
	
	public default <V> FStream<V> flatMap(Function<? super T,? extends FStream<? extends V>> mapper) {
		Objects.requireNonNull(mapper, "mapper is null");
		
		return new FlatMappedStream<>(this, mapper);
	}
	
	public default <V> FStream<V> flatMapOption(Function<? super T,Option<V>> mapper) {
		Objects.requireNonNull(mapper, "mapper is null");

		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapIterable(Function<? super T,Iterable<V>> mapper) {
		Objects.requireNonNull(mapper, "mapper is null");
		
		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapStream(Function<? super T,Stream<V>> mapper) {
		Objects.requireNonNull(mapper, "mapper is null");
		
		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default boolean exists(Predicate<? super T> pred) {
		Objects.requireNonNull(pred, "predicate is null");
		
		return foldLeft(false, true, (a,t) -> { 
			try {
				return pred.test(t);
			}
			catch ( Exception e ) {
				return false;
			}
		});
	}
	public default boolean forAll(Predicate<? super T> pred) {
		Objects.requireNonNull(pred, "predicate");
		
		return foldLeft(true, false, (a,t) -> { 
			try {
				return pred.test(t);
			}
			catch ( Throwable e ) {
				return false;
			}
		});
	}
	
	public default <S> S foldLeft(S accum, BiFunction<? super S,? super T,? extends S> folder) {
		Objects.requireNonNull(folder);
		
		T next;
		while ( (next = next()) != null ) {
			accum = folder.apply(accum, next);
		}
		
		return accum;
	}
	
	public default <S> S foldLeft(S accum, S stopper,
								BiFunction<? super S,? super T,? extends S> folder) {
		Preconditions.checkArgument(accum != null, "accum is null");
		Preconditions.checkArgument(folder != null, "folder is null");
		
		if ( accum.equals(stopper) ) {
			return accum;
		}
		
		T next;
		while ( (next = next()) != null ) {
			accum = folder.apply(accum, next);
			if ( accum.equals(stopper) ) {
				return accum;
			}
		}
		
		return accum;
	}
	
	public default <S> S foldRight(S accum, BiFunction<? super T,? super S,? extends S> folder) {
		return FLists.foldRight(toList(), accum, folder);
	}
	
	public default FStream<T> scan(BinaryOperator<T> combiner) {
		Objects.requireNonNull(combiner);
		
		return new ScannedStream<>(this, combiner);
	}
	
	public default T reduce(BiFunction<? super T,? super T,? extends T> reducer) {
		Preconditions.checkArgument(reducer != null, "reducer is null");
		
		T next = next();
		if ( next != null ) {
			return foldLeft(next, (a,t) -> reducer.apply(a, t));
		}
		else {
			throw new IllegalStateException("Stream is empty");
		}
	}
	
	public default <S> S collectLeft(S collector, BiConsumer<? super S,? super T> collect) {
		Objects.requireNonNull(collector);
		Objects.requireNonNull(collect);
		
		T next;
		while ( (next = next()) != null ) {
			collect.accept(collector, next);
		}
		closeQuietly();
		
		return collector;
	}
	
	public default long count() {
		long count = 0;
		while ( next() != null ) {
			++count;
		}
		
		return count;
	}
	
	public default boolean anyMatch(Predicate<? super T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		T next;
		while ( (next = next()) != null && !pred.test(next) );
		return next != null;
	}
	
	public default Option<T> first() {
		return Option.of(next());
	}
	
	public default Option<T> last() {
		T last = null;
		
		T next;
		while ( (next = next()) != null ) {
			last = next;
		}
		
		return Option.of(last);
	}
	
	public default FStream<T> concatWith(FStream<? extends T> tail) {
		Preconditions.checkArgument(tail != null, "tail is null");
		
		return new FStreams.AppendedStream<>(this, tail);
	}
	
	public default FStream<T> concatWith(T tail) {
		Preconditions.checkArgument(tail != null, "tail is null");
		
		return concatWith(FStream.of(tail));
	}
	
	public static <T> FStream<T> concat(FStream<? extends T> head, FStream<? extends T> tail) {
		Preconditions.checkArgument(head != null, "head is null");
		Preconditions.checkArgument(tail != null, "tail is null");
		
		return new FStreams.AppendedStream<>(head, tail);
	}
	
	public default FStream<Tuple2<T,Integer>> zipWithIndex() {
		return zipWith(range(0,Integer.MAX_VALUE));
	}
	
	public default <U> FStream<Tuple2<T,U>> zipWith(FStream<? extends U> other) {
		return new ZippedFStream<>(this, other);
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
	
	public default HashSet<T> toHashSet() {
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
	
	public default int[] toIntArray() {
		List<T> list = toList();
		int[] array = new int[list.size()];
		for ( int i =0; i < array.length; ++i ) {
			array[i] = (Integer)list.get(i);
		}
		
		return array;
	}
	
	public default T[] toArray(Class<T> componentType) {
		List<T> list = toList();
		@SuppressWarnings("unchecked")
		T[] array = (T[])Array.newInstance(componentType, list.size());
		return list.toArray(array);
	}
	
	public default PeekableFStream<T> toPeekable() {
		return new PeekableFStream<>(this);
	}
	
	public default FStream<List<T>> buffer(int count, int skip) {
		return new BufferedStream<>(this, count, skip);
	}
	
	public default <K> KVFStream<K,T> toKVFStream(Function<? super T,? extends K> keyGen) {
		return new KeyGeneratedKVFStream<>(this, keyGen);
	}
	
	public default <K,V> KVFStream<K,V> toKVFStream(Function<? super T,? extends K> keyGen,
													Function<? super T,? extends V> valueGen) {
		return new KeyValueGeneratedKVFStream<>(this, keyGen, valueGen);
	}
	
//	@SuppressWarnings("unchecked")
//	public default <K,V> KVFStream<K,V> toKVFStream() {
//		return new KVFStreamImpl<>(
//				() -> next().map(t -> {
//					if ( !(t instanceof Tuple2) ) {
//						throw new IllegalStateException("source FStream is not a stream of Tuple2: "
//														+ t.getClass());
//					}
//					
//					Tuple2<K,V> tuple = (Tuple2<K,V>)t;
//					return new KeyValue<>(tuple._1, tuple._2);
//				}),
//				() -> close()
//			);
//	}
	
	public default Supplier<T> toSupplier() {
		return () -> next();
	}
	
/*
	@SuppressWarnings("unchecked")
	public static <T,K,V> FStream<Tuple2<K,V>> toTupleStream(FStream<T> stream) {
		Option<Tuple2<T,FStream<T>>> otuple = stream.peekFirst();
		if ( otuple.isEmpty() ) {
			return FStream.empty();
		}
		
		Tuple2<T,FStream<T>> tuple = otuple.get();
		if ( !(tuple._1 instanceof Tuple2) ) {
			throw new IllegalStateException("not Tuple2 FStream: this=" + stream);
		}
		
		FStream<T> stream2 = otuple.get()._2;
		return () -> (Option<Tuple2<K,V>>)stream2.next();
	}
	
	public default <K,V> Map<K,V> toHashMap() {
		return toMap(Maps.newHashMap());
	}
	
	public default <K, V, S extends Map<K,V>> S toMap(S map) {
		return FStream.<T,K,V>toTupleStream(this)
					.foldLeft(map, (accum,t) -> {
						accum.put(t._1, t._2);
						return accum;
					});
	}
*/
	
	public default Stream<T> stream() {
		return Utilities.stream(iterator());
	}
	
	public default Observable<T> observe() {
		return Observable.create(new FStreamSubscriber<>(this));
	}
	
	public default void forEach(Consumer<? super T> effect) {
		Preconditions.checkArgument(effect != null, "effect is null");
		
		T next;
		while ( (next = next()) != null ) {
			effect.accept(next);
		}
	}
	
	public default void forEachIE(CheckedConsumer<? super T> effect) {
		Preconditions.checkArgument(effect != null, "effect is null");

		T next;
		while ( (next = next()) != null ) {
			try {
				effect.accept(next);
			}
			catch ( Throwable ignored ) { }
		}
	}
	
	public default <K> KVFStream<K,T> reduceByKey(Function<? super T,? extends K> keyer,
												BiFunction<? super T,? super T,? extends T> reducer) {
		Map<K,T> accums = Maps.newHashMap();
		
		T value;
		while ( (value = next()) != null ) {
			K key = keyer.apply(value);
			final T v = value;
			accums.compute(key, (k,old) -> (old != null) ? reducer.apply(old, v) : v);
		}
		
		return KVFStream.of(accums);
	}
	
	public default <K,S> KVFStream<K,S> foldLeftByKey(Function<? super T,? extends K> keyer,
													Function<? super K,? extends S> accumInitializer,
													BiFunction<? super S,? super T,? extends S> folder) {
		Map<K,S> accums = Maps.newHashMap();

		T value;
		while ( (value = next()) != null ) {
			K key = keyer.apply(value);
			final T v = value;
					
			accums.compute(key, (k,accum) -> {
				if ( accum == null ) {
					accum = accumInitializer.apply(k);
				}
				return folder.apply(accum, v);
			});
		}
		
		return KVFStream.of(accums);
	}
	
	public default <K> KeyedGroups<K,T> groupBy(Function<? super T,? extends K> keyer) {
		return collectLeft(KeyedGroups.create(), (g,t) -> g.add(keyer.apply(t), t));
	}
	
	public default <K,V> KeyedGroups<K,V> groupBy(Function<? super T,? extends K> keySelector,
											Function<? super T,? extends V> valueSelector) {
		return collectLeft(KeyedGroups.create(),
							(g,t) -> g.add(keySelector.apply(t), valueSelector.apply(t)));
	}
	
	public default <K,V> KeyedGroups<K,V> groupByKeyValue(Function<? super T,KeyValue<K,V>> selector) {
		return collectLeft(KeyedGroups.create(),
							(g,t) -> {
								KeyValue<K,V> kv = selector.apply(t);
								g.add(kv.key(), kv.value());
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
		return of(list);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default FStream<T> sort() {
		return sort((t1,t2) -> ((Comparable)t1).compareTo(t2));
	}
	
    public default FStream<T> takeTopK(int k, Comparator<? super T> cmp) {
        return new TopKPickedFStream<>(this, k, cmp);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public default FStream<T> takeTopK(int k) {
        return new TopKPickedFStream<>(this, k, (t1,t2) -> ((Comparable)t1).compareTo(t2));
    }

	
	@SuppressWarnings("unchecked")
	public default List<T> max() {
		Comparable<T> max = null;
		List<T> maxList = Lists.newArrayList();
		
		T next;
		while ( (next = next()) != null ) {
			if ( max != null ) {
				int cmp = max.compareTo(next);
				
				if ( cmp < 0 ) {
					max = (Comparable<T>)next;
					maxList.clear();
					maxList.add(next);
				}
				else if ( cmp == 0 ) {
					maxList.add(next);
				}
			}
			else {
				if ( !(next instanceof Comparable) ) {
					throw new IllegalStateException("FStream elements are not Comparable");
				}
				
				max = (Comparable<T>)next;
				maxList.add(next);
			}
		}

		return maxList;
	}

	@SuppressWarnings("unchecked")
	public default List<T> min() {
		Comparable<T> min = null;
		List<T> minList = Lists.newArrayList();
		
		T next;
		while ( (next = next()) != null ) {
			if ( min != null ) {
				int cmp = min.compareTo(next);
				if ( cmp > 0 ) {
					min = (Comparable<T>)next;
					minList.clear();
					minList.add(next);
				}
				else if ( cmp == 0 ) {
					minList.add(next);
				}
			}
			else {
				if ( !(next instanceof Comparable) ) {
					throw new IllegalStateException("FStream elements are not Comparable");
				}
				
				min = (Comparable<T>)next;
				minList.add(next);
			}
		}
		
		return minList;
	}
	
	public default <K extends Comparable<K>> List<T> max(Function<? super T,? extends K> keySelector) {
		T max = null;
		K maxKey = null;
		List<T> maxList = Lists.newArrayList();
		
		T next;
		while ( (next = next()) != null ) {
			K key = keySelector.apply(next);

			if ( max != null ) {
				int cmp = maxKey.compareTo(key);
				if ( cmp < 0 ) {
					max = next;
					maxList.clear();
					maxList.add(next);
					maxKey = key;
				}
				else if ( cmp == 0 ) {
					maxList.add(next);
				}
			}
			else {
				max = next;
				maxList.clear();
				maxList.add(next);
				maxKey = key;
			}
		}
		
		return maxList;
	}
	
	public default <K extends Comparable<K>> List<T> min(Function<? super T,? extends K> keySelector) {
		T min = null;
		K minKey = null;
		List<T> minList = Lists.newArrayList();
		
		T next;
		while ( (next = next()) != null ) {
			K key = keySelector.apply(next);

			if ( min != null ) {
				int cmp = minKey.compareTo(key);
				if ( cmp > 0 ) {
					min = next;
					minList.clear();
					minList.add(next);
					minKey = key;
				}
				else if ( cmp == 0 ) {
					minList.add(next);
				}
			}
			else {
				min = next;
				minList.clear();
				minList.add(next);
				minKey = key;
			}
		}
		
		return minList;
	}
	
	public default Option<T> max(Comparator<? super T> cmp) {
		Option<T> max = Option.none();
		
		T next;
		while ( (next = next()) != null ) {
			if ( max.isDefined() ) {
				if ( cmp.compare(next, max.get()) > 0 ) {
					max = Option.some(next);
				}
			}
			else {
				max = Option.some(next);
			}
		}
		
		return max;
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

	public default boolean startsWith(FStream<T> subList) {
		Preconditions.checkArgument(subList != null, "subList is null");
		
		T subNext = subList.next();
		T next = next();
		while ( subNext != null && next != null ) {
			if ( !subNext.equals(next) ) {
				return false;
			}
			
			subNext = subList.next();
			next = next();
		}
		
		return (next != null && subNext == null);
	}
	
	public default FStream<T> distinct() {
		return FStream.of(toCollection(Sets.newHashSet()));
	}
}
