package utils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import lombok.experimental.Delegate;

import utils.func.Funcs;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;

/**
 * 각 값에서 키 추출 함수({@code keyer})로 구한 키가 서로 겹치지 않도록 관리하는 {@link List}.
 * <p>
 * 값은 {@link java.util.ArrayList}에 삽입 순서대로 저장되며, 키 비교는 {@code equals}로 한다.
 * 키 기반 조회({@link #getOfKey(Object)}, {@link #indexOfKey(Object)} 등)는 리스트를 처음부터 훑는다.
 * 동기화하지 않는다.
 * <p>
 * <b>주의:</b> 키 중복은 {@link #add(Object)}, {@link #add(int, Object)}, {@link #addIfAbsent(Object)}에서만
 * 검사한다. Lombok {@code @Delegate}로 내부 리스트에 그대로 위임되는 {@code addAll}, {@code set},
 * {@code listIterator().add} 등은 검사하지 않으므로 중복 키나 {@code null} 값이 들어갈 수 있다.
 *
 * @param <K>	키의 타입.
 * @param <V>	값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyedValueList<K,V> implements List<V> {
	@Delegate private final List<V> m_values;
	private final Function<V,K> m_keyer;
	
	/**
	 * 주어진 키 추출 함수를 쓰는 빈 리스트를 만든다.
	 * <p>
	 * {@code keyer}의 {@code null} 여부를 검사하지 않으므로, {@code null}이면 값을 추가할 때
	 * {@link NullPointerException}이 발생한다.
	 *
	 * @param keyer	값에서 키를 추출하는 함수.
	 */
	public KeyedValueList(Function<V,K> keyer) {
		m_keyer = keyer;
		m_values = Lists.newArrayList();
	}

	/**
	 * 각 값에 {@code keyer}로 구한 키를 붙인 스트림을 반환한다.
	 *
	 * @return	(키, 값) 스트림.
	 */
	public KeyValueFStream<K,V> fstream() {
		return FStream.from(m_values).tagKey(m_keyer);
	}
	
	/**
	 * 주어진 키 추출 함수를 쓰는 빈 {@link KeyedValueList} 객체를 생성한다.
	 * 
	 * @param <K> 키 값의 타입.
	 * @param <V> 값의 타입.
	 * @param keyer 키 값을 추출하는 함수.
	 * @return	새 {@link KeyedValueList} 객체.
	 * @throws IllegalArgumentException	{@code keyer}가 {@code null}인 경우.
	 */
	public static <K, V> KeyedValueList<K, V> with(Function<V,K> keyer) {
		Preconditions.checkArgument(keyer != null, "Function<V,K> keyer is null");
		
		return new KeyedValueList<>(keyer);
	}

	/**
	 * 주어진 {@link Iterable} 객체로부터 {@link KeyedValueList} 객체를 생성한다.
	 * <p>
	 * {@code values}의 값들을 순서대로 {@link #add(Object)}로 추가한다. {@code keyer}의 {@code null} 여부는
	 * 검사하지 않으므로, {@code null}이고 {@code values}가 비어 있지 않으면 {@link NullPointerException}이 발생한다.
	 * 
	 * @param <K> 키 값의 타입.
	 * @param <V> 값의 타입.
	 * @param values 값의 {@link Iterable} 객체.
	 * @param keyer	키 값을 추출하는 함수.
	 * @return	새 {@link KeyedValueList} 객체.
	 * @throws IllegalArgumentException	{@code values}가 {@code null}이거나, {@code null} 값을 포함하거나,
	 * 			같은 키를 가지는 값이 둘 이상 있는 경우.
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
	 * @return	값이 추가되었으면 {@code true}, 동일한 키 값을 가지는 값이 이미 있으면 {@code false}.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}인 경우.
	 */
	public boolean addIfAbsent(V value) {
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
		
		return Funcs.findFirst(m_values, v -> m_keyer.apply(v).equals(key));
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
		
		var indexed = Funcs.findFirstIndexed(m_values, v -> m_keyer.apply(v).equals(key));
		return indexed != null ? indexed.index() : -1;
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
	public LinkedHashMap<K,V> toMap() {
		return fstream().toMap(new LinkedHashMap<>());
	}
	
	@Override
	public String toString() {
		return m_values.toString();
	}
	
	/**
	 * 주어진 인덱스가 삽입 위치로 유효한지({@code 0 <= index <= size()}) 검사한다.
	 * <p>
	 * <b>주의:</b> 예외 메시지의 {@code Size} 부분에 리스트 크기가 아니라 {@code index}가 들어간다.
	 *
	 * @param index	검사할 인덱스.
	 * @throws IndexOutOfBoundsException	인덱스가 범위를 벗어난 경우.
	 */
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
