package utils.stream;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.vavr.Tuple2;
import io.vavr.control.Option;
import utils.func.FLists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Grouped<K,V> {
	private final Map<K,List<V>> m_groups;
	
	public static <K,V> Grouped<K,V> create() {
		return new Grouped<>();
	}
	
	public static <K,V> Grouped<K,V> fromMap(Map<K,List<V>> map) {
		return new Grouped<>(map);
	}
	
	private Grouped() {
		this(Maps.newHashMap());
	}
	
	private Grouped(Map<K,List<V>> map) {
		m_groups = map;
	}
	
	public Map<K,List<V>> asMap() {
		return Collections.unmodifiableMap(m_groups);
	}
	
	public List<V> get(final K key) {
		return m_groups.getOrDefault(key, Collections.unmodifiableList(Collections.emptyList()));
	}
	
	public void add(K key, V value) {
		Preconditions.checkNotNull(key);
		Preconditions.checkNotNull(value);
		
		m_groups.computeIfAbsent(key, k -> Lists.newArrayList())
			.add(value);
	}
	
	public void addAll(K key, Collection<V> values) {
		Preconditions.checkNotNull(key);
		Preconditions.checkNotNull(values);
		
		m_groups.computeIfAbsent(key, k -> Lists.newArrayList())
				.addAll(values);
	}
	
	public Option<List<V>> remove(K key) {
		Preconditions.checkNotNull(key);
		
		return Option.of(m_groups.remove(key));
	}
	
	public KVFStream<K,List<V>> fstream() {
		return KVFStream.of(m_groups);
	}
	
	public <A> FStream<Tuple2<K,A>> fold(A init, BiFunction<A,V,A> folder) {
		return new FoldedStream<>(m_groups, init, folder);
	}
	
	public FStream<Tuple2<K,V>> reduce(BiFunction<V,V,V> reducer) {
		return new ReducedStream<>(m_groups, reducer);
	}
	
	public Set<K> keySet() {
		return m_groups.keySet();
	}
	
	public Collection<List<V>> groups() {
		return m_groups.values();
	}
	
	public KVFStream<K, List<V>> stream() {
		return KVFStream.of(m_groups);
	}
	
	public <K2> Grouped<K2,V> mapKey(BiFunction<K,List<V>,K2> mapper) {
		return new Grouped<>(KVFStream.of(m_groups)
									.mapKey(mapper)
									.toMap());
	}
	
	public <V2> Grouped<K,V2> mapValue(Function<V,V2> mapper) {
		return new Grouped<>(KVFStream.of(m_groups)
									.mapValue(list -> (List<V2>)FStream.of(list)
															.map(mapper)
															.toList())
									.toMap());
	}
	
	public <V2> Grouped<K,V2> mapValueList(Function<List<V>, List<V2>> mapper) {
		return new Grouped<>(KVFStream.of(m_groups)
									.mapValue(mapper)
									.toMap());
	}
	
	@Override
	public String toString() {
		return m_groups.toString();
	}
	
	private static class FoldedStream<K,V,A> implements FStream<Tuple2<K,A>> {
		private final A m_init;
		private final Iterator<Map.Entry<K, List<V>>> m_iter;
		private final BiFunction<A,V,A> m_folder;
		
		FoldedStream(Map<K,List<V>> groups, A init, BiFunction<A,V,A> folder) {
			m_iter = groups.entrySet().iterator();
			m_init = init;
			m_folder = folder;
		}

		@Override
		public Option<Tuple2<K, A>> next() {
			if ( !m_iter.hasNext() ) {
				return Option.none();
			}
			
			Map.Entry<K, List<V>> ent = m_iter.next();
			A folded = FLists.foldLeft(ent.getValue(), m_init, m_folder);
			return Option.of(new Tuple2<>(ent.getKey(), folded));
		}
	}
	
	private static class ReducedStream<K,V> implements FStream<Tuple2<K,V>> {
		private final Iterator<Map.Entry<K, List<V>>> m_iter;
		private final BiFunction<V,V,V> m_reducer;
		
		ReducedStream(Map<K,List<V>> groups, BiFunction<V,V,V> reducer) {
			m_iter = groups.entrySet().iterator();
			m_reducer = reducer;
		}

		@Override
		public Option<Tuple2<K, V>> next() {
			if ( !m_iter.hasNext() ) {
				return Option.none();
			}
			
			Map.Entry<K, List<V>> ent = m_iter.next();
			V reduced = FLists.reduce(ent.getValue(), m_reducer).get();
			return Option.of(new Tuple2<>(ent.getKey(), reduced));
		}
	}
}
