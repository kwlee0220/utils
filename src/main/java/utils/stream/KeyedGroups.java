package utils.stream;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyedGroups<K,V> {
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
	
	public int size() {
		return m_groups.size();
	}
	
	public Map<K,List<V>> asMap() {
		return m_groups;
	}
	
	public List<V> get(final K key) {
		return m_groups.getOrDefault(key, Collections.emptyList());
	}
	
	public KeyedGroups<K,V> add(K key, V value) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(value);
		
		m_groups.computeIfAbsent(key, k -> Lists.newArrayList())
				.add(value);
		return this;
	}
	
	KeyedGroups<K,V> put(K key, List<V> values) {
		Objects.requireNonNull(key);
		
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
		Objects.requireNonNull(key);
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
		Objects.requireNonNull(key);
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
		Objects.requireNonNull(key);
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
	
	public Option<Collection<V>> remove(K key) {
		Objects.requireNonNull(key);
		
		return Option.of(m_groups.remove(key));
	}
	
	public KVFStream<K,List<V>> fstream() {
		return KVFStream.of(m_groups);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public KVFStream<K,V> ungroup() {
		return new KVFStreamImpl("ungrouped", () -> fstream().flatMap(this::ungroup).toSupplier());
	}
	
	public <K2> KeyedGroups<K2,V> mapKey(BiFunction<? super K,List<V>,? extends K2> mapper) {
		return fstream().mapKey(mapper)
						.foldLeft(create(), (groups,kv) -> groups.addAll(kv.key(), kv.value()));
	}
	
	public <V2> KeyedGroups<K,V2> mapValue(BiFunction<? super K,? super V,? extends V2> mapper) {
		return fstream().foldLeft(create(), (groups,kv) -> {
			K key = kv.key();
			Function<? super V,? extends V2> curried = v -> mapper.apply(key, v);
			return groups.put(key, FStream.of(kv.value())
											.map(v -> (V2)curried.apply(v))
											.toList());
		});
	}
	
	public <V2> KeyedGroups<K,V2> mapValueList(BiFunction<? super K,? super List<V>,? extends List<V2>> mapper) {
		return fstream().foldLeft(create(), (groups,kv) -> {
			K key = kv.key();
			Function<List<V>,List<V2>> curried = v -> mapper.apply(key, v);
			return groups.put(key, curried.apply(kv.value()));
		});
	}
	
//	public <A> KVFStream<K,A> fold(A init, BiFunction<? super A,? super V,? extends A> folder) {
//		return new FoldedStream<>(m_groups, init, folder);
//	}
//	
//	public KVFStream<K,V> reduce(BiFunction<? super V,? super V,? extends V> reducer) {
//		return new ReducedStream<>(m_groups, reducer);
//	}
	
	public Set<K> keySet() {
		return m_groups.keySet();
	}
	
	public Collection<List<V>> values() {
		return m_groups.values();
	}
	
	@Override
	public String toString() {
		return m_groups.toString();
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private KVFStream<K,V> ungroup(KeyValue<K,List<V>> group) {
		Supplier<Option<KeyValue<K,V>>> supplier = FStream.of(group.value())
														.map(v -> new KeyValue<>(group.key(),v))
														.toSupplier();
		return new KVFStreamImpl("ungrouped", () -> supplier);
	}
	
	private static class FoldedStream<K,V,A> implements KVFStream<K,A> {
		private final A m_init;
		private final Iterator<Entry<K, List<V>>> m_iter;
		private final BiFunction<? super A,? super V,? extends A> m_folder;
		
		FoldedStream(Map<K,List<V>> groups, A init,
					BiFunction<? super A,? super V,? extends A> folder) {
			m_iter = groups.entrySet().iterator();
			m_init = init;
			m_folder = folder;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public Option<KeyValue<K, A>> next() {
			if ( !m_iter.hasNext() ) {
				return Option.none();
			}
			
			Entry<K, List<V>> ent = m_iter.next();
			A folded = FStream.of(ent.getValue()).foldLeft(m_init, m_folder);
			return Option.of(new KeyValue<>(ent.getKey(), folded));
		}
	}
	
	private static class ReducedStream<K,V> implements KVFStream<K,V> {
		private final Iterator<Map.Entry<K, List<V>>> m_iter;
		private final BiFunction<? super V,? super V,? extends V> m_reducer;
		
		ReducedStream(Map<K,List<V>> groups, BiFunction<? super V,? super V,? extends V> reducer) {
			m_iter = groups.entrySet().iterator();
			m_reducer = reducer;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public Option<KeyValue<K, V>> next() {
			if ( !m_iter.hasNext() ) {
				return Option.none();
			}
			
			Entry<K, List<V>> ent = m_iter.next();
			V reduced = FStream.of(ent.getValue()).reduce(m_reducer);
			return Option.of(new KeyValue<>(ent.getKey(), reduced));
		}
	}
}
