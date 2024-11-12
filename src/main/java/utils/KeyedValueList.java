package utils;

import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;

import utils.func.Funcs;
import utils.stream.FStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyedValueList<K,V> extends AbstractList<V> {
	private final Function<V,K> m_keyer;
	private final List<KeyValue<K,V>> m_keyValues = Lists.newArrayList();
	
	protected KeyedValueList(Function<V,K> keyer) {
		m_keyer = keyer;
	}
	
	public static <K,V> KeyedValueList<K,V> newInstance(Function<V,K> keyer) {
		return new KeyedValueList<>(keyer);
	}
	
	public static <K,V> KeyedValueList<K,V> from(Iterable<V> initValues, Function<V,K> keyer) {
		KeyedValueList<K,V> kvList = new KeyedValueList<>(keyer);
		FStream.from(initValues).forEach(kvList::add);
		
		return kvList;
	}

	@Override
	public int size() {
		return m_keyValues.size();
	}

	@Override
	public V get(int index) {
		return m_keyValues.get(index).value();
	}

	@Override
	public boolean add(V value) {
		K key = m_keyer.apply(value);
		if ( Funcs.exists(m_keyValues, kv -> kv.key().equals(key)) ) {
			throw newDuplicationError(key, value);
		}
		
		return m_keyValues.add(KeyValue.of(key, value));
	}
	
	public void addIfAbscent(V value) {
		K key = m_keyer.apply(value);
		if ( !Funcs.exists(m_keyValues, kv -> kv.key().equals(key)) ) {
			m_keyValues.add(KeyValue.of(key, value));
		}
	}

	@Override
	public void add(int index, V value) {
		assertValidIndex(index);
		
		K key = m_keyer.apply(value);
		if ( Funcs.exists(m_keyValues, kv -> kv.key().equals(key)) ) {
			throw newDuplicationError(key, value);
		}
		
		m_keyValues.add(index, KeyValue.of(key, value));
	}
	
	@Override
	public V remove(int index) {
		assertValidIndex(index);
		
		return m_keyValues.remove(index).value();
	}
	
	public V replace(V value) {
		K key = m_keyer.apply(value);
		
		int idx = indexOfKey(key);
		if ( idx >= 0 ) {
			KeyValue<K,V> removed = m_keyValues.remove(idx);
			m_keyValues.add(idx, KeyValue.of(key, value));
			
			return removed.value();
		}
		else {
			return null;
		}
	}
	
	public V addOrReplace(V value) {
		K key = m_keyer.apply(value);
		
		int idx = indexOfKey(key);
		if ( idx >= 0 ) {
			KeyValue<K,V> removed = m_keyValues.remove(idx);
			m_keyValues.add(idx, KeyValue.of(key, value));
			
			return removed.value();
		}
		else {
			m_keyValues.add(KeyValue.of(key, value));
			return null;
		}
	}
	
	@Override
	public void clear() {
		m_keyValues.clear();
	}
	
	public boolean containsKey(K key) {
		return Funcs.exists(m_keyValues, kv -> kv.key().equals(key));
	}
	
	public @Nullable V getOfKey(K key) {
		return Funcs.findFirst(m_keyValues, kv -> kv.key().equals(key))
					.map(KeyValue::value)
					.getOrNull();
	}
	
	public int indexOfKey(K key) {
		return Funcs.findFirstIndexed(m_keyValues, kv -> kv.key().equals(key))
						.map(Indexed::index)
						.getOrElse(-1);
	}
	
	public boolean removeOfKey(K key) {
		return m_keyValues.removeIf(kv -> kv.key().equals(key));
	}
	
	public Map<K,V> toMap() {
		return FStream.from(m_keyValues).toMap(KeyValue::key, KeyValue::value);
	}
	
	protected void assertValidIndex(int index) {
		if (index > size() || index < 0) {
	        throw new IndexOutOfBoundsException("Index: " + index + ", Size " + index);
	    }
	}
	
	private IllegalArgumentException newDuplicationError(K key, V value) {
		String msg = String.format("Same key exists: key=%s, value=%s", key, value);
		return new IllegalArgumentException(msg);
	}
}
