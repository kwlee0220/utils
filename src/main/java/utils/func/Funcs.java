package utils.func;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import utils.Indexed;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Funcs {
	private Funcs() {
		throw new AssertionError("Should not be called: class=" + Funcs.class);
	}
	
	public static <T> void when(boolean flag, Runnable work) {
		if ( flag ) {
			work.run();
		}
	}
	public static <T> void runIfNotNull(T obj, Runnable work) {
		if ( obj != null ) {
			work.run();
		}
	}
	
	public static <T> void acceptIfNotNull(T obj, Consumer<T> consumer) {
		if ( obj != null ) {
			consumer.accept(obj);
		}
	}
	
	public static <T,S> S applyIfNotNull(T obj, Function<T,S> func) {
		return applyIfNotNull(obj, func, null);
	}
	
	public static <T,S> S applyIfNotNull(T obj, Function<T,S> func, S elsePart) {
		if ( obj != null ) {
			return func.apply(obj);
		}
		else {
			return elsePart;
		}
	}
	
	public static <T> T getIf(boolean flag, T trueCase, T falseCase) {
		return (flag) ? trueCase : falseCase;
	}
	
	public static <T> T getIfNotNull(T obj, T falseCase) {
		return (obj != null) ? obj : falseCase;
	}
	
	public static <T> T asNonNull(T obj, Supplier<? extends T> nullValueSupplier) {
		return (obj != null) ? obj : nullValueSupplier.get();
	}
	
	public static <T> T getIfNotNull(Object obj, T trueCase, T falseCase) {
		return (obj != null) ? trueCase : falseCase;
	}
	
	public static <K,V> void acceptIfPresent(Map<K,V> map, K key, BiConsumer<K, V> consumer) {
		V value = map.get(key);
		if ( value != null ) {
			consumer.accept(key, value);
		}
	}
	
	public static <T> Set<T> add(Set<T> set, T elm) {
		Set<T> added = Sets.newHashSet(set);
		added.add(elm);
		return added;
	}
	
	public static <T> Set<T> remove(Set<T> set, T elm) {
		Set<T> removed = Sets.newHashSet(set);
		removed.remove(elm);
		return removed;
	}
	
	/**
	 * 주어진 collection 중에서 주어진 predicate를 만족하는 첫번째 element를 삭제한다.
	 *
	 * @param <T>
	 * @param coll	Collection 객체.
	 * @param pred	Element의 삭제 여부를 판달할 Predicate 객체.
	 * @return	삭제가 성공한 경우는 삭제된 element, 그렇지 않은 경우는 null.
	 */
	public static <T> T removeAny(Iterable<T> coll, Predicate<? super T> pred) {
		Iterator<T> iter = coll.iterator();
		while ( iter.hasNext() ) {
			T elm = iter.next();
			if ( pred.test(elm) ) {
				iter.remove();
				return elm;
			}
		}
		
		return null;
	}
	
	public static <T> List<T> removeIf(Iterable<T> coll, Predicate<? super T> pred) {
		List<T> removeds = Lists.newArrayList();
		Iterator<T> iter = coll.iterator();
		while ( iter.hasNext() ) {
			T elm = iter.next();
			if ( pred.test(elm) ) {
				iter.remove();
				removeds.add(elm);
			}
		}
		
		return removeds;
	}
	
	public static <T> List<T> add(List<T> list, T elm) {
		List<T> added = Lists.newArrayList(list);
		added.add(elm);
		return added;
	}
	
	public static <T> List<T> remove(List<T> list, T elm) {
		List<T> removed = Lists.newArrayList(list);
		removed.remove(elm);
		return removed;
	}
	
	public static <T> boolean intersects(Collection<T> set1, Collection<T> set2) {
		return FStream.from(set1).exists(v -> set2.contains(v));
	}

	public static <T> T getFirst(Iterable<T> list) {
		Iterator<T> iter = list.iterator();
		return iter.hasNext() ? iter.next() : null;
	}

	public static <T> T getLast(Iterable<T> list) {
		return Iterables.getLast(list, null);
	}
	
	public static <T> List<T> filter(Iterable<T> list, Predicate<? super T> pred) {
		return FStream.from(list).filter(pred).toList();
	}
	
	public static <T,S> List<S> map(List<T> list, Function<? super T, ? extends S> mapper) {
		List<S> coll = Lists.newArrayList();
		return FStream.from(list).map(mapper).toCollection(coll);
	}
	
	public static <T,S> Set<S> map(Set<T> set, Function<? super T, ? extends S> mapper) {
		Set<S> result = Sets.newHashSet();
		return FStream.from(set).map(mapper).toCollection(result);
	}
	
	public static <T>T findAny(Iterable<T> coll, Predicate<? super T> pred) {
		return FStream.from(coll).findFirst(pred).getOrNull();
	}
	
	public static <T> Indexed<T> findAnyIndexed(Iterable<T> coll, Predicate<? super T> pred) {
		return FStream.from(coll)
						.zipWithIndex()
						.map(t -> Indexed.with(t._1, t._2))
						.findFirst(idxed -> pred.test(idxed.getData()))
						.getOrNull();
	}
	
	public static <T> boolean exists(Iterable<T> coll, Predicate<? super T> pred) {
		return FStream.from(coll).exists(pred);
	}
	
	public static <T> int replace(List<T> list, Predicate<? super T> pred,
									Function<? super T, ? extends T> updater) {
		int count =0;
		for ( int i =0; i < list.size(); ++i ) {
			T v = list.get(i);
			if ( pred.test(v) ) {
				list.set(i, updater.apply(v));
				++count;
			}
		}
		
		return count;
	}
	
	public static <T> boolean replaceFirst(List<T> list, Predicate<? super T> pred,
										Function<? super T, ? extends T> updater) {
		for ( int i =0; i < list.size(); ++i ) {
			T v = list.get(i);
			if ( pred.test(v) ) {
				list.set(i, updater.apply(v));
				return true;
			}
		}
		
		return false;
	}
	
	public static <T> boolean replaceFirst(List<T> list, Predicate<? super T> pred, T newVal) {
		for ( int i =0; i < list.size(); ++i ) {
			T v = list.get(i);
			if ( pred.test(v) ) {
				list.set(i, newVal);
				return true;
			}
		}
		
		return false;
	}
}
