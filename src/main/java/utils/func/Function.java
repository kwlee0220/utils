package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Function<T,U> {
	public U apply(T arg);
	
	public static <T> Function<T,T> identity() {
		return t -> t;
	}
	
	public default <V> Function<V,U> compose(Function<V,T> g) {
		return (V x) -> apply(g.apply(x));
	}
	
	public default <V> Function<T,V> andThen(Function<U,V> f) {
		return (T x) -> f.apply(apply(x));
	}

	public static <T, U, V> Function<V, U> compose(Function<T, U> f, Function<V, T> g) {
		return (V x) -> f.apply(g.apply(x));
	}

	public static <T, U, V> Function<T, V> andThen(Function<T, U> f, Function<U, V> g) {
		return x -> g.apply(f.apply(x));
	}
	
	static <T, U, V> Function<Function<T, U>,
							Function<Function<U, V>, Function<T, V>>> compose() {
		return x -> y -> y.compose(x);
	}
	
	static <T, U, V> Function<Function<T, U>,
							Function<Function<V, T>, Function<V, U>>> andThen() {
		return x -> y -> y.andThen(x);
	}

	public static <T, U, V> Function<Function<U, V>,
									Function<Function<T, U>, Function<T, V>>> higherCompose() {
		return f -> g -> x -> f.apply(g.apply(x));
	}
	
	public static <T, U, V> Function<Function<T, U>,
									Function<Function<U, V>, Function<T, V>>> higherAndThen() {
		return f -> g -> z -> g.apply(f.apply(z));
	}
}
