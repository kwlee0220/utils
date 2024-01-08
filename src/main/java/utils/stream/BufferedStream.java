package utils.stream;

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
	protected FOption<List<T>> getNext(FStream<T> src) {
		if ( m_buffer.size() > 0 ) {
			drain(src);
		}
		fill(src);
		
		if ( m_buffer.size() > 0 ) {
			return FOption.of(Lists.newArrayList(m_buffer));
		}
		else {
			return FOption.empty();
		}
	}
	
	private void drain(FStream<T> src) {
		for ( int i =0; i < m_skip; ++i ) {
			if ( m_buffer.size() > 0 ) {
				m_buffer.remove(0);
			}
			else if ( src.next().isAbsent() ) {
				break;
			}
		}
	}
	
	private void fill(FStream<T> src) {
		while ( !m_endOfSource && m_buffer.size() < m_count ) {
			FOption<T> next = src.next();
			if ( next.isPresent() ) {
				m_buffer.add(next.get());
			}
			else {
				m_endOfSource = true;
			}
		}
	}
}
