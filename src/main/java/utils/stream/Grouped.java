package utils.stream;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

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
	private final Map<K,List<V>> m_map = Maps.newHashMap();
	
	public static <K,V> Grouped<K,V> empty() {
		return new Grouped<>();
	}
	
	public List<V> get(final K key) {
		return m_map.getOrDefault(key, Collections.unmodifiableList(Collections.emptyList()));
	}
	
	public Grouped<K,V> add(K key, V value) {
		Preconditions.checkArgument(key != null, "key cannot be null");
		Preconditions.checkArgument(value != null, "value cannot be null");
		
		List<V> group = m_map.get(key);
		if ( group == null ) {
			m_map.put(key, group = Lists.newArrayList());
		}
		group.add(value);
		
		return this;
	}
	
	public <A> Stream<Tuple2<K,A>> fold(A init, BiFunction<A,V,A> folder) {
		return new FoldedStream<>(m_map, init, folder);
	}
	
	public Stream<Tuple2<K,V>> fold(BiFunction<V,V,V> reducer) {
		return new ReducedStream<>(m_map, reducer);
	}
	
	@Override
	public String toString() {
		return m_map.toString();
	}
	
	private static class FoldedStream<K,V,A> implements Stream<Tuple2<K,A>> {
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
	
	private static class ReducedStream<K,V> implements Stream<Tuple2<K,V>> {
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
