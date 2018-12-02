package utils.stream;

import java.util.function.BinaryOperator;

import io.vavr.control.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ScannedStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private final BinaryOperator<T> m_combine;
	private Option<T> m_current = null;
	
	ScannedStream(FStream<T> src, BinaryOperator<T> combine) {
		m_src = src;
		m_combine = combine;
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}

	@Override
	public Option<T> next() {
		if ( m_current == null ) {	// 첫번째 call인 경우.
			return m_current = m_src.next();
		}
		else {
			Option<T> next = m_src.next();
			if ( next.isDefined() ) {
				return m_current = Option.some(m_combine.apply(m_current.get(), next.get()));
			}
			else {
				return Option.none();
			}
		}
	}
}
