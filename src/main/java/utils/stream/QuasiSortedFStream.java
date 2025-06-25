package utils.stream;

import static utils.Utilities.checkState;

import java.util.Comparator;
import java.util.PriorityQueue;

import org.checkerframework.checker.nullness.qual.Nullable;

import utils.Utilities;
import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class QuasiSortedFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private @Nullable PriorityQueue<T> m_queue;	// null인 경우는 본 stream이 close된 것을 의미한다.
	private final int m_queueLength;
	private boolean m_endOfUpstream;
	
	QuasiSortedFStream(FStream<T> src, int queueLength, Comparator<T> cmp) {
		Utilities.checkNotNullArgument(src, "src is null");
		Utilities.checkArgument(queueLength > 0, "queueLength > 0, but k=" + queueLength);
		
		m_src = src;
		m_queueLength = queueLength;
		m_queue = new PriorityQueue<>(queueLength+1, cmp);
		m_endOfUpstream = false;
	}

	@Override
	public void close() throws Exception {
		if ( m_queue != null ) {
			m_queue.clear();
			m_queue = null;
			m_src.close();
		}
	}

	@Override
	public FOption<T> next() {
		checkState(m_queue != null, "closed already");
		
		while ( !m_endOfUpstream && m_queue.size() <= m_queueLength ) {
			m_endOfUpstream = m_src.next()
									.ifPresent(v -> m_queue.add(v))
									.isAbsent();
		}
		
		if ( m_queue.size() > 0 ) {
			return FOption.of(m_queue.poll());
		}
		else {
			return FOption.empty();
		}
	}
}
