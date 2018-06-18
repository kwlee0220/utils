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
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import io.reactivex.Observable;
import io.vavr.CheckedConsumer;
import io.vavr.Function2;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import utils.Utilities;
import utils.func.FLists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FStream<T> {
	public Option<T> next();
	
	@SuppressWarnings("unchecked")
	public static <T> FStream<T> empty() {
		return Streams.EMPTY;
	}
	
	@SafeVarargs
	public static <T> FStream<T> of(T... values) {
		return of(Arrays.asList(values));
	}
	public static FStream<Integer> of(int... values) {
		return of(Ints.asList(values));
	}
	
	public static <T> FStream<T> of(Option<T> opt) {
		return opt.map(t -> FStream.of(t))
					.getOrElse(FStream.empty());
	}
	
	public static <T> FStream<T> of(Iterable<T> values) {
		return of(values.iterator());
	}
	
	public static <T> FStream<T> of(Iterator<T> iter) {
		return () -> iter.hasNext() ? Option.some(iter.next()) : Option.none();
	}
	
	public static <T> FStream<T> of(Stream<T> strm) {
		return of(strm.iterator());
	}
	
	public static <T> FStream<T> of(Supplier<T> supplier) {
		return of(Utilities.toIterator(supplier));
	}
	
	public static <T> FStream<T> from(Stream<T> stream) {
		return of(stream.iterator());
	}
	
	public static <K,V> FStream<Tuple2<K,V>> of(Map<K,V> map) {
		return of(map.entrySet())
					.map(ent -> Tuple.of(ent.getKey(), ent.getValue()));
	}
	
	public static <S,T> FStream<T> unfold(S init, Function<S,Option<Tuple2<T,S>>> generator) {
		Preconditions.checkArgument(init != null, "init is null");
		Preconditions.checkArgument(generator != null, "generator is null");
		
		return new Streams.UnfoldStream<T, S>(init, generator);
	}
	
	public static <T> FStream<T> generate(T init, Function<T,T> inc) {
		Preconditions.checkArgument(init != null, "init is null");
		Preconditions.checkArgument(inc != null, "inc is null");
		
		return new Streams.GeneratedStream<>(init, inc);
	}
	
	public static FStream<Integer> range(int start, int end) {
		return new Streams.RangedStream(start, end, false);
	}
	public static FStream<Integer> rangeClosed(int start, int end) {
		return new Streams.RangedStream(start, end, true);
	}
	
	public default FStream<T> take(long count) {
		Preconditions.checkArgument(count >= 0, "count < 0");
		return new Streams.TakenStream<>(this, count);
	}
	public default FStream<T> drop(long count) {
		Preconditions.checkArgument(count >= 0, "count < 0");
		return new Streams.DroppedStream<>(this, count);
	}

	public default FStream<T> takeWhile(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		return new Streams.TakeWhileStream<>(this, pred);
	}
	public default FStream<T> dropWhile(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		return new Streams.DropWhileStream<>(this, pred);
	}
	
	public default FStream<T> filter(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		Predicate<T> negated = pred.negate();
		return () -> {
			Option<T> next;
			while ( (next = next()).filter(negated).isDefined() );
			return next;
		};
	}
	
	public default <S> FStream<S> map(Function<T,S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return () -> next().map(mapper);
	}
	
	public default <V> FStream<V> cast(Class<V> cls) {
		Preconditions.checkNotNull(cls, "target class is null");
		
		return () -> next().map(cls::cast);
	}
	
	public default <V> FStream<V> castSafely(Class<V> cls) {
		Preconditions.checkArgument(cls != null, "target class is null");
		
		return () -> next().filter(cls::isInstance)
							.map(cls::cast);
	}
	
	public default FStream<T> peek(Consumer<? super T> consumer) {
		Preconditions.checkArgument(consumer != null, "consumer is null");
		
		return () -> next().peek(consumer);
	}
	
	public default <V> FStream<V> flatMap(Function<T,FStream<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return new FlatMappedStream<>(this, mapper);
	}
	
	public default <V> FStream<V> flatMapOption(Function<T,Option<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapIterable(Function<T,Iterable<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default <V> FStream<V> flatMapStream(Function<T,Stream<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return flatMap(t -> FStream.of(mapper.apply(t)));
	}
	
	public default boolean exists(Predicate<T> pred) {
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
	public default boolean forAll(Predicate<T> pred) {
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
	
	public default <S> S foldLeft(S accum, Function2<S,T,S> folder) {
//		Preconditions.checkArgument(accum != null, "accum is null");
		Preconditions.checkArgument(folder != null, "folder is null");
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			accum = folder.apply(accum, next.get());
		}
		
		return accum;
	}
	
	public default <S> S foldLeft(S accum, S stopper, BiFunction<S,T,S> folder) {
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
	
	public default <S> S foldRight(S accum, BiFunction<T,S,S> folder) {
		return FLists.foldRight(toList(), accum, folder);
	}
	
	public default FStream<T> scan(BinaryOperator<T> combiner) {
		return new ScannedStream<>(this, combiner);
	}
	
	public default T reduce(BiFunction<T,T,T> reducer) {
		Preconditions.checkArgument(reducer != null, "reducer is null");
		
		Option<T> next = next();
		if ( next.isDefined() ) {
			return foldLeft(next.get(), (a,t) -> reducer.apply(a, t));
		}
		else {
			throw new IllegalStateException("Stream is empty");
		}
	}
	
	public default <S> S collectLeft(S collector, BiConsumer<S,T> collect) {
		Preconditions.checkNotNull(collector);
		Preconditions.checkNotNull(collect);
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			collect.accept(collector, next.get());
		}
		
		return collector;
	}
	
	public default long count() {
		long count = 0;
		while ( next().isDefined() ) {
			++count;
		}
		
		return count;
	}
	
	public default Option<T> find(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		Option<T> next;
		Predicate<T> negated = pred.negate();
		while ( (next = next()).filter(negated).isDefined() );
		
		return next;
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
	
	public default FStream<T> concatWith(FStream<T> tail) {
		Preconditions.checkArgument(tail != null, "tail is null");
		
		return new Streams.AppendedStream<>(this, tail);
	}
	
	public default FStream<T> concatWith(T tail) {
		Preconditions.checkArgument(tail != null, "tail is null");
		
		return concatWith(FStream.of(tail));
	}
	
	public static <T> FStream<T> concat(FStream<T> head, FStream<T> tail) {
		Preconditions.checkArgument(head != null, "head is null");
		Preconditions.checkArgument(tail != null, "tail is null");
		
		return new Streams.AppendedStream<>(head, tail);
	}
	
	public default FStream<Tuple2<T,Integer>> zipWithIndex() {
		return zip(range(0,Integer.MAX_VALUE));
	}
	
	public default <U> FStream<Tuple2<T,U>> zip(FStream<U> other) {
		return () -> {
			Option<T> next1 = this.next();
			Option<U> next2 = other.next();
			
			return ( next1.isDefined() && next2.isDefined() )
					? Option.some(new Tuple2<>(next1.get(), next2.get()))
					: Option.none();
		};
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
	
	public default Option<Tuple2<T,FStream<T>>> peekFirst() {
		return next().map(head -> Tuple.of(head, concat(of(head), this)));
	}
	
	public default FStream<List<T>> buffer(int count, int skip) {
		return new BufferedStream<>(this, count, skip);
	}
	
	public default <K> KVFStream<K,T> toKVFStream(Function<T,K> keyGen) {
		return () -> next().map(t -> new KeyValue<>(keyGen.apply(t), t));
	}
	
	public default <K,V> KVFStream<K,V> toKVFStream(Function<T,K> keyGen,
													Function<T,V> valueGen) {
		return () -> next().map(t -> new KeyValue<>(keyGen.apply(t), valueGen.apply(t)));
	}
	
	public default <K,V> KVFStream<K,V> toKVFStream() {
		return () -> next().map(t -> {
			if ( !(t instanceof Tuple2) ) {
				throw new IllegalStateException("source FStream is not a stream of Tuple2: "
												+ t.getClass());
			}
			
			@SuppressWarnings("unchecked")
			Tuple2<K,V> tuple = (Tuple2<K,V>)t;
			return new KeyValue<>(tuple._1, tuple._2);
		});
	}
	
	public default Supplier<T> toSupplier() {
		return new Supplier<T>() {
			public T get() {
				return next().getOrNull();
			}
		};
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
	
	public default void forEachIE(CheckedConsumer<T> effect) {
		Preconditions.checkArgument(effect != null, "effect is null");
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			try {
				effect.accept(next.get());
			}
			catch ( Throwable ignored ) { }
		}
	}
	
	public default <K> Grouped<K,T> groupBy(Function<T,K> keyer) {
		return collectLeft(Grouped.create(), (g,t) -> g.add(keyer.apply(t), t));
	}
	
	public default <K,V> Grouped<K,V> groupBy(Function<T,K> keySelector, Function<T,V> valueSelector) {
		return collectLeft(Grouped.create(),
							(g,t) -> g.add(keySelector.apply(t), valueSelector.apply(t)));
	}
	
	public default <K,V> Grouped<K,V> multiGroupBy(Function<T,FStream<K>> keysSelector,
													Function<T,FStream<V>> valuesSelector) {
		return flatMap(t -> keysSelector.apply(t).map(k -> Tuple.of(k,t)))
				.collectLeft(Grouped.create(), (groups,t) -> {
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
	
	public default <K extends Comparable<K>> List<T> max(Function<T,K> keySelector) {
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
	
	public default <K extends Comparable<K>> List<T> min(Function<T,K> keySelector) {
		T min = null;
		K minKey = null;
		List<T> minList = Lists.newArrayList();
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			T data = next.get();
			K key = keySelector.apply(data);

			if ( min != null ) {
				int cmp = minKey.compareTo(key);
				if ( cmp < 0 ) {
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
}
