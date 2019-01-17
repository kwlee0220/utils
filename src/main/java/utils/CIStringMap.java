package utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import utils.stream.FStream;
import utils.stream.KVFStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CIStringMap<T> implements Map<String,T> {
	private final Map<CIString,T> m_map;
	
	public static <T> CIStringMap<T> newHashMap() {
		return new CIStringMap<>(new HashMap<>());
	}
	
	public static <T> CIStringMap<T> newLinkedHashMap() {
		return new CIStringMap<>(new LinkedHashMap<>());
	}
	
	private CIStringMap(Map<CIString,T> base) {
		Objects.requireNonNull(base);
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
		 return KVFStream.of(m_map)
				 		.mapKey((k,v) -> k.get())
				 		.toMap()
				 		.entrySet();
	}

	@Override
	public T remove(Object key) {
		return m_map.remove(CIString.of((String)key));
	}

	@Override
	public void putAll(Map<? extends String, ? extends T> m) {
		KVFStream.of(m)
				.mapKey(CIString::of)
				.forEach((k,v) -> m_map.put(k, v));
	}

	@Override
	public void clear() {
		m_map.clear();
	}

	@Override
	public Set<String> keySet() {
		return FStream.of(m_map.keySet()).map(CIString::get).toHashSet();
	}

	@Override
	public Collection<T> values() {
		return m_map.values();
	}
}
