package utils.stream;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.func.FOption;
import utils.func.MultipleCases;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyedGroups<K,V> implements Iterable<KeyValue<K,List<V>>> {
	private final Map<K,List<V>> m_groups;
	
	public static <K,V> KeyedGroups<K,V> create() {
		return new KeyedGroups<>();
	}
	
	public static <K,V> KeyedGroups<K,V> from(Map<K,Collection<V>> map) {
		KeyedGroups<K,V> groups = create();
		map.forEach(groups::addAll);
		return groups;
	}
	
	private KeyedGroups() {
		this(Maps.newHashMap());
	}
	
	private KeyedGroups(Map<K,List<V>> map) {
		m_groups = map;
	}
	
	public int groupCount() {
		return m_groups.size();
	}
	
	public Set<K> keys() {
		return m_groups.keySet();
	}
	
	public long size() {
		return KVFStream.from(m_groups)
						.toValueStream()
						.mapToInt(List::size)
						.sum();
	}
	
	public Map<K,List<V>> asMap() {
		return m_groups;
	}
	
	public boolean containsKey(K key) {
		return m_groups.containsKey(key);
	}
	
	public List<V> getOrEmptyList(final K key) {
		return m_groups.getOrDefault(key, Collections.emptyList());
	}
	
	public @Nullable List<V> get(final K key) {
		return m_groups.get(key);
	}
	
	public KeyedGroups<K,V> add(K key, V value) {
		Objects.requireNonNull(value);
		
		m_groups.computeIfAbsent(key, k -> Lists.newArrayList())
				.add(value);
		return this;
	}
	
	KeyedGroups<K,V> put(K key, List<V> values) {
		m_groups.compute(key, (k, old) -> {
			if ( old == null ) {
				return values;
			}
			else {
				old.addAll(values);
				return old;
			}
		});
		return this;
	}
	
	public KeyedGroups<K,V> addAll(K key, Iterator<? extends V> values) {
		Objects.requireNonNull(values);
		
		m_groups.compute(key, (k, group) -> {
			if ( group == null ) {
				group = Lists.newArrayList();
			}
			while ( values.hasNext() ) {
				group.add(values.next());
			}
			
			return group;
		});
		return this;
	}
	
	public KeyedGroups<K,V> addAll(K key, Collection<? extends V> values) {
		Objects.requireNonNull(values);
		
		m_groups.compute(key, (k, group) -> {
			if ( group == null ) {
				return Lists.newArrayList(values);
			}
			else {
				group.addAll(values);
				return group;
			}
		});
		return this;
	}
	
	public KeyedGroups<K,V> addAll(K key, FStream<? extends V> values) {
		Objects.requireNonNull(values);
		
		m_groups.compute(key, (k, group) -> {
			if ( group == null ) {
				group = Lists.newArrayList();
			}
			values.forEach(group::add);
			
			return group;
		});
		return this;
	}
	
	public FOption<List<V>> remove(K key) {
		return FOption.ofNullable(m_groups.remove(key));
	}
	
	public KVFStream<K,List<V>> stream() {
		return KVFStream.from(m_groups);
	}
	
	public KVFStream<K,V> ungroup() {
		return KVFStream.downcast(stream().flatMap(this::ungroup));
	}
	
	public <K2> KeyedGroups<K2,V> mapKey(BiFunction<? super K,List<V>,? extends K2> mapper) {
		return stream().mapKey(mapper)
						.fold(create(), (groups,kv) -> groups.addAll(kv.key(), kv.value()));
	}
	
	public <V2> KeyedGroups<K,V2> mapValue(BiFunction<? super K,? super V,? extends V2> mapper) {
		return stream().fold(create(), (groups,kv) -> {
			K key = kv.key();
			Function<? super V,? extends V2> curried = v -> mapper.apply(key, v);
			return groups.put(key, FStream.from(kv.value())
											.map(v -> (V2)curried.apply(v))
											.toList());
		});
	}
	
	public <V2> KeyedGroups<K,V2> mapValueList(BiFunction<? super K,? super List<V>,? extends List<V2>> mapper) {
		return stream().fold(create(), (groups,kv) -> {
			K key = kv.key();
			Function<List<V>,List<V2>> curried = v -> mapper.apply(key, v);
			return groups.put(key, curried.apply(kv.value()));
		});
	}
	
	public Set<K> keySet() {
		return m_groups.keySet();
	}
	
	public Collection<List<V>> values() {
		return m_groups.values();
	}

	@Override
	public Iterator<KeyValue<K, List<V>>> iterator() {
		return KVFStream.from(m_groups).iterator();
	}
	
	@Override
	public String toString() {
		return KVFStream.from(m_groups)
						.map((key,list) -> String.format("%s:%d", key, list.size()))
						.join(", ");
	}
	
	private FStream<KeyValue<K,V>> ungroup(KeyValue<K,List<V>> group) {
		return FStream.from(group.value()).map(v -> KeyValue.of(group.key(), v));
	}
	
	public MultipleCases<K,List<V>> switcher() {
		return MultipleCases.switching(asMap());
	}
}
