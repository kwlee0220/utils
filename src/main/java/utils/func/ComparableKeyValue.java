package utils.func;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import utils.CSV;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class ComparableKeyValue<K extends Comparable<K>,V>
														implements Comparable<ComparableKeyValue<K,V>> {
	private final K m_key;
	private final V m_value;
	
	public static <K extends Comparable<K>,V>
	ComparableKeyValue<K,V> from(Map.Entry<? extends K,? extends V> entry) {
		return new ComparableKeyValue<>(entry.getKey(), entry.getValue());
	}
	
	public static <K extends Comparable<K>,V>
	ComparableKeyValue<K,V> from(Tuple<? extends K,? extends V> tupl) {
		return new ComparableKeyValue<>(tupl._1, tupl._2);
	}
	
	public static <K extends Comparable<K>,V> ComparableKeyValue<K,V> of(K key, V value) {
		return new ComparableKeyValue<>(key, value);
	}
	
	private ComparableKeyValue(K key, V value) {
		m_key = key;
		m_value = value;
	}
	
	public K key() {
		return m_key;
	}
	
	public V value() {
		return m_value;
	}
	
	public Tuple<K,V> toTuple() {
		return Tuple.of(m_key, m_value);
	}
	
	public <T> T map(BiFunction<? super K,? super V,? extends T> mapper) {
		return mapper.apply(m_key, m_value);
	}
	
	public <S extends Comparable<S>> ComparableKeyValue<S,V> mapKey(BiFunction<? super K,? super V,? extends S> mapper) {
		return new ComparableKeyValue<>(mapper.apply(m_key, m_value), m_value);
	}
	
	public <U> ComparableKeyValue<K,U> mapValue(Function<? super V,? extends U> mapper) {
		return new ComparableKeyValue<>(m_key, mapper.apply(m_value));
	}
	
	public <U> ComparableKeyValue<K,U> mapValue(BiFunction<? super K,? super V,? extends U> mapper) {
		return new ComparableKeyValue<>(m_key, mapper.apply(m_key, m_value));
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
	public String toString() {
		return "" + m_key + "=" + m_value;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_key, m_value);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		@SuppressWarnings("unchecked")
		ComparableKeyValue<K,V> other = (ComparableKeyValue<K,V>)obj;
		return Objects.equals(m_key, other.m_key)
			&& Objects.equals(m_value, other.m_value);
	}

	@Override
	public int compareTo(ComparableKeyValue<K, V> o) {
		return m_key.compareTo(o.m_key);
	}
}
