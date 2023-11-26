package utils.func;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import utils.Indexed;
import utils.stream.FStream;
import utils.stream.KeyedGroups;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Funcs {
	private Funcs() {
		throw new AssertionError("Should not be called: class=" + Funcs.class);
	}
	
	/**
	 * 주어진 목록들 중에서 조건을 만족하는 임의의 한 목록을 반환한다. 
	 * 목록들 중에서 조건을 만족하는 것이 여러개인 경우에는 임의로 하나만 선택된다.
	 *
	 * @param <T>
	 * @param coll	검색 대상 목록들.
	 * @param pred	검색 조건
	 * @return	조건을 만족하는 목록. 만일 조건을 만족하는 것이 없는 경우에는 {@code null}.
	 */
	public static <T> T findFirst(Iterable<T> coll, Predicate<? super T> pred) {
		return FStream.from(coll).findFirst(pred).getOrNull();
	}

	/**
	 * 주어진 리스트에 포함된 목록들 중에서 조건을 만족하는 임의의 한 목록을 반환한다. 
	 * 목록들 중에서 조건을 만족하는 것이 여러개인 경우에는 가장 처음으로 조건을 만족하는 목록이 선택된다.
	 *
	 * @param <T>
	 * @param list	검색 대상 목록들.
	 * @param pred	검색 조건
	 * @return	조건을 만족하는 목록. 만일 조건을 만족하는 것이 없는 경우에는 {@code null}.
	 */
	public static <T> Indexed<T> findFirstIndexed(Iterable<T> list, Predicate<? super T> pred) {
		return FStream.from(list)
						.zipWithIndex()
						.map(t -> Indexed.with(t._1, t._2))
						.findFirst(idxed -> pred.test(idxed.value()))
						.getOrNull();
	}

	/**
	 * 주어진 목록들 중에서 첫번재 목록을 반환한다.
	 *
	 * @param <T>
	 * @param iterable	목록 리스트.
	 * @return	첫번째 목록. 리스트가 빈 경우에는 {@code null}이 반환된다.
	 */
	public static <T> T getFirst(Iterable<T> iterable) {
		Iterator<T> iter = iterable.iterator();
		return iter.hasNext() ? iter.next() : null;
	}

	/**
	 * 주어진 목록들 중에서 마지막 목록을 반환한다.
	 *
	 * @param <T>
	 * @param iterable	목록 리스트.
	 * @return	마지막 목록. 리스트가 빈 경우에는 {@code null}이 반환된다.
	 */
	public static <T> T getLast(Iterable<T> list) {
		return com.google.common.collect.Iterables.getLast(list, null);
	}
	
	/**
	 * 주어진 목록에서 주어진 조건을 만족하는 목록의 존재 여부를 반환한다.
	 *
	 * @param <T>
	 * @param iterable	검색 대상 목록들.
	 * @param pred		검색 조건
	 * @return	존재하는 경우에는 {@code true}, 그렇지 않은 경우에는 {@code false}.
	 */
	public static <T> boolean exists(Iterable<T> iterable, Predicate<? super T> pred) {
		return FStream.from(iterable).exists(pred);
	}
	
	public static <T> boolean all(Iterable<T> iterable, Predicate<? super T> pred) {
		return !FStream.from(iterable).exists(v -> !pred.test(v));
	}
	
	public static <T> List<T> filter(Iterable<T> list, Predicate<? super T> pred) {
		return FStream.from(list).filter(pred).toList();
	}
	public static <T> Set<T> filter(Set<T> list, Predicate<? super T> pred) {
		return FStream.from(list).filter(pred).toSet();
	}
	
	public static <T,S> List<S> map(Iterable<T> list, Function<? super T, ? extends S> mapper) {
		List<S> coll = Lists.newArrayList();
		return FStream.from(list).map(mapper).toCollection(coll);
	}
	public static <T,S> Set<S> map(Set<T> set, Function<? super T, ? extends S> mapper) {
		Set<S> result = Sets.newHashSet();
		return FStream.from(set).map(mapper).toCollection(result);
	}

	/**
	 * 주어진 목록들 중에서 주어진 조건을 만족하는 모든 element를 주어진 목록으로 대체시킨다.
	 *
	 * @param <T>
	 * @param list	Iterable 객체.
	 * @param pred		목록의 대체 여부를 판달할 Predicate 객체.
	 * @param supplier	대체할 새 객체를 제공할 Supplier 객체
	 * @return	대체되기 이전 목록들의 리스트.
	 */
	public static <T> List<T> replaceIf(List<T> list, Predicate<? super T> pred,
											Function<? super T, ? extends T> supplier) {
		List<T> replaceds = Lists.newArrayList();
		for ( int i =0; i < list.size(); ++i ) {
			T v = list.get(i);
			if ( pred.test(v) ) {
				replaceds.add(v);
				list.set(i, supplier.apply(v));
			}
		}
		
		return replaceds;
	}

	/**
	 * 주어진 목록들 중에서 주어진 조건을 만족하는 첫번째 element를 주어진 목록으로 대체시킨다.
	 *
	 * @param <T>
	 * @param list		Iterable 객체.
	 * @param pred		목록의 대체 여부를 판달할 Predicate 객체.
	 * @param supplier	대체할 새 객체를 제공할 Supplier 객체
	 * @return	대체되기 이전 목록. 대체된 목록이 없었던 경우에는 {@code null}이 반환됨.
	 */
	public static <T> T replaceFirst(List<T> list, Predicate<? super T> pred,
										Function<? super T, ? extends T> updater) {
		for ( int i =0; i < list.size(); ++i ) {
			T v = list.get(i);
			if ( pred.test(v) ) {
				list.set(i, updater.apply(v));
				return v;
			}
		}
		
		return null;
	}

	/**
	 * 주어진 목록들 중에서 주어진 조건을 만족하는 첫번째 element를 주어진 목록으로 대체시킨다.
	 *
	 * @param <T>
	 * @param list		Iterable 객체.
	 * @param pred		목록의 대체 여부를 판달할 Predicate 객체.
	 * @param supplier	대체할 새 객체.
	 * @return	대체되기 이전 목록. 대체된 목록이 없었던 경우에는 {@code null}이 반환됨.
	 */
	public static <T> T replaceFirst(List<T> list, Predicate<? super T> pred, T newVal) {
		for ( int i =0; i < list.size(); ++i ) {
			T v = list.get(i);
			if ( pred.test(v) ) {
				list.set(i, newVal);
				return v;
			}
		}

		return null;
	}
	
	public static <T> T removeFirst(Iterable<T> iterable) {
		Iterator<T> iter = iterable.iterator();
		if ( iter.hasNext() ) {
			T removed = iter.next();
			iter.remove();
			return removed;
		}
		else {
			return null;
		}
	}
	
	/**
	 * 주어진 목록들 중에서 주어진 조건을 만족하는 첫번째 element를 삭제한다.
	 * 만일 목록 내에 같은 조건의 목록이 여러개 있는 경우에는 그 중 하나의 목록만 삭제된다.
	 *
	 * @param <T>
	 * @param iterable	Collection 객체.
	 * @param pred	Element의 삭제 여부를 판달할 Predicate 객체.
	 * @return	삭제가 성공한 경우는 삭제된 element, 그렇지 않은 경우는 null.
	 */
	public static <T> T removeFirstIf(Iterable<T> iterable, Predicate<? super T> pred) {
		Iterator<T> iter = iterable.iterator();
		while ( iter.hasNext() ) {
			T elm = iter.next();
			if ( pred.test(elm) ) {
				iter.remove();
				return elm;
			}
		}
		
		return null;
	}

	/**
	 * 주어진 목록들 중에서 조건을 만족하는 모든 element를 삭제하고,
	 * 삭제된 목록들을 반환한다.
	 *
	 * @param <T>
	 * @param iterable	Iterable 객체.
	 * @param pred		Element의 삭제 여부를 판달할 Predicate 객체.
	 * @return	삭제된 목록들의 리스트.
	 */
	public static <T> List<T> removeIf(Iterable<T> iterable, Predicate<? super T> pred) {
		List<T> removeds = Lists.newArrayList();
		
		Iterator<T> iter = iterable.iterator();
		while ( iter.hasNext() ) {
			T elm = iter.next();
			if ( pred.test(elm) ) {
				iter.remove();
				removeds.add(elm);
			}
		}
		
		return removeds;
	}
	
	/**
	 * 주어진 {@link Map}에 등록된 entry들 중에서 주어진 조건 (pred)를 만족하는 것들을 삭제한다.
	 * 
	 * @param <K>
	 * @param <V>
	 * @param map	삭제를 수행할 대상 Map 객체.
	 * @param pred	삭제 조건.
	 * @return		삭제된 {@link Map.Entry} 객체들.
	 */
	public static <K,V> List<KeyValue<K,V>> removeIf(Map<K,V> map, BiPredicate<? super K, ? super V> pred) {
		List<KeyValue<K,V>> removeds = Lists.newArrayList();
		
		Iterator<Map.Entry<K, V>> iter = map.entrySet().iterator();
		while ( iter.hasNext() ) {
			Map.Entry<K, V> ent = iter.next();
			if ( pred.test(ent.getKey(), ent.getValue()) ) {
				removeds.add(KeyValue.from(ent));
				iter.remove();
			}
		}

		return removeds;
	}

	/**
	 * 주어진 목록들 중에서 주어진 조건을 만족하는 모든 element를 주어진 consumer를 통해 apply를 호출한다.
	 *
	 * @param <T>
	 * @param iterable	Iterable 객체.
	 * @param pred	Element의 apply 여부를 판달할 Predicate 객체.
	 * @param consumer	consumer 객체.
	 * @return	apply된 객체 갯수.
	 */
	public static <T> int acceptIf(Iterable<T> list, Predicate<? super T> pred,
									Consumer<? super T> consumer) {
		return (int)FStream.from(list)
							.filter(pred)
							.peek(consumer)
							.count();
	}
	
	public static <K,V> void acceptIfPresent(Map<K,V> map, K key, BiConsumer<K, V> consumer) {
		V value = map.get(key);
		if ( value != null ) {
			consumer.accept(key, value);
		}
	}
	
	public static <T> Set<T> addCopy(Set<T> set, T elm) {
		Set<T> added = Sets.newHashSet(set);
		added.add(elm);
		return added;
	}
	
	public static <T> List<T> addCopy(List<T> list, T elm) {
		List<T> added = Lists.newArrayList(list);
		added.add(elm);
		return added;
	}
	
	public static <T> List<T> removeCopy(List<T> list, T elm) {
		List<T> removed = Lists.newArrayList(list);
		removed.remove(elm);
		return removed;
	}
	
	public static <T> Set<T> removeCopy(Set<T> set, T elm) {
		Set<T> removed = Sets.newHashSet(set);
		removed.remove(elm);
		return removed;
	}
	
	public static <T, K extends Comparable<K>>
	FOption<? extends T> min(Iterable<? extends T> list, Function<? super T,? extends K> keyer) {
		return FStream.from(list).min(keyer);
	}
	
	public static <T, K extends Comparable<K>>
	FOption<? extends T> max(Iterable<? extends T> list, Function<? super T,? extends K> keyer) {
		return FStream.from(list).max(keyer);
	}
	
	public static <T,K extends Comparable<K>>
	int argmax(List<? extends T> list, Function<? super T,? extends K> keyer) {
		int maxIdx = -1;
		K maxValue = null;
		for ( int i =0; i < list.size(); ++i ) {
			K key = keyer.apply(list.get(i));
			if ( maxValue == null ) {
				maxValue = key;
				maxIdx = 0;
			}
			else if ( key.compareTo(maxValue) > 0 ) {
				maxValue = key;
				maxIdx = i;
			}
		}
		return maxIdx;
	}
	
	public static <T,K extends Comparable<K>>
	int argmin(List<? extends T> list, Function<? super T,? extends K> keyer) {
		int minIdx = -1;
		K minValue = null;
		for ( int i =0; i < list.size(); ++i ) {
			K key = keyer.apply(list.get(i));
			if ( minValue == null ) {
				minValue = key;
				minIdx = 0;
			}
			else if ( key.compareTo(minValue) < 0 ) {
				minValue = key;
				minIdx = i;
			}
		}
		return minIdx;
	}
	
	public static <T> boolean intersects(Collection<? extends T> set1, Collection<? extends T> set2) {
		return FStream.from(set1).exists(v -> set2.contains(v));
	}
	
	@SafeVarargs
	public static <T> List<T> union(Collection<? extends T>... clArray) {
		List<T> union = Lists.newArrayList();
		for ( Collection<? extends T> cl: clArray ) {
			union.addAll(cl);
		}
		return union;
	}
	
	public static <T,K> KeyedGroups<K,T> groupBy(Iterable<T> values, Function<? super T,? extends K> keyer) {
		return FStream.from(values).groupByKey(keyer);
	}
	
	public static <T> Tuple<List<T>,List<T>> partition(Iterable<T> values, Predicate<? super T> pred) {
		List<T> trueCollection = Lists.newArrayList();
		List<T> falseCollection = Lists.newArrayList();
		for ( T elm: values ) {
			if ( pred.test(elm) ) {
				trueCollection.add(elm);
			}
			else {
				falseCollection.add(elm);
			}
		}
		
		return Tuple.of(trueCollection, falseCollection);
	}
	
	
	
	

	
	public static <T> T asNonNull(T obj, T nullCaseValue) {
		return (obj != null) ? obj : nullCaseValue;
	}
	public static <T> T asNonNull(T obj, Supplier<? extends T> nullValueSupplier) {
		return (obj != null) ? obj : nullValueSupplier.get();
	}
	
	public static <T> void runIf(boolean flag, Runnable work) {
		if ( flag ) {
			work.run();
		}
	}
	public static <T> void runIfNotNull(Object obj, Runnable work) {
		if ( obj != null ) {
			work.run();
		}
	}
	
	public static <T> void acceptIfNotNull(T obj, Consumer<T> consumer) {
		if ( obj != null ) {
			consumer.accept(obj);
		}
	}
	
	public static <T,S> S applyIfNotNull(T obj, Function<T,S> func, S elsePart) {
		if ( obj != null ) {
			return func.apply(obj);
		}
		else {
			return elsePart;
		}
	}
	
	public static <T,S> S applyIfNotNull(T obj, Function<T,S> func) {
		return applyIfNotNull(obj, func, null);
	}
}
