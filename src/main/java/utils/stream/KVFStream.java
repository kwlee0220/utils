package utils.stream;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import io.vavr.Tuple2;
import utils.func.FOption;
import utils.stream.KVFStreams.FStreamAdaptor;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface KVFStream<K,V> extends FStream<KeyValue<K,V>> {
	public static <K,V> KVFStream<K,V> downcast(FStream<KeyValue<K,V>> stream) {
		Objects.requireNonNull(stream);
		
		return new FStreamAdaptor<>(stream);
	}
	
	public static <K,V> KVFStream<K,V> fromTupleFStream(FStream<Tuple2<K,V>> stream) {
		Objects.requireNonNull(stream);
		
		return downcast(stream.map(t -> KeyValue.of(t._1, t._2)));
	}
	
	public static <K,V> KVFStream<K,V> of(Map<? extends K, ? extends V> map) {
		Objects.requireNonNull(map);
		
		return downcast(FStream.of(map.entrySet().iterator())
							.map(e -> KeyValue.of(e.getKey(), e.getValue())));
	}
	
	public default KVFStream<K,V> filterKey(Predicate<? super K> pred) {
		Objects.requireNonNull(pred);
		
		return downcast(filter(kv -> pred.test(kv.key())));
	}
	
	public default KVFStream<K,V> filterValue(Predicate<? super V> pred) {
		Objects.requireNonNull(pred);
		
		return downcast(filter(kv -> pred.test(kv.value())));
	}
	
	public default <S> FStream<S> map(BiFunction<? super K,? super V,? extends S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return map(kv -> mapper.apply(kv.key(), kv.value()));
	}
	
	public default <S> KVFStream<S,V> mapKey(BiFunction<? super K,? super V,? extends S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return downcast(map(kv -> KeyValue.of(mapper.apply(kv.key(), kv.value()), kv.value())));
	}
	
	public default <S> KVFStream<S,V> mapKey(Function<? super K,? extends S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return downcast(map(kv -> KeyValue.of(mapper.apply(kv.key()), kv.value())));
	}
	
	public default <U> KVFStream<K,U> mapValue(BiFunction<? super K,? super V,? extends U> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return downcast(map(kv -> KeyValue.of(kv.key(), mapper.apply(kv.key(), kv.value()))));
	}
	
	public default <U> KVFStream<K,U> mapValue(Function<? super V,? extends U> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return downcast(map(kv -> KeyValue.of(kv.key(), mapper.apply(kv.value()))));
	}
	
	public default <T> FStream<T> flatMap(BiFunction<? super K,? super V,FStream<T>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return flatMap(kv -> mapper.apply(kv.key(), kv.value()));
	}
	
	public default <U> KVFStream<K,U> castValue(Class<? extends U> cls) {
		Preconditions.checkArgument(cls != null, "target class is null");
		
		return mapValue(cls::cast);
	}
	
	public default void forEach(BiConsumer<? super K,? super V> effect) {
		Preconditions.checkArgument(effect != null, "effect is null");
		
		forEach(kv -> effect.accept(kv.key(), kv.value()));
	}
	
	public default <C> C collectLeft(C collector, TriConsumer<? super C,? super K,? super V> collect) {
		Objects.requireNonNull(collector);
		Objects.requireNonNull(collect);
		
		FOption<KeyValue<K,V>> next;
		while ( (next = next()).isPresent() ) {
			collect.accept(collector, next.get().key(), next.get().value());
		}
		
		return collector;
	}
	
	public default KeyedGroups<K,V> groupBy() {
		return foldLeft(KeyedGroups.create(), (groups,kv) -> groups.add(kv.key(), kv.value()));
	}
	
	public default KVFStream<K,V> sortByKey() {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		FStream<KeyValue<K,V>> sorted = sort((kv1,kv2) -> ((Comparable)kv1.key()).compareTo(kv2.key()));
		return downcast(sorted);
	}
	
	public default KVFStream<K,V> sortByKey(Comparator<? super K> cmp) {
		FStream<KeyValue<K,V>> sorted = sort((kv1,kv2) -> cmp.compare(kv1.key(), kv2.key()));
		return downcast(sorted);
		
	}
	
	public default FStream<K> toKeyStream() {
		return map(kv -> kv.key());
	}
	
	public default FStream<V> toValueStream() {
		return map(kv -> kv.value());
	}
	
	public default <T extends Map<K,V>> T toMap(T map) {
		return collectLeft(map, (accum,kv) -> accum.put(kv.key(), kv.value()));
	}
	
	public default HashMap<K,V> toMap() {
		return toMap(Maps.newHashMap());
	}

/*
	public default <V> FStream<V> map(Function<T,V> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return () -> next().map(mapper);
	}
	
	public <S> KVFStream<K,S> mapValue(Function<V,S> m) {
		return () -> next().map(mapper);
	}

	public Option<T> next();
	
	@SuppressWarnings("unchecked")
	public static <T> KVFStream<T> empty() {
		return Streams.EMPTY;
	}
	
	@SafeVarargs
	public static <T> KVFStream<T> of(T... values) {
		return of(Arrays.asList(values));
	}
	
	public static <T> KVFStream<T> of(Iterable<T> values) {
		return of(values.iterator());
	}
	
	public static <T> KVFStream<T> of(Iterator<T> iter) {
		return () -> iter.hasNext() ? Option.some(iter.next()) : Option.none();
	}
	
	public static <T> KVFStream<T> of(Stream<T> strm) {
		return of(strm.iterator());
	}
	
	public static <K,V> KVFStream<Tuple2<K,V>> of(Map<K,V> map) {
		return of(map.entrySet())
					.map(ent -> Tuple.of(ent.key(), ent.getValue()));
	}
	
	public static <S,T> KVFStream<T> unfold(S init, Function<S,Option<Tuple2<T,S>>> generator) {
		Preconditions.checkArgument(init != null, "init is null");
		Preconditions.checkArgument(generator != null, "generator is null");
		
		return new Streams.UnfoldStream<T, S>(init, generator);
	}
	
	public static <T> KVFStream<T> generate(T init, Function<T,T> inc) {
		Preconditions.checkArgument(init != null, "init is null");
		Preconditions.checkArgument(inc != null, "inc is null");
		
		return new Streams.GeneratedStream<>(init, inc);
	}
	
	public static KVFStream<Integer> range(int start, int end) {
		return new Streams.RangedStream(start, end, false);
	}
	public static KVFStream<Integer> rangeClosed(int start, int end) {
		return new Streams.RangedStream(start, end, true);
	}
	
	public default KVFStream<T> take(long count) {
		Preconditions.checkArgument(count >= 0, "count < 0");
		return new Streams.TakenStream<>(this, count);
	}
	public default KVFStream<T> drop(long count) {
		Preconditions.checkArgument(count >= 0, "count < 0");
		return new Streams.DroppedStream<>(this, count);
	}

	public default KVFStream<T> takeWhile(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		return new Streams.TakeWhileStream<>(this, pred);
	}
	public default KVFStream<T> dropWhile(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		return new Streams.DropWhileStream<>(this, pred);
	}
	
	public default <V> KVFStream<V> map(Function<T,V> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return () -> next().map(mapper);
	}
	
	public default <V> KVFStream<V> cast(Class<V> cls) {
		Preconditions.checkArgument(cls != null, "target class is null");
		
		return () -> next().map(cls::cast);
	}
	
	public default <V> KVFStream<V> castSafely(Class<V> cls) {
		Preconditions.checkArgument(cls != null, "target class is null");
		
		return () -> next().filter(cls::isInstance)
							.map(cls::cast);
	}
	
	public default KVFStream<T> peek(Consumer<? super T> consumer) {
		Preconditions.checkArgument(consumer != null, "consumer is null");
		
		return () -> next().peek(consumer);
	}
	
	public default <V> KVFStream<V> flatMap(Function<T,KVFStream<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return map(mapper).foldLeft(empty(), (a,s) -> concat(a,s));
	}
	
	public default <V> KVFStream<V> flatMapOption(Function<T,Option<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return map(mapper).foldLeft(empty(), (a,opt) -> 
			opt.isDefined() ? concat(a,of(opt.get())) : a
		);
	}
	
	public default <V> KVFStream<V> flatMapIterable(Function<T,Iterable<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return map(mapper).foldLeft(empty(), (a,s) -> concat(a,of(s)));
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
		Objects.requireNonNull(collector);
		Objects.requireNonNull(collect);
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			collect.accept(collector, next.get());
		}
		
		return collector;
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
	
	public static <T> KVFStream<T> concat(KVFStream<T> head, KVFStream<T> tail) {
		Preconditions.checkArgument(head != null, "head is null");
		Preconditions.checkArgument(tail != null, "tail is null");
		
		return new Streams.AppendedStream<>(head, tail);
	}
	
	public default KVFStream<Tuple2<T,Integer>> zipWithIndex() {
		return zip(range(0,Integer.MAX_VALUE));
	}
	
	public default <U> KVFStream<Tuple2<T,U>> zip(KVFStream<U> other) {
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
	
	public default List<T> toList() {
		return foldLeft(Lists.newArrayList(), (l,t) -> { l.add(t); return l; });
	}
	
	public default T[] toArray(Class<T> componentType) {
		List<T> list = toList();
		@SuppressWarnings("unchecked")
		T[] array = (T[])Array.newInstance(componentType, list.size());
		return list.toArray(array);
	}
	
	public default Option<Tuple2<T,KVFStream<T>>> peekFirst() {
		return next().map(head -> Tuple.of(head, concat(of(head), this)));
	}
	
	@SuppressWarnings("unchecked")
	public static <T,K,V> KVFStream<Tuple2<K,V>> toTupleStream(KVFStream<T> stream) {
		Option<Tuple2<T,KVFStream<T>>> otuple = stream.peekFirst();
		if ( otuple.isEmpty() ) {
			return KVFStream.empty();
		}
		
		Tuple2<T,KVFStream<T>> tuple = otuple.get();
		if ( !(tuple._1 instanceof Tuple2) ) {
			throw new IllegalStateException("not Tuple2 FStream: this=" + stream);
		}
		
		KVFStream<T> stream2 = otuple.get()._2;
		return () -> (Option<Tuple2<K,V>>)stream2.next();
	}
	
	public default Stream<T> stream() {
		return Utilities.stream(iterator());
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
		return foldLeft(new Grouped<>(), (g,t) -> g.add(keyer.apply(t), t));
	}
	
	public default KVFStream<T> sorted(Comparator<? super T> cmp) {
		List<T> list = toList();
		list.sort(cmp);
		return of(list);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default KVFStream<T> sorted() {
		return sorted((t1,t2) -> ((Comparable)t1).compareTo(t2));
	}
	
	@SuppressWarnings("unchecked")
	public default Option<T> max() {
		Comparable<T> max = null;
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			if ( max != null ) {
				if ( max.compareTo(next.get()) < 0 ) {
					max = (Comparable<T>)next.get();
				}
			}
			else {
				if ( !(next.get() instanceof Comparable) ) {
					throw new IllegalStateException("FStream elements are not Comparable");
				}
				
				max = (Comparable<T>)next.get();
			}
		}
		
		return Option.of(((T)max));
	}

	@SuppressWarnings("unchecked")
	public default Option<T> min() {
		Comparable<T> max = null;
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			if ( max != null ) {
				if ( max.compareTo(next.get()) > 0 ) {
					max = (Comparable<T>)next.get();
				}
			}
			else {
				if ( !(next.get() instanceof Comparable) ) {
					throw new IllegalStateException("FStream elements are not Comparable");
				}
				
				max = (Comparable<T>)next.get();
			}
		}
		
		return Option.of(((T)max));
	}
	
	public default <K extends Comparable<K>> Option<T> max(Function<T,K> keySelector) {
		Option<T> max = Option.none();
		K maxKey = null;
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			K key = next.map(keySelector).get();
			if ( max.isDefined() ) {
				if ( maxKey.compareTo(key) < 0 ) {
					max = next;
					maxKey = key;
				}
			}
			else {
				max = next;
				maxKey = key;
			}
		}
		
		return max;
	}
	
	public default <K extends Comparable<K>> Option<T> min(Function<T,K> keySelector) {
		Option<T> max = Option.none();
		K maxKey = null;
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			K key = next.map(keySelector).get();
			if ( max.isDefined() ) {
				if ( maxKey.compareTo(key) > 0 ) {
					max = next;
					maxKey = key;
				}
			}
			else {
				max = next;
				maxKey = key;
			}
		}
		
		return max;
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

	public default boolean startsWith(KVFStream<T> subList) {
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
*/
}
