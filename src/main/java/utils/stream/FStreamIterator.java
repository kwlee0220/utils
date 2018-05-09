package utils.stream;

import java.util.Iterator;
import java.util.NoSuchElementException;

import utils.func.FOptional;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class FStreamIterator<T> implements Iterator<T> {
	private final FStream<T> m_fstrm;
	private FOptional<T> m_next;
	
	FStreamIterator(FStream<T> rset) {
		m_fstrm = rset;
		m_next = m_fstrm.next();
	}
	
	@Override
	public boolean hasNext() {
		return m_next.isPresent();
	}

	@Override
	public T next() {
		if ( m_next == null ) {
			throw new NoSuchElementException();
		}
		
		return m_next.flatMap(v -> {
						FOptional<T> result = m_next;
						m_next = m_fstrm.next();
						return result;
					})
					.getOrNull();
	}
}