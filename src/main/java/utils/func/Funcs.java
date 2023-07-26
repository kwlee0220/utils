package utils.func;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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
	
	public static <T> List<T> filter(List<T> list, Predicate<? super T> pred) {
		return FStream.from(list).filter(pred).toList();
	}
	
	public static <T> FOption<T> findFirst(Iterable<T> coll, Predicate<? super T> pred) {
		return FStream.from(coll).findFirst(pred);
	}
	
	public static <T> boolean exists(Iterable<T> coll, Predicate<? super T> pred) {
		return FStream.from(coll).exists(pred);
	}
}
