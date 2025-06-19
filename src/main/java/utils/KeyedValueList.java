package utils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import lombok.experimental.Delegate;

import utils.func.Funcs;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyedValueList<K,V> implements List<V> {
	@Delegate private final List<V> m_values;
	private final Function<V,K> m_keyer;
	
	public KeyedValueList(Function<V,K> keyer) {
		m_keyer = keyer;
		m_values = Lists.newArrayList();
	}

	public KeyValueFStream<K,V> fstream() {
		return FStream.from(m_values)
		        		.toKeyValueStream(v -> KeyValue.of(m_keyer.apply(v), v));
	}
	
	/**
	 * 주어진 {@link Iterable} 객체로부터 {@link KeyedValueList} 객체를 생성한다.
	 * <p>
	 * {@code Iterable} 객체에 포함된 값들은 {@link Keyed} 인터페이스를 구현해야 한다.
	 * 
	 * @param <K> 키 값의 타입.
	 * @param <V> 값의 타입.
	 * @param values 값의 {@link Iterable} 객체.
	 */
	public static <K, V> KeyedValueList<K, V> with(Function<V,K> keyer) {
		Preconditions.checkArgument(keyer != null, "Function<V,K> keyer is null");
		
		return new KeyedValueList<>(keyer);
	}

	/**
	 * 주어진 {@link Iterable} 객체로부터 {@link KeyedValueList} 객체를 생성한다.
	 * <p>
	 * {@code Iterable} 객체에 포함된 값들은 {@link Keyed} 인터페이스를 구현해야 한다.
	 * 
	 * @param <K> 키 값의 타입.
	 * @param <V> 값의 타입.
	 * @param values 값의 {@link Iterable} 객체.
	 */
	public static <K, V> KeyedValueList<K, V> from(Iterable<V> values, Function<V,K> keyer) {
		Preconditions.checkArgument(values != null, "values is null");
		
		KeyedValueList<K,V> kvList = new KeyedValueList<>(keyer);
		FStream.from(values).forEach(kvList::add);
		
		return kvList;
	}

	/**
	 * 주어진 값이 키 값이 존재하지 않을 경우에만 추가한다.
	 * 만일 동일한 키 값을 가지는 값이 존재할 경우는 {@code IllegalArgumentException} 예외를 발생시킨다.
	 * 
	 * @param value 추가할 값.
	 * @return 값이 추가되었는지 여부.
	 */
	@Override
	public boolean add(V value) {
		Preconditions.checkArgument(value != null, "value is null");
		
		K key = m_keyer.apply(value);
		if ( Funcs.exists(m_values, v -> m_keyer.apply(v).equals(key)) ) {
			throw newDuplicationError(key, value);
		}
		
		return m_values.add(value);
	}
	
	/**
	 * 주어진 값이 키 값이 존재하지 않을 경우에만 추가한다.
	 * <p>
	 * 만일 동일한 키 값을 가지는 값이 존재할 경우는 아무런 동작을 하지 않고, {@code false}를 반환한다.
	 * 
	 * @param value 추가할 값.
	 */
	public boolean addIfAbscent(V value) {
		Preconditions.checkArgument(value != null, "value is null");

		K key = m_keyer.apply(value);
		if ( Funcs.exists(m_values, v -> m_keyer.apply(v).equals(key)) ) {
			return false;
		}
		
		return m_values.add(value);
	}

	@Override
	public void add(int index, V value) {
		Preconditions.checkArgument(value != null, "value is null");
		assertValidIndex(index);

		K key = m_keyer.apply(value);
		if ( Funcs.exists(m_values, v -> m_keyer.apply(v).equals(key)) ) {
			throw newDuplicationError(key, value);
		}
		
		m_values.add(index, value);
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
			V removed = m_values.remove(idx);
			m_values.add(idx, value);
			
			return removed;
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
			V removed = m_values.remove(idx);
			m_values.add(idx, value);
			
			return removed;
		}
		else {
			m_values.add(value);
			return null;
		}
	}
	
	/**
	 * 리스트에 포함된 모든 값들의 키 값을 반환한다.
	 * 
	 * @return 키 값
	 */
	public LinkedHashSet<K> keySet() {
		LinkedHashSet<K> keys = Sets.newLinkedHashSet();
		return FStream.from(m_values).map(m_keyer).toCollection(keys);
	}
	
	/**
	 * 주어진 키 값을 가지는 값이 존재하는지 여부를 반환한다.
	 * 
	 * @param key 키 값.
	 * @return 값이 존재하는지 여부.
	 */
	public boolean containsKey(K key) {
		return Funcs.exists(m_values, v -> m_keyer.apply(v).equals(key));
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
		Preconditions.checkArgument(key != null, "key is null");
		
		return Funcs.findFirst(m_values, v -> m_keyer.apply(v).equals(key))
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
		Preconditions.checkArgument(key != null, "key is null");
		
		return Funcs.findFirstIndexed(m_values, v -> m_keyer.apply(v).equals(key))
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
		Preconditions.checkArgument(key != null, "key is null");
		
		return Funcs.removeFirstIf(m_values, v -> m_keyer.apply(v).equals(key));
	}
	
	/**
	 * 키 값 기반 리스트를 {@link Map} 객체로 변환한다.
	 * 
	 * @return {@link Map} 객체.
	 */
	public Map<K,V> toMap() {
		return fstream().toMap();
	}
	
	@Override
	public String toString() {
		return m_values.toString();
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
