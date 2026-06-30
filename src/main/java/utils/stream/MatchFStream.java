package utils.stream;

import java.util.Map;
import java.util.function.Function;

import utils.KeyValue;
import utils.func.FOption;
import utils.stream.KeyValueFStreams.AbstractKeyValueFStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
class MatchFStream<K,V> extends AbstractKeyValueFStream<K,V> {
	private final FStream<K> m_stream;
	private final Function<K,V> m_lookup;
	private boolean m_keepUnmatched = false;
	
	MatchFStream(FStream<K> stream, Function<K,V> lookup, boolean keepUnmatched) {
		m_stream = stream;
		m_lookup = lookup;
		m_keepUnmatched = keepUnmatched;
	}
	
	MatchFStream(FStream<K> stream, Map<K,V> lut, boolean keepUnmatched) {
		this(stream, lut::get, keepUnmatched);
	}
	MatchFStream(FStream<K> stream, Map<K,V> lut) {
		this(stream, lut::get, false);
	}

	@Override
	protected void closeInGuard() throws Exception { }

	@Override
	protected FOption<KeyValue<K,V>> nextInGuard() {
		while ( true ) {
			FOption<K> onext = m_stream.next();
			if ( onext.isAbsent() ) {
				return FOption.empty();
			}
			
			K key = onext.get();
			V value = m_lookup.apply(key);
			if ( value != null || m_keepUnmatched ) {
				return FOption.of(KeyValue.of(key, value));
			}
		}
	}
}
