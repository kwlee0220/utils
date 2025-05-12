package utils.stream;

import utils.KeyValue;
import utils.func.FOption;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DefaultKeyValueFStream<K,V> implements KeyValueFStream<K,V> {
	private final FStream<KeyValue<K,V>> m_inputStream;

	DefaultKeyValueFStream(FStream<KeyValue<K, V>> stream) {
		m_inputStream = stream;
	}

	@Override
	public void close() throws Exception {
		m_inputStream.close();
	}

	@Override
	public FOption<KeyValue<K, V>> next() {
		return m_inputStream.next();
	}
}
