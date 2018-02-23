package utils.stream;

import java.util.Objects;
import java.util.function.Function;

import io.vavr.Tuple2;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValue<K,V> {
	private final K m_key;
	private final V m_value;
	
	public static <K,V> KeyValue<K,V> from(Tuple2<K,V> t) {
		return new KeyValue<>(t._1, t._2);
	}
	
	public KeyValue(K key, V value) {
		m_key = key;
		m_value = value;
	}
	
	public K key() {
		return m_key;
	}
	
	public V value() {
		return m_value;
	}
	
	public <S> KeyValue<S,V> mapKey(Function<K,S> mapper) {
		return new KeyValue<>(mapper.apply(m_key), m_value);
	}
	
	public <U> KeyValue<K,U> mapValue(Function<V,U> mapper) {
		return new KeyValue<>(m_key, mapper.apply(m_value));
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
