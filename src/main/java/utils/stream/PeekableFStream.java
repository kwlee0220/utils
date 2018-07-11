package utils.stream;

import com.google.common.base.Preconditions;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PeekableFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private Option<T> m_peeked;
	
	PeekableFStream(FStream<T> src) {
		Preconditions.checkNotNull(src);
		
		m_src = src;
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}

	public Option<T> peekNext() {
		if ( m_peeked == null ) {
			m_peeked = m_src.next();
		}
		
		return m_peeked;
	}
	
	public boolean hasNext() {
		return peekNext().isDefined();
	}

	@Override
	public Option<T> next() {
		if ( m_peeked == null ) {
			return m_src.next();
		}
		else {
			Option<T> ret = m_peeked;
			m_peeked = null;
			return ret;
		}
	}

}
