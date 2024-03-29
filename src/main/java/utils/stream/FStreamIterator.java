package utils.stream;

import java.util.Iterator;
import java.util.NoSuchElementException;

import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class FStreamIterator<T> implements Iterator<T>, AutoCloseable {
	private final FStream<T> m_strm;
	private FOption<T> m_next;
	private boolean m_closed = false;
	
	FStreamIterator(FStream<T> strm) {
		m_strm = strm;
		m_next = m_strm.next();
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
		return m_next.isPresent();
	}

	@Override
	public T next() {
		if ( m_next.isAbsent() ) {
			throw new NoSuchElementException();
		}
		
		T next = m_next.get();
		m_next = m_strm.next().ifAbsent(m_strm::closeQuietly);
		
		return next;
	}
}