package utils.stream;

import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PeekableFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private T m_peeked = null;
	
	PeekableFStream(FStream<T> src) {
		Objects.requireNonNull(src);
		
		m_src = src;
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}

	public T peekNext() {
		if ( m_peeked == null ) {
			m_peeked = m_src.next();
		}
		
		return m_peeked;
	}
	
	public boolean hasNext() {
		return peekNext() != null;
	}

	@Override
	public T next() {
		if ( m_peeked == null ) {
			return m_src.next();
		}
		else {
			T ret = m_peeked;
			m_peeked = null;
			return ret;
		}
	}

}
