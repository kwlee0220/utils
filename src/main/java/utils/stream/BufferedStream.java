package utils.stream;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

import utils.Utilities;
import utils.func.FOption;
import utils.stream.FStreams.SingleSourceStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class BufferedStream<T> extends SingleSourceStream<T,List<T>> {
	private final int m_count;
	private final int m_skip;
	private final List<T> m_buffer;
	
	private boolean m_endOfSource = false;
	
	BufferedStream(FStream<T> src, int count, int skip) {
		super(src);
		Utilities.checkArgument(count > 0, "count > 0, but: " + count);
		
		m_count = count;
		m_skip = skip;
		m_buffer = Lists.newArrayListWithExpectedSize(count);
	}
	
	public int getCount() {
		return m_count;
	}

	@Override
	public FOption<List<T>> next() {
		if ( m_buffer.size() > 0 ) {
			drain();
		}
		fill();
		
		if ( m_buffer.size() > 0 ) {
			return FOption.of(Collections.unmodifiableList(m_buffer));
		}
		else {
			return FOption.empty();
		}
	}
	
	private void drain() {
		for ( int i =0; i < m_skip; ++i ) {
			if ( m_buffer.size() > 0 ) {
				m_buffer.remove(0);
			}
			else if ( m_src.next().isAbsent() ) {
				break;
			}
		}
	}
	
	private void fill() {
		while ( !m_endOfSource && m_buffer.size() < m_count ) {
			FOption<T> next = m_src.next();
			if ( next.isPresent() ) {
				m_buffer.add(next.get());
			}
			else {
				m_endOfSource = true;
			}
		}
	}
}
