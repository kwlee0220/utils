package utils;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ComparableKeyValue<K extends Comparable<K>,V>
														extends KeyValue<K,V>
														implements Comparable<ComparableKeyValue<K,V>> {
	public static <K extends Comparable<K>,V> ComparableKeyValue<K,V> of(K key, V value) {
		return new ComparableKeyValue<>(key, value);
	}

	public static <K extends Comparable<K>,V>
	ComparableKeyValue<K,V> fromEntry(Map.Entry<? extends K,? extends V> entry) {
		return new ComparableKeyValue<>(entry.getKey(), entry.getValue());
	}

	public static <K extends Comparable<K>,V>
	ComparableKeyValue<K,V> fromTuple(Tuple<? extends K,? extends V> tupl) {
		return new ComparableKeyValue<>(tupl._1, tupl._2);
	}

	private ComparableKeyValue(K key, V value) {
		super(key, value);
	}

	@Override
	public <U> ComparableKeyValue<K,U> mapValue(Function<? super V,? extends U> mapper) {
		return new ComparableKeyValue<>(key(), mapper.apply(value()));
	}

	@Override
	public <U> ComparableKeyValue<K,U> mapValue(BiFunction<? super K,? super V,? extends U> mapper) {
		return new ComparableKeyValue<>(key(), mapper.apply(key(), value()));
	}

	public static ComparableKeyValue<String,String> parse(String expr, char quote) {
		List<String> parts = CSV.parseCsv(expr, '=', quote)
								.map(String::trim)
								.toList();
		if ( parts.size() != 2 ) {
			throw new IllegalArgumentException("invalid key-value: " + expr);
		}

		return ComparableKeyValue.of(parts.get(0), parts.get(1));
	}

	public static ComparableKeyValue<String,String> parse(String expr) {
		List<String> parts = CSV.parseCsv(expr, '=')
								.map(String::trim)
								.toList();
		if ( parts.size() != 2 ) {
			throw new IllegalArgumentException("invalid key-value: " + expr);
		}

		return ComparableKeyValue.of(parts.get(0), parts.get(1));
	}

	@Override
	public int compareTo(ComparableKeyValue<K, V> o) {
		return key().compareTo(o.key());
	}
}
