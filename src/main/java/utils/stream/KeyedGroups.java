package utils.stream;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.func.FOption;
import utils.func.MultipleCases;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyedGroups<K,V> implements FStreamable<KeyValue<K,List<V>>> {
	private final Map<K,List<V>> m_groups;
	
	public static <K,V> KeyedGroups<K,V> create() {
		return new KeyedGroups<>();
	}
	
	private KeyedGroups() {
		m_groups = Maps.newHashMap();
	}
	
	public Map<K,List<V>> asMap() {
		return m_groups;
	}
	
	public Set<K> keySet() {
		return m_groups.keySet();
	}
	
	public int groupCount() {
		return m_groups.size();
	}
	
	public long size() {
		return KeyValueFStream.from(m_groups)
					    .values()
						.mapToInt(List::size)
						.sum();
	}
	
	public List<V> get(final K key) {
		return m_groups.get(key);
	}
	
	public List<V> getOrDefault(final K key, List<V> defaultValue) {
		return m_groups.getOrDefault(key, defaultValue);
	}
	
	public KeyedGroups<K,V> add(K key, V value) {
		Preconditions.checkArgument(value != null, "value is null");
		
		m_groups.computeIfAbsent(key, k -> Lists.newArrayList())
				.add(value);
		return this;
	}
	
	public FOption<List<V>> remove(K key) {
		return FOption.ofNullable(m_groups.remove(key));
	}
	
	public KeyValueFStream<K,List<V>> fstream() {
		return KeyValueFStream.from(m_groups);
	}
	
	public KeyValueFStream<K,V> ungroup() {
		return KeyValueFStream.from(m_groups)
						.flatMap(kv -> FStream.from(kv.value())
												.map(v -> KeyValue.of(kv.key(), v)))
						.toKeyValueStream(v -> v);
	}
	
	@Override
	public String toString() {
		return KeyValueFStream.from(m_groups)
						.map(kv -> String.format("%s:%d", kv.key(), kv.value().size()))
						.join(", ");
	}
	
	public MultipleCases<K,List<V>> switcher() {
		return MultipleCases.switching(asMap());
	}
}
