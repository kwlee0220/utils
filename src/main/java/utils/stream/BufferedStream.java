package utils.stream;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import io.vavr.control.Option;


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
		Preconditions.checkNotNull(src);
		Preconditions.checkArgument(count > 0);
		
		m_src = src;
		m_count = count;
		m_skip = skip;
		m_buffer = Lists.newArrayListWithExpectedSize(count);
		
		Option<T> next;
		for ( int i =0; i < count-1 && (next = src.next()).isDefined(); ++i ) {
			if ( i == 0 ) {
				m_buffer.add(next.get());
			}
			m_buffer.add(next.get());
		}
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}
	
	public int getCount() {
		return m_count;
	}

	@Override
	public Option<List<T>> next() {
		if ( m_buffer.size() > 0 ) {
			drain();
			fill();
			if ( m_buffer.size() > 0 ) {
				return Option.some(Collections.unmodifiableList(m_buffer));
			}
		}
		
		return Option.none();
	}
	
	private void drain() {
		for ( int i =0; i < m_skip; ++i ) {
			if ( m_buffer.size() > 0 ) {
				m_buffer.remove(0);
			}
			else {
				if ( m_src.next().isEmpty() ) {
					return;
				}
			}
		}
	}
	
	private void fill() {
		if ( !m_endOfSource ) {
			while ( m_buffer.size() < m_count ) {
				Option<T> onext = m_src.next()
										.peek(m_buffer::add);
				if ( onext.isEmpty() ) {
					m_endOfSource = true;
					return;
				}
			}
		}
	}
}
