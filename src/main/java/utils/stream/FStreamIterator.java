package utils.stream;

import java.util.Iterator;
import java.util.NoSuchElementException;

import io.vavr.control.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class FStreamIterator<T> implements Iterator<T> {
	private final FStream<T> m_fstrm;
	private Option<T> m_next;
	
	FStreamIterator(FStream<T> rset) {
		m_fstrm = rset;
		m_next = m_fstrm.next();
	}
	
	@Override
	public boolean hasNext() {
		return m_next.isDefined();
	}

	@Override
	public T next() {
		if ( m_next == null ) {
			throw new NoSuchElementException();
		}
		
		return m_next.flatMap(v -> {
						Option<T> result = m_next;
						m_next = m_fstrm.next();
						return result;
					})
					.getOrNull();
	}
}