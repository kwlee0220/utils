package utils.stream;

import java.util.Map;

import utils.KeyValue;
import utils.Tuple;
import utils.func.FOption;
import utils.stream.KeyValueFStreams.AbstractKeyValueFStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
class MatchKVFStream<K,TL,TR> extends AbstractKeyValueFStream<K,Tuple<TL,TR>> {
	private final KeyValueFStream<K,TL> m_stream;
	private final Map<? super K,? extends TR> m_lut;
	private boolean m_keepUnmatched = false;
	
	MatchKVFStream(KeyValueFStream<K,TL> stream, Map<? super K,? extends TR> lut, boolean keepUnmatched) {
		m_stream = stream;
		m_lut = lut;
		m_keepUnmatched = keepUnmatched;
	}
	
	MatchKVFStream(KeyValueFStream<K,TL> stream, Map<? super K,? extends TR> lut) {
		this(stream, lut, false);
	}

	// 중간 연산이므로 원본을 닫는다(패키지 닫기 규약 2번).
	@Override
	protected void closeInGuard() throws Exception {
		m_stream.close();
	}

	@Override
	protected FOption<KeyValue<K,Tuple<TL,TR>>> nextInGuard() {
		while ( true ) {
			FOption<KeyValue<K,TL>> onext = m_stream.next();
			if ( onext.isAbsent() ) {
				return FOption.empty();
			}
			
			KeyValue<K,TL> left = onext.get();
			if ( m_lut.containsKey(left.key()) || m_keepUnmatched ) {
				TR match = m_lut.get(left.key());
				return FOption.of(KeyValue.of(left.key(), Tuple.of(left.value(), match)));
			}
		}
	}
}
