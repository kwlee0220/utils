package utils.stream;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Preconditions;

import io.vavr.control.Option;
import io.vavr.control.Try;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class FStreamIterator<T> implements Iterator<T>, AutoCloseable {
	private FStream<T> m_strm;
	private Option<T> m_next;
	
	FStreamIterator(FStream<T> strm) {
		m_strm = strm;
		m_next = m_strm.next();
	}

	@Override
	public void close() throws Exception {
		if ( m_strm != null ) {
			FStream<T> strm = m_strm;
			m_strm = null;
			m_next = Option.none();
			
			strm.close();
		}
	}
	
	@Override
	public boolean hasNext() {
		return m_next.isDefined();
	}

	@Override
	public T next() {
		Preconditions.checkState(m_strm != null, "FStreamIterator has been closed already");
		
		Option<T> result = m_next;
		m_next = m_strm.next()
						.onEmpty(() -> Try.run(this::close));
		
		return result.getOrElseThrow(NoSuchElementException::new);
	}
}