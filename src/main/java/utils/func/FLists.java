package utils.func;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FLists {
	private FLists() {
		throw new AssertionError("Should not be called: class=" + FLists.class);
	}
	
	public static <T,U> U foldLeft(List<T> list, U init, BiFunction<U, T, U> fold) {
		U accum = init;
		for ( T t: list ) {
			accum = fold.apply(accum, t);
		}
		return accum;
	}
	public static <T,U> U foldLeft(List<T> list, U init, U stopper, BiFunction<U, T, U> fold) {
		U accum = init;
		for ( T t: list ) {
			accum = fold.apply(accum, t);
			if ( accum.equals(stopper) ) {
				return accum;
			}
		}
		return accum;
	}
	
	public static <T,U> U foldLeft(List<T> list, U identity,
									Function<U, Function<T, U>> fold) {
		return foldLeft(list, identity, (u, t)->fold.apply(u).apply(t));
	}
	
	public static <T,U> U foldRight(List<? extends T> list, U init,
									BiFunction<? super T, ? super U, ? extends U> fold) {
		U accum = init;
		for ( int i = list.size()-1; i >= 0; --i ) {
			final T t = list.get(i);
			accum = fold.apply(t, accum);
		}
		return accum;
	}
	public static <T,U> U foldRight(List<T> list, U init, U stopper, BiFunction<T, U, U> fold) {
		U accum = init;
		for ( int i = list.size()-1; i >= 0; --i ) {
			final T t = list.get(i);
			accum = fold.apply(t, accum);
			if ( accum.equals(stopper) ) {
				return accum;
			}
		}
		return accum;
	}
	
	public static <T,U> U foldRight(List<T> list, U identity,
									Function<T, Function<U, U>> fold) {
		return foldRight(list, identity, (t,u) -> fold.apply(t).apply(u));
	}
	
	public static <T> Optional<T> reduce(List<T> list, BiFunction<T, T, T> fold) {
		if ( list.isEmpty() ) {
			return Optional.empty();
		}
		
		T accum = list.get(0);
		for ( int i =1; i < list.size(); ++i ) {
			final T t = list.get(i);
			accum = fold.apply(accum, t);
		}
		return Optional.of(accum);
	}
	
	public static <T> Optional<T> reduce(List<T> list, Function<T, Function<T, T>> fold) {
		return reduce(list, (accum,t) -> fold.apply(accum).apply(t));
	}
	
	public static <T> Optional<Tuple<Integer,T>> maxBy(List<T> list, Comparator<? super T> cmp) {
		int idx = -1;
		T max = null;
		for ( int i =0; i < list.size(); ++i ) {
			T t = list.get(i);
			if ( max == null || cmp.compare(max, t) < 0 ) {
				max = t;
				idx = i;
			}
		}
		
		return (idx >= 0) ? Optional.of(Tuple.of(idx, max)) : Optional.empty(); 
	}
	
	@SafeVarargs
	public static <T> List<List<T>> branch(List<T> list, Predicate<T>... guards) {
		List<List<T>> branches = Lists.newArrayListWithExpectedSize(guards.length);
		for ( int i =0; i < guards.length; ++i ) {
			branches.add(Lists.newArrayList());
		}
		
		for ( T v: list ) {
			for ( int i =0; i < guards.length; ++i ) {
				if ( guards[i].test(v) ) {
					branches.get(i).add(v);
					break;
				}
			}
		}
		
		return branches;
	}
}
