package utils.stream;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class BufferedStream<T> implements FStream<List<T>> {
	private final FStream<T> m_src;
	private final int m_count;
	private final int m_skip;
	private final List<T> m_buffer;
	
	private boolean m_endOfSource = false;
	
	BufferedStream(FStream<T> src, int count, int skip) {
		Objects.requireNonNull(src);
		Preconditions.checkArgument(count > 0);
		
		m_src = src;
		m_count = count;
		m_skip = skip;
		m_buffer = Lists.newArrayListWithExpectedSize(count);
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}
	
	public int getCount() {
		return m_count;
	}

	@Override
	public List<T> next() {
		if ( m_buffer.size() > 0 ) {
			drain();
		}
		fill();
		
		if ( m_buffer.size() > 0 ) {
			return Collections.unmodifiableList(m_buffer);
		}
		else {
			return null;
		}
	}
	
	private void drain() {
		for ( int i =0; i < m_skip; ++i ) {
			if ( m_buffer.size() > 0 ) {
				m_buffer.remove(0);
			}
			else if ( m_src.next() == null ) {
				break;
			}
		}
	}
	
	private void fill() {
		while ( !m_endOfSource && m_buffer.size() < m_count ) {
			T next = m_src.next();
			if ( next != null ) {
				m_buffer.add(next);
			}
			else {
				m_endOfSource = true;
			}
		}
	}
}
