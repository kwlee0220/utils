package utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import utils.stream.FStream;
import utils.stream.KeyValueFStream;

/**
 * {@link String} 키를 대소문자 구분 없이 다루는 {@link Map}.
 * <p>
 * 내부적으로 {@link CIString}을 키로 하는 맵에 위임한다. {@code get}, {@code containsKey}, {@code remove}에
 * {@code null} 키를 넘기면 {@link IllegalArgumentException}이, {@link String}이 아닌 키를 넘기면
 * {@link ClassCastException}이 발생한다. 동기화하지 않는다.
 * <p>
 * <b>주의:</b> {@link #keySet()}과 {@link #entrySet()}은 호출 시점의 복사본(해시 기반)을 돌려주므로,
 * {@link #newLinkedHashMap()}으로 만든 경우에도 삽입 순서가 보존되지 않고, 복사본을 수정해도 이 맵에
 * 반영되지 않는다. 반면 {@link #values()}는 내부 맵의 뷰이다. 또 {@code equals}/{@code hashCode}/{@code toString}을
 * 재정의하지 않아 {@link Map}의 규약(내용 기반 비교)을 따르지 않는다.
 *
 * @param <T>	값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class CIStringMap<T> implements Map<String,T> {
	private final Map<CIString,T> m_map;
	
	/**
	 * {@link HashMap}을 내부 맵으로 쓰는 빈 {@link CIStringMap}을 만든다.
	 *
	 * @param <T>	값의 타입.
	 * @return	새 {@link CIStringMap} 객체.
	 */
	public static <T> CIStringMap<T> newHashMap() {
		return new CIStringMap<>(new HashMap<>());
	}
	
	/**
	 * {@link LinkedHashMap}을 내부 맵으로 쓰는 빈 {@link CIStringMap}을 만든다.
	 *
	 * @param <T>	값의 타입.
	 * @return	새 {@link CIStringMap} 객체.
	 */
	public static <T> CIStringMap<T> newLinkedHashMap() {
		return new CIStringMap<>(new LinkedHashMap<>());
	}
	
	private CIStringMap(Map<CIString,T> base) {
		Preconditions.checkNotNullArgument(base, "base map is null");
		m_map = base;
	}

	@Override
	public boolean isEmpty() {
		return m_map.isEmpty();
	}
	
	@Override
    public int size() {
        return m_map.size();
    }
	
	@Override
    public T get(Object name) {
    	return m_map.get(CIString.of((String)name));
    }
	
	@Override
    public T put(String key, T value) {
		return m_map.put(CIString.of((String)key), value);
	}

	@Override
	public boolean containsKey(Object key) {
		return m_map.containsKey(CIString.of((String)key));
	}

	@Override
	public boolean containsValue(Object value) {
		return m_map.containsValue(value);
	}
    
	@Override
	public Set<Entry<String, T>> entrySet() {
		 return KeyValueFStream.from(m_map)
				 		.mapKey(k -> k.get())
				 		.toMap()
				 		.entrySet();
	}

	@Override
	public T remove(Object key) {
		return m_map.remove(CIString.of((String)key));
	}

	@Override
	public void putAll(Map<? extends String, ? extends T> m) {
		KeyValueFStream.from(m)
				.mapKey(CIString::of)
				.forEach((k,v) -> m_map.put(k, v));
	}

	@Override
	public void clear() {
		m_map.clear();
	}

	@Override
	public Set<String> keySet() {
		return FStream.from(m_map.keySet()).map(CIString::get).toSet();
	}

	@Override
	public Collection<T> values() {
		return m_map.values();
	}
}
