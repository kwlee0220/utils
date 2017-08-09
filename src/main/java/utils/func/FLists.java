package utils.func;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FLists {
	private FLists() {
		throw new AssertionError("Should not be called: class=" + FLists.class);
	}
	
	public static <T,F> F foldLeft(List<T> list, F identity, BiFunction<F, T, F> fold) {
		F accum = identity;
		for ( T t: list ) {
			accum = fold.apply(accum, t);
		}
		return accum;
	}
	
	public static <T,F> F foldLeft(List<T> list, F identity, Function<F, Function<T, F>> fold) {
		F accum = identity;
		for ( T t: list ) {
			accum = fold.apply(accum).apply(t);
		}
		return accum;
	}
	
	public static <T,F> F foldRight(List<T> list, F identity, BiFunction<F, T, F> fold) {
		F accum = identity;
		for ( int i = list.size()-1; i >= 0; --i ) {
			final T t = list.get(i);
			accum = fold.apply(accum, t);
		}
		return accum;
	}
	
	public static <T,F> F foldRight(List<T> list, F identity, Function<F, Function<T, F>> fold) {
		F accum = identity;
		for ( int i = list.size()-1; i >= 0; --i ) {
			final T t = list.get(i);
			accum = fold.apply(accum).apply(t);
		}
		return accum;
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
		if ( list.isEmpty() ) {
			return Optional.empty();
		}
		
		T accum = list.get(0);
		for ( int i =1; i < list.size(); ++i ) {
			final T t = list.get(i);
			accum = fold.apply(accum).apply(t);
		}
		return Optional.of(accum);
	}
}
