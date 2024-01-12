package utils.stream;

import java.util.Comparator;

import com.google.common.collect.MinMaxPriorityQueue;

import utils.Utilities;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class TopKPickedFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private final Comparator<? super T> m_cmptor;
	private final MinMaxPriorityQueue<Node> m_queue;
	
	TopKPickedFStream(FStream<T> src, int k, Comparator<? super T> cmp) {
		Utilities.checkNotNullArgument(src, "src is null");
		Utilities.checkArgument(k >= 0, "k >= 0, but k=" + k);
		
		m_src = src;
		m_cmptor = cmp;
		
		if ( k > 0 ) {
			m_queue =  MinMaxPriorityQueue.maximumSize(k).create();
			src.forEach(v -> m_queue.add(new Node(v)));
		}
		else {
			m_queue = null;
		}
	}
	
	private class Node implements Comparable<Node> {
		private final T m_value;
		
		Node(T value) {
			m_value = value;
		}

		@Override
		public int compareTo(Node other) {
			return m_cmptor.compare(m_value, other.m_value);
		}
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}
	
	public boolean hasNext() {
		return !m_queue.isEmpty();
	}

	@Override
	public FOption<T> next() {
		if ( m_queue != null ) {
			Node node = m_queue.poll();
			if ( node != null ) {
				return FOption.of(node.m_value);
			}
			else {
				return FOption.empty();
			}
		}
		else {
			return FOption.empty();
		}
	}
}
