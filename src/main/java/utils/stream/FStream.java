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
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.reactivex.Observable;
import io.vavr.CheckedConsumer;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import utils.CSV;
import utils.KeyValue;
import utils.Utilities;
import utils.func.FLists;
import utils.func.FOption;
import utils.stream.FStreams.FilteredStream;
import utils.stream.FStreams.MapToDoubleStream;
import utils.stream.FStreams.MapToIntStream;
import utils.stream.FStreams.MapToLongStream;
import utils.stream.FStreams.MappedStream;
import utils.stream.FStreams.PeekedStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FStream<T> extends AutoCloseable {
	/**
	 * 스트림에 포함된 다음 데이터를 반환한다.
	 * <p>
	 * 더 이상의 데이터가 없는 경우, 또는 이미 close된 경우에는 {@link FOption#empty()}을 반환함.
	 * 
	 * @return	다음 데이터. 없는 경우는 {@link FOption#empty()}.
	 */
	public FOption<T> next();
	
	@SuppressWarnings("unchecked")
	public static <T> FStream<T> empty() {
		return FStreams.EMPTY;
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
		Utilities.checkNotNullArguments(values, "at least one of values is null");
		
		return from(Arrays.asList(values));
	}
	
	public static <T> FStream<T> of(Try<? extends T> tried) {
		Utilities.checkNotNullArgument(tried, "Try is null");
		
		return Try.<T>narrow(tried).map(FStream::of).getOrElse(FStream::empty);
	}
	
	public static <T> FStream<T> from(Iterable<? extends T> values) {
		Utilities.checkNotNullArgument(values, "Iterable is null");
		
		return from(values.iterator());
	}
	
	public static <T> FStream<T> from(Iterator<? extends T> iter) {
		Utilities.checkNotNullArgument(iter, "Iterator is null");
		
		return new FStreams.IteratorStream<>(iter);
	}
	
	public static <T> FStream<T> from(Stream<? extends T> stream) {
		Utilities.checkNotNullArgument(stream, "Stream is null");
		
		return from(stream.iterator());
	}
	
	public static <T> FStream<T> from(Observable<? extends T> ob) {
		Utilities.checkNotNullArgument(ob, "Observable is null");
		
		return new ObservableStream<>(ob);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> FStream<T> narrow(FStream<? extends T> stream) {
		Utilities.checkNotNullArgument(stream, "stream is null");
		
		return (FStream<T>)stream;
	}
	
	public static <T> SuppliableFStream<T> pipe(int length) {
		Utilities.checkArgument(length > 0, "length > 0: but=" + length);
		
		return new SuppliableFStream<>(length);
	}
	
	public static <S,T> FStream<T> unfold(S init,
										Function<? super S,Tuple2<? extends S,T>> gen) {
		Utilities.checkNotNullArgument(init, "initial value is null");
		Utilities.checkNotNullArgument(gen, "next value generator is null");
		
		return new FStreams.UnfoldStream<>(init, gen);
	}
	
	public static <T> FStream<T> generate(T init, Function<? super T,? extends T> inc) {
		Utilities.checkNotNullArgument(init, "initial value is null");
		Utilities.checkNotNullArgument(inc, "next value generator is null");
		
		return new FStreams.GeneratedStream<>(init, inc);
	}
	
	public default Try<Void> closeQuietly() {
		return Try.run(this::close);
	}
	
	public static IntFStream range(int start, int end) {
		return new IntFStream.RangedStream(start, end, false);
	}
	public static IntFStream rangeClosed(int start, int end) {
		return new IntFStream.RangedStream(start, end, true);
	}
	
	/**
	 * 스트림의 첫 count개의 데이터로 구성된 FStream 객체를 생성한다.
	 * 
	 * @param count	데이터 갯수.
	 * @return	'count' 개의  데이터로 구성된 스트림 객체.
	 */
	public default FStream<T> take(long count) {
		Utilities.checkArgument(count >= 0, "count >= 0: but: " + count);
		
		return new FStreams.TakenStream<>(this, count);
	}
	public default FStream<T> drop(long count) {
		Utilities.checkArgument(count >= 0, "count >= 0: but: " + count);
		
		return new FStreams.DroppedStream<>(this, count);
	}

	public default FStream<T> takeWhile(Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		return new FStreams.TakeWhileStream<>(this, pred);
	}
	public default FStream<T> dropWhile(Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		return new FStreams.DropWhileStream<>(this, pred);
	}
	
	public default FStream<T> sample(double ratio) {
		Utilities.checkArgument(ratio >= 0, "ratio >= 0");
		
		return new FStreams.SampledStream<>(this, ratio);
	}
	
	public default FStream<T> filter(Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		return new FilteredStream<>(this, pred);
	}
	
	public default <S> FStream<S> map(Function<? super T,? extends S> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return new MappedStream<>(this, mapper);
	}
	
	public default FStream<T> mapIf(boolean flag, Function<FStream<T>,FStream<T>> mapper) {
		if ( flag ) {
			return mapper.apply(this);
		}
		else {
			return this;
		}
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
		
		return new FlatMappedStream<>(this, mapper);
	}
	
	public default <V> FStream<V> flatMapParallel(Function<? super T,? extends FStream<V>> mapper,
													int parallelLevel) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		Utilities.checkArgument(parallelLevel > 0, "parallelLevel > 0, but: " + parallelLevel);
		
		return mergeParallel(map(mapper), parallelLevel);
	}
	
	public default <V> FStream<V> flatMapOption(Function<? super T,FOption<V>> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");

		return flatMap(t -> mapper.apply(t).stream());
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

		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default boolean exists() {
		return next().isPresent();
	}
	
	public default boolean exists(Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate");
		
		// same to the below.
		// return foldLeft(false, true, (a,v) -> a || pred.test(v));
		for ( FOption<T> next = next(); next.isPresent(); next = next() ) {
			if ( pred.test(next.getUnchecked()) ) {
				return true;
			}
		}
		
		return false;
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
		
		FOption<T> next;
		while ( (next = next()).isPresent() ) {
			accum = folder.apply(accum, next.get());
		}
		
		return accum;
	}
	
	public default <S> S foldLeft(S accum, S stopper,
								BiFunction<? super S,? super T,? extends S> folder) {
		Utilities.checkNotNullArgument(accum, "accum is null");
		Utilities.checkNotNullArgument(folder, "folder is null");
		
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
		
		FOption<T> next;
		while ( (next = next()).isPresent() ) {
			collect.accept(accum, next.get());
		}
		
		return accum;
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

	public default FOption<T> findNext(Predicate<? super T> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		FOption<T> next;
		while ( (next = next()).isPresent() && !pred.test(next.get()) );
		return next;
	}
	
	public default FOption<T> last() {
		FOption<T> last = FOption.empty();
		
		FOption<T> next;
		while ( (next = next()).isPresent() ) {
			last = next;
		}
		
		return last;
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
	
	public static <T> FStream<T> concat(FStream<? extends FStream<? extends T>> fact) {
		Utilities.checkNotNullArgument(fact, "source FStream factory");
		
		return new ConcatedStream<>(fact);
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
	
	public default void forEach(Consumer<? super T> effect) {
		Utilities.checkNotNullArgument(effect, "effect is null");
		
		FOption<T> next;
		while ( (next = next()).isPresent() ) {
			effect.accept(next.get());
		}
	}
	
	public default void forEachAE(CheckedConsumer<? super T> effect)
		throws Throwable {
		Utilities.checkNotNullArgument(effect, "effect is null");
		
		FOption<T> next;
		while ( (next = next()).isPresent() ) {
			effect.accept(next.get());
		}
	}
	
	public default void forEachIE(CheckedConsumer<? super T> effect) {
		Utilities.checkNotNullArgument(effect, "effect is null");
		
		forEach(v -> Try.run(() -> effect.accept(v)));
	}
	
	/**
	 * 주어진 키에 해당하는 데이터별로 reduce작업을 수행한다.
	 * <p>
	 * 수행된 결과는 key별로 {@link Map}에 저장되어 반환된다.
	 * 
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
	
	public default <K> FStream<KeyedFStream<K,T>> groupBy(Function<? super T,? extends K> grouper) {
		return new GroupByStream<>(this, grouper);
	}
	
	public default PrefetchStream<T> prefetched(int count) {
		return new PrefetchStream<>(this, count);
	}
	
	public default FStream<T> delay(long delay, TimeUnit tu) {
		return new FStreams.DelayedStream<>(this, delay, tu);
	}
}
