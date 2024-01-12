package utils.stream;

import utils.func.FOption;
import utils.func.KeyValue;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class KVFStreams {
	private KVFStreams() {
		throw new AssertionError("Should not be called: " + KVFStreams.class);
	}

	static class FStreamAdaptor<K,V> implements KVFStream<K,V> {
		private final FStream<KeyValue<K,V>> m_base;
		
		FStreamAdaptor(FStream<KeyValue<K,V>> base) {
			m_base = base;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public FOption<KeyValue<K, V>> next() {
			return m_base.next();
		}	
	}
}
