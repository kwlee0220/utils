package utils.stream;

import java.util.function.BinaryOperator;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ScannedStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private final BinaryOperator<T> m_combine;
	private T m_current;
	
	ScannedStream(FStream<T> src, BinaryOperator<T> combine) {
		m_src = src;
		m_combine = combine;
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}

	@Override
	public T next() {
		if ( m_current == null ) {
			return m_current = m_src.next();
		}
		else {
			T next = m_src.next();
			if ( next != null ) {
				return m_current = m_combine.apply(m_current, next);
			}
			else {
				return null;
			}
		}
	}
}
