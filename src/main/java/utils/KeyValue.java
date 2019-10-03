package utils;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.vavr.Tuple2;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class KeyValue<K,V> {
	private final K m_key;
	private final V m_value;
	
	public static <K,V> KeyValue<K,V> from(Tuple2<? extends K,? extends V> tupl) {
		return new KeyValue<>(tupl._1, tupl._2);
	}
	
	public static <K,V> KeyValue<K,V> of(K key, V value) {
		return new KeyValue<>(key, value);
	}
	
	private KeyValue(K key, V value) {
		Utilities.checkNotNullArgument(key, "key is null");
		
		m_key = key;
		m_value = value;
	}
	
	public K key() {
		return m_key;
	}
	
	public V value() {
		return m_value;
	}
	
	public <T> T map(BiFunction<? super K,? super V,? extends T> mapper) {
		return mapper.apply(m_key, m_value);
	}
	
	public <S> KeyValue<S,V> mapKey(BiFunction<? super K,? super V,? extends S> mapper) {
		return new KeyValue<>(mapper.apply(m_key, m_value), m_value);
	}
	
	public <U> KeyValue<K,U> mapValue(Function<? super V,? extends U> mapper) {
		return new KeyValue<>(m_key, mapper.apply(m_value));
	}
	
	public <U> KeyValue<K,U> mapValue(BiFunction<? super K,? super V,? extends U> mapper) {
		return new KeyValue<>(m_key, mapper.apply(m_key, m_value));
	}
	
	public static KeyValue<String,String> parse(String expr, char quote) {
		List<String> parts = CSV.parseCsv(expr, '=', quote)
								.map(String::trim)
								.toList();
		if ( parts.size() != 2 ) {
			throw new IllegalArgumentException("invalid key-value: " + expr);
		}
		
		return KeyValue.of(parts.get(0), parts.get(1));
	}
	
	public static KeyValue<String,String> parse(String expr) {
		List<String> parts = CSV.parseCsv(expr, '=')
								.map(String::trim)
								.toList();
		if ( parts.size() != 2 ) {
			throw new IllegalArgumentException("invalid key-value: " + expr);
		}
		
		return KeyValue.of(parts.get(0), parts.get(1));
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
		KeyValue<K,V> other = (KeyValue<K,V>)obj;
		return Objects.equals(m_key, other.m_key)
			&& Objects.equals(m_value, other.m_value);
	}
}
