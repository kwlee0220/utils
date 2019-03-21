package utils.stream;

import java.util.Comparator;

import com.google.common.collect.MinMaxPriorityQueue;

import utils.Utilities;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class QuasiSortedFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private MinMaxPriorityQueue<T> m_queue;
	
	QuasiSortedFStream(FStream<T> src, int queueLength, Comparator<T> cmp) {
		Utilities.checkNotNullArgument(src, "src is null");
		Utilities.checkArgument(queueLength > 0, "queueLength > 0, but k=" + queueLength);
		
		m_src = src;
		m_queue = MinMaxPriorityQueue.orderedBy(cmp)
									.maximumSize(queueLength)
									.create();
		for ( int i =0; i < queueLength; ++i ) {
			if ( m_src.next().ifPresent(m_queue::add).isAbsent() ) {
				break;
			}
		}
	}

	@Override
	public void close() throws Exception {
		if ( m_queue != null ) {
			m_queue = null;
			m_src.close();
		}
	}

	@Override
	public FOption<T> next() {
		if ( m_queue != null ) {
			T v = m_queue.poll();
			if ( v != null ) {
				m_src.next().ifPresent(m_queue::add);
				return FOption.of(v);
			}
		}
		
		return FOption.empty();
	}
}
