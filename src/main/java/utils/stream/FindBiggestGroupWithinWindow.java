package utils.stream;

import java.util.List;

import utils.KeyValue;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class FindBiggestGroupWithinWindow<K,V> implements KVFStream<K,List<V>> {
	private final KVFStream<K, V> m_kvStrm;
	private final KeyedGroups<K, V> m_groups;
	private final int m_maxLength;
	private final int m_retainLength;
	private int m_length;
	private boolean m_eos = false;
	private K m_lastSelectedGroup = null;
	
	FindBiggestGroupWithinWindow(KVFStream<K, V> kvStrm, int maxLength, int retainLength) {
		m_kvStrm = kvStrm;
		m_groups = KeyedGroups.create();
		m_maxLength = maxLength;
		m_retainLength = retainLength;
		m_length = 0;
		m_lastSelectedGroup = null;
	}
	
	FindBiggestGroupWithinWindow(KVFStream<K, V> kvStrm, int maxLength) {
		this(kvStrm, maxLength, maxLength);
	}

	@Override
	public void close() throws Exception {
		m_kvStrm.close();
	}

	@Override
	public FOption<KeyValue<K,List<V>>> next() {
		if ( !m_eos ) {
			fill();
		}
		
		if ( m_lastSelectedGroup != null ) {
			List<V> values = m_groups.getOrEmptyList(m_lastSelectedGroup);
			if ( values.size() >= m_retainLength ) {
				values = m_groups.remove(m_lastSelectedGroup).get();
				m_length -= values.size();
				
				return FOption.of(KeyValue.of(m_lastSelectedGroup, values));
			}
//			else if ( values.size() > 0 ) {
//				System.out.printf("skip too small group: group size=%d, retain_size=%d%n", values.size(), m_retainLength);
//			}
//			else {
//				System.out.println("---------");
//			}
		}
		
		return m_groups.stream()
						.takeTopK(1, (g1,g2) -> Integer.compare(g2.value().size(), g1.value().size()))
						.findFirst()
						.ifPresent(kv -> {
							m_lastSelectedGroup = kv.key();
							m_groups.remove(kv.key());
							m_length -= kv.value().size();
						});
	}

	private void fill() {
		FOption<KeyValue<K,V>> okv = null;
		while ( m_length < m_maxLength && (okv = m_kvStrm.next()).isPresent() ) {
			KeyValue<K,V> kv = okv.get();
			
			m_groups.add(kv.key(), kv.value());
			++m_length;
		}
		
		m_eos = okv != null && okv.isAbsent();
	}
}
