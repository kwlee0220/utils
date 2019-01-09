package utils.stream;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import utils.Guard;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class GroupByStream<K,V> implements FStream<KeyedFStream<K,V>> {
	private final FStream<V> m_src;
	private final Function<? super V,? extends K> m_grouper;
	private final SuppliableFStream<KeyedFStream<K,V>> m_output;
	
	private final Guard m_guard = Guard.create();
	private final Map<K,Group> m_groups;
	
	GroupByStream(FStream<V> src, Function<? super V,? extends K> grouper) {
		m_src = src;
		m_grouper = grouper;
		m_output = new SuppliableFStream<>(128);
		m_groups = new HashMap<>();
	}

	@Override
	public void close() throws Exception {
		m_src.closeQuietly();
		m_output.closeQuietly();
	}

	@Override
	public FOption<KeyedFStream<K, V>> next() {
		FOption<KeyedFStream<K, V>> nextGrp = m_output.poll();
		if ( !nextGrp.isAbsent() ) {
			return nextGrp;
		}
		
		m_guard.lock();
		try {
			FOption<V> next;
			while ( (next = m_src.next()).isPresent() ) {
				V value = next.get();
				K key = m_grouper.apply(value);
				
				supplyToGroup(key, value);
				if ( (nextGrp = m_output.poll()).isPresent() ) {
					return nextGrp;
				}
			}
			
			return FOption.empty();
		}
		finally {
			m_guard.unlock();
		}
	}
	
	private FOption<V> fillUntilInGuard(K stopKey) {
		FOption<V> onext;
		while ( (onext = m_src.next()).isPresent() ) {
			V value = onext.get();
			K key = m_grouper.apply(value);
			if ( stopKey.equals(key) ) {
				return FOption.of(value);
			}
			
			supplyToGroup(key, value);
		}
		
		m_groups.forEach((k,v) -> v.endOfSupply());
		m_output.endOfSupply();
		
		return FOption.empty();
	}
	
	private void supplyToGroup(K key, V value) {
		Group group = m_groups.computeIfAbsent(key, k -> {
			Group g = new Group(key);
			m_groups.put(key, g);
			m_output.supply(g);
			
			return g;
		});

		group.supply(value);
	}

	private class Group extends SuppliableFStream<V> implements KeyedFStream<K,V> {
		private final K m_key;
		
		Group(K key) {
			super(128);
			
			m_key = key;
		}

		@Override
		public K getKey() {
			return m_key;
		}

		@Override
		public FOption<V> next() {
			return poll().orElse(this::find);
		}
		
		@Override
		public String toString() {
			return String.format("KeyedFStream[key=%s,size=%d]", m_key, size());
		}
		
		private FOption<V> find() {
			if ( !isEndOfSupply() ) {
				return m_guard.get(() -> poll().orElse(() -> fillUntilInGuard(m_key)));
			}
			else {
				return FOption.empty();
			}
		}
	}
}
