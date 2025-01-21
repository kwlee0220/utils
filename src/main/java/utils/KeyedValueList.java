package utils;

import java.util.AbstractList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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
	
	/**
	 * 새로운 빈 {@link KeyedValueList} 객체를 생성한다.
	 * 
	 * @param <K>   키 값의 타입.
	 * @param <V>   값의 타입.
	 * @param keyer 값에서 키를 추출하는 함수.
	 * @return {@link KeyedValueList} 객체.
	 */
	public static <K,V> KeyedValueList<K,V> newInstance(Function<V,K> keyer) {
		return new KeyedValueList<>(keyer);
	}
	
	/**
	 * 주어진 리스트에 포함 값들을 초기값으로 하는 {@link KeyedValueList} 객체를 생성한다.
	 * 
	 * @param <K>        키 값의 타입.
	 * @param <V>        값의 타입.
	 * @param initValues 초기 값을 포한한 리스트.
	 * @param keyer      값에서 키를 추출하는 함수.
	 * @return {@link KeyedValueList} 객체.
	 */
	public static <K,V> KeyedValueList<K,V> from(Iterable<? extends V> initValues, Function<V,K> keyer) {
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
	
	/**
	 * 주어진 값이 키 값이 존재하지 않을 경우에만 추가한다.
	 * 
	 * @param value 추가할 값.
	 */
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
	
	/**
	 * 주어진 값과 동일한 키 값을 가지는 값이 존재할 경우 주어진 값으로 대체한다.
	 * <p>
	 * 동일한 키 값을 가지는 값이 존재하지 않을 경우는 아무런 동작을 하지 않고, {@code null}을 반환한다.
	 * 
	 * @param value 제거할 값.
	 * @return 제거된 값. 동일한 키 값을 가지는 값이 존재하지 않을 경우는 {@code null}.
	 */
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
	
	/**
	 * 주어진 값을 추가한다.
	 * <p>
	 * 만일 동일한 키 값을 가지는 값이 존재할 경우는 해당 값을 제거하고 주어진 값을 추가한다.
	 * 
	 * @param value 제거할 값.
	 * @return 제거된 값. 동일한 키 값을 가지는 값이 존재하지 않을 경우는 {@code null}.
	 */
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
	
	/**
	 * 리스트에 포함된 모든 값들의 키 값을 반환한다.
	 * 
	 * @return 키 값
	 */
	public LinkedHashSet<K> keySet() {
		LinkedHashSet<K> keys = Sets.newLinkedHashSet();
		return FStream.from(m_keyValues).map(KeyValue::key).toCollection(keys);
	}
	
	/**
	 * 주어진 키 값을 가지는 값이 존재하는지 여부를 반환한다.
	 * 
	 * @param key 키 값.
	 * @return 값이 존재하는지 여부.
	 */
	public boolean containsKey(K key) {
		return Funcs.exists(m_keyValues, kv -> kv.key().equals(key));
	}
	
	/**
	 * 주어진 키 값을 가지는 값을 반환한다.
	 * <p>
	 * 만일 주어진 키 값을 가지는 값이 존재하지 않을 경우는 {@code null}을 반환한다.
	 * 
	 * @param key 키 값.
	 * @return 값. 주어진 키 값을 가지는 값이 존재하지 않을 경우는 {@code null}.
	 */
	public @Nullable V getOfKey(K key) {
		return Funcs.findFirst(m_keyValues, kv -> kv.key().equals(key))
					.map(KeyValue::value)
					.getOrNull();
	}
	
	/**
	 * 주어진 키 값을 가지는 값의 인덱스를 반환한다.
	 * <p>
	 * 만일 주어진 키 값을 가지는 값이 존재하지 않을 경우는 {@code -1}을 반환한다.
	 * 
	 * @param key 키 값.
	 * @return 값의 인덱스. 주어진 키 값을 가지는 값이 존재하지 않을 경우는 {@code -1}.
	 */
	public int indexOfKey(K key) {
		return Funcs.findFirstIndexed(m_keyValues, kv -> kv.key().equals(key))
						.map(Indexed::index)
						.getOrElse(-1);
	}
	
	/**
	 * 주어진 키 값을 가지는 값을 제거한다.
	 * <p>
	 * 만일 주어진 키 값을 가지는 값이 존재하지 않을 경우는 아무런 동작을 하지 않고, {@code null}을 반환한다.
	 * 
	 * @param key 제거할 값의 키 값.
	 * @return 제거된 값. 주어진 키 값을 가지는 값이 존재하지 않을 경우는 {@code null}.
	 */
	public V removeOfKey(K key) {
		KeyValue<K,V> removed = Funcs.removeFirstIf(m_keyValues, kv -> kv.key().equals(key));
		return (removed != null) ? removed.value() : null;
	}
	
	/**
	 * 키 값 기반 리스트를 {@link Map} 객체로 변환한다.
	 * 
	 * @return {@link Map} 객체.
	 */
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
