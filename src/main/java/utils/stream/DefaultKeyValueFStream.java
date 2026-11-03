package utils.stream;

import utils.KeyValue;
import utils.func.FOption;


/**
 * {@link KeyValue} 원소 스트림을 {@link KeyValueFStream}으로 감싸는 기본 구현.
 * <p>
 * {@link #next()}와 {@link #close()}를 모두 입력 스트림에 그대로 위임한다.
 *
 * @param <K>	키 타입.
 * @param <V>	값 타입.
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
