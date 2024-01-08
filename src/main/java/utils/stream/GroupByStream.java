package utils.stream;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import utils.async.Guard;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class GroupByStream<K,T> implements FStream<KeyedFStream<K,T>> {
	private final FStream<T> m_src;
	private final Function<? super T,? extends K> m_grouper;
	private final SuppliableFStream<KeyedFStream<K,T>> m_output;
	
	private final Guard m_guard = Guard.create();
	private final Map<K,Group> m_groups;
	
	GroupByStream(FStream<T> src, Function<? super T,? extends K> grouper) {
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
	public FOption<KeyedFStream<K, T>> next() {
		FOption<KeyedFStream<K, T>> nextGrp = m_output.poll();
		if ( !nextGrp.isAbsent() ) {
			return nextGrp;
		}
		
		m_guard.lock();
		try {
			FOption<T> next;
			while ( (next = m_src.next()).isPresent() ) {
				T value = next.get();
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
	
	private FOption<T> fillUntilInGuard(K stopKey) {
		FOption<T> onext;
		while ( (onext = m_src.next()).isPresent() ) {
			T value = onext.get();
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
	
	private void supplyToGroup(K key, T value) {
		Group group = m_groups.computeIfAbsent(key, k -> {
			Group g = new Group(key);
			m_groups.put(key, g);
			m_output.supply(g);
			
			return g;
		});

		group.supply(value);
	}

	private class Group extends SuppliableFStream<T> implements KeyedFStream<K,T> {
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
		public FOption<T> next() {
			return poll().orElse(this::find);
		}
		
		@Override
		public String toString() {
			return String.format("KeyedFStream[key=%s,size=%d]", m_key, size());
		}
		
		private FOption<T> find() {
			if ( !isEndOfSupply() ) {
				return m_guard.get(() -> poll().orElse(() -> fillUntilInGuard(m_key)));
			}
			else {
				return FOption.empty();
			}
		}
	}
}
