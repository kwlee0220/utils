package utils.stream;

import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class FStreamIterator<T> implements Iterator<T>, AutoCloseable {
	private final FStream<T> m_strm;
	@Nullable private T m_next;	// 다음 요소가 없는 경우는 null
	private boolean m_closed = false;
	
	FStreamIterator(FStream<T> strm) {
		m_strm = strm;
		m_next = m_strm.next().getOrNull();
	}

	@Override
	public void close() throws Exception {
		if ( !m_closed ) {
			m_closed = true;
			m_strm.close();
			m_next = null;
		}
	}
	
	@Override
	public boolean hasNext() {
		return m_next != null;
	}

	@Override
	public T next() {
		if ( m_next == null ) {
			throw new NoSuchElementException();
		}
		
		T next = m_next;
		m_next = m_strm.next().ifAbsent(m_strm::closeQuietly).getOrNull();
		
		return next;
	}
}