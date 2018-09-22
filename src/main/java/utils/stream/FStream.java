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
import utils.Unchecked;
import utils.Utilities;
import utils.func.FLists;
import utils.func.MultipleSupplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FStream<T> extends AutoCloseable {
	public Option<T> next();
	
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
									Function<? super S,Option<Tuple2<T,S>>> generator) {
		Preconditions.checkArgument(init != null, "init is null");
		Preconditions.checkArgument(generator != null, "generator is null");
		
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
		return new FStreams.TakenStream<>(this, count);
	}
	public default FStream<T> drop(long count) {
		return new FStreams.DroppedStream<>(this, count);
	}

	public default FStream<T> takeWhile(Predicate<? super T> pred) {
		return new FStreams.TakeWhileStream<>(this, pred);
	}
	public default FStream<T> dropWhile(Predicate<? super T> pred) {
		return new FStreams.DropWhileStream<>(this, pred);
	}
	
	public default FStreamImpl<T> filter(Predicate<? super T> pred) {
		Objects.requireNonNull(pred);
		
		Predicate<? super T> negated = pred.negate();
		return new FStreamImpl<>(
			"filter",
			() -> {
				Option<T> next;
				while ( (next = next()).filter(negated).isDefined() );
				return next;
			},
			() -> close()
		);
	}
	
	public default <S> FStream<S> map(Function<? super T,? extends S> mapper) {
		Objects.requireNonNull(mapper);
		
		return new FStreamImpl<>(
			"map",
			() -> next().map(mapper),
			() -> close()
		);
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
		
		return new IntFStreamImpl(
			"mapToInt",
			() -> next().map(mapper),
			() -> close()
		);
	}
	
	public default LongFStream mapToLong(Function<? super T, Long> mapper) {
		Objects.requireNonNull(mapper);
		
		return new LongFStreamImpl(
			"mapToLong",
			() -> next().map(mapper),
			() -> close()
		);
	}
	
	public default DoubleFStream mapToDouble(Function<? super T, Double> mapper) {
		Objects.requireNonNull(mapper);
		
		return new DoubleFStreamImpl(
			"mapToDouble",
			() -> next().map(mapper),
			() -> close()
		);
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
	
	public default FStream<T> peek(Consumer<? super T> consumer) {
		Objects.requireNonNull(consumer);
		
		return new FStreamImpl<> (
			"peek",
			() -> next().peek(consumer),
			() -> close()
		);
	}
	
	public default <V> FStream<V> flatMap(Function<? super T,? extends FStream<? extends V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return new FlatMappedStream<>(this, mapper);
	}
	
	public default <V> FStream<V> flatMapOption(Function<? super T,Option<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapIterable(Function<? super T,Iterable<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapStream(Function<? super T,Stream<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default boolean exists(Predicate<? super T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
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
		Preconditions.checkArgument(pred != null, "pred is null");
		
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
//		Preconditions.checkArgument(accum != null, "accum is null");
		Objects.requireNonNull(folder);
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			accum = folder.apply(accum, next.get());
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
		
		Option<T> next = next();
		while ( (next = next()).isDefined() ) {
			accum = folder.apply(accum, next.get());
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
		return new ScannedStream<>(this, combiner);
	}
	
	public default T reduce(BiFunction<? super T,? super T,? extends T> reducer) {
		Preconditions.checkArgument(reducer != null, "reducer is null");
		
		Option<T> next = next();
		if ( next.isDefined() ) {
			return foldLeft(next.get(), (a,t) -> reducer.apply(a, t));
		}
		else {
			throw new IllegalStateException("Stream is empty");
		}
	}
	
	public default <S> S collectLeft(S collector, BiConsumer<? super S,? super T> collect) {
		Objects.requireNonNull(collector);
		Objects.requireNonNull(collect);
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			collect.accept(collector, next.get());
		}
		Unchecked.runRTE(this::close);
		
		return collector;
	}
	
	public default long count() {
		long count = 0;
		while ( next().isDefined() ) {
			++count;
		}
		
		return count;
	}
	
	public default boolean anyMatch(Predicate<? super T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");

		Predicate<? super T> negated = pred.negate();
		Option<T> next;
		while ( (next = next()).filter(negated).isDefined() );
		
		return next.isDefined();
	}
	
	public default Option<T> first() {
		List<T> list = take(1).toList();
		return list.isEmpty() ? Option.none() : Option.some(list.get(0));
	}
	
	public default Option<T> last() {
		Option<T> last = Option.none();
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			last = next;
		}
		
		return last;
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
		return new KVFStreamImpl<>(
			"toKVFStream",
			() -> next().map(t -> new KeyValue<>(keyGen.apply(t), t)),
			() -> close()
		);
	}
	
	public default <K,V> KVFStream<K,V> toKVFStream(Function<? super T,? extends K> keyGen,
													Function<? super T,? extends V> valueGen) {
		return new KVFStreamImpl<>(
			"toKVFStream",
			() -> next().map(t -> KeyValue.of(keyGen.apply(t), valueGen.apply(t))),
			() -> close()
		);
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
	
	public default MultipleSupplier<T> toSupplier() {
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
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			effect.accept(next.get());
		}
	}
	
	public default void forEachIE(CheckedConsumer<? super T> effect) {
		Preconditions.checkArgument(effect != null, "effect is null");
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			try {
				effect.accept(next.get());
			}
			catch ( Throwable ignored ) { }
		}
	}
	
	public default <K> KVFStream<K,T> reduceByKey(Function<? super T,? extends K> keyer,
												BiFunction<? super T,? super T,? extends T> reducer) {
		Map<K,T> accums = Maps.newHashMap();
		
		Option<T> ovalue;
		while ( (ovalue = next()).isDefined() ) {
			T value = ovalue.get();
			K key = keyer.apply(value);
			accums.compute(key, (k,old) -> (old != null) ? reducer.apply(old, value) : value);
		}
		
		return KVFStream.of(accums);
	}
	
	public default <K,S> KVFStream<K,S> foldLeftByKey(Function<? super T,? extends K> keyer,
													Function<? super K,? extends S> accumInitializer,
													BiFunction<? super S,? super T,? extends S> folder) {
		Map<K,S> accums = Maps.newHashMap();
		
		Option<T> ovalue;
		while ( (ovalue = next()).isDefined() ) {
			T value = ovalue.get();
			K key = keyer.apply(value);
					
			accums.compute(key, (k,accum) -> {
				if ( accum == null ) {
					accum = accumInitializer.apply(k);
				}
				return folder.apply(accum, value);
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
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			T data = next.get();
			
			if ( max != null ) {
				int cmp = max.compareTo(data);
				
				if ( cmp < 0 ) {
					max = (Comparable<T>)data;
					maxList.clear();
					maxList.add(data);
				}
				else if ( cmp == 0 ) {
					maxList.add(data);
				}
			}
			else {
				if ( !(data instanceof Comparable) ) {
					throw new IllegalStateException("FStream elements are not Comparable");
				}
				
				max = (Comparable<T>)data;
				maxList.add(data);
			}
		}

		return maxList;
	}

	@SuppressWarnings("unchecked")
	public default List<T> min() {
		Comparable<T> min = null;
		List<T> minList = Lists.newArrayList();
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			T data = next.get();
			
			if ( min != null ) {
				int cmp = min.compareTo(data);
				if ( cmp > 0 ) {
					min = (Comparable<T>)data;
					minList.clear();
					minList.add(data);
				}
				else if ( cmp == 0 ) {
					minList.add(data);
				}
			}
			else {
				if ( !(data instanceof Comparable) ) {
					throw new IllegalStateException("FStream elements are not Comparable");
				}
				
				min = (Comparable<T>)data;
				minList.add(data);
			}
		}
		
		return minList;
	}
	
	public default <K extends Comparable<K>> List<T> max(Function<? super T,? extends K> keySelector) {
		T max = null;
		K maxKey = null;
		List<T> maxList = Lists.newArrayList();
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			T data = next.get();
			K key = keySelector.apply(data);

			if ( max != null ) {
				int cmp = maxKey.compareTo(key);
				if ( cmp < 0 ) {
					max = data;
					maxList.clear();
					maxList.add(data);
					maxKey = key;
				}
				else if ( cmp == 0 ) {
					maxList.add(data);
				}
			}
			else {
				max = data;
				maxList.clear();
				maxList.add(data);
				maxKey = key;
			}
		}
		
		return maxList;
	}
	
	public default <K extends Comparable<K>> List<T> min(Function<? super T,? extends K> keySelector) {
		T min = null;
		K minKey = null;
		List<T> minList = Lists.newArrayList();
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			T data = next.get();
			K key = keySelector.apply(data);

			if ( min != null ) {
				int cmp = minKey.compareTo(key);
				if ( cmp > 0 ) {
					min = data;
					minList.clear();
					minList.add(data);
					minKey = key;
				}
				else if ( cmp == 0 ) {
					minList.add(data);
				}
			}
			else {
				min = data;
				minList.clear();
				minList.add(data);
				minKey = key;
			}
		}
		
		return minList;
	}
	
	public default Option<T> max(Comparator<? super T> cmp) {
		Option<T> max = Option.none();
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			if ( max.isDefined() ) {
				if ( cmp.compare(next.get(), max.get()) > 0 ) {
					max = next;
				}
			}
			else {
				max = next;
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
		
		Option<T> subNext = subList.next();
		Option<T> next = next();
		while ( subNext.isDefined() && next.isDefined() ) {
			if ( !subNext.get().equals(next.get()) ) {
				return false;
			}
			
			subNext = subList.next();
			next = next();
		}
		
		return (next.isDefined() && subNext.isEmpty());
	}
	
	public default FStream<T> distinct() {
		return FStream.of(toCollection(Sets.newHashSet()));
	}
}
