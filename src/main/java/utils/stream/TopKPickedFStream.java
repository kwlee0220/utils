package utils.stream;

import java.util.Comparator;

import javax.annotation.concurrent.GuardedBy;

import com.google.common.collect.MinMaxPriorityQueue;

import utils.Preconditions;
import utils.func.FOption;

/**
 * 비교자 순서에서 앞쪽 {@code k}개 원소를 그 순서대로 내보내는 스트림.
 * <p>
 * 결과는 {@code src.sort(cmp).take(k)}와 같지만 크기 {@code k}의 우선순위 큐만 유지한다. 원본은 첫 {@link #next()}
 * 호출 때 끝까지 읽고 닫는다({@code k}가 0이면 읽지 않는다).
 * <p>
 * 2026-11-02 이전에는 생성자에서 원본을 끝까지 읽었고, {@code k}가 0이면 {@code hasNext()}에서 NPE가 났다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TopKPickedFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private final int m_k;
	private final Comparator<? super T> m_cmptor;
	@GuardedBy("this") private MinMaxPriorityQueue<Node> m_queue = null;	// 첫 next() 때 채운다
	
	TopKPickedFStream(FStream<T> src, int k, Comparator<? super T> cmp) {
		Preconditions.checkNotNullArgument(src, "src is null");
		Preconditions.checkArgument(k >= 0, "k >= 0, but k=" + k);
		Preconditions.checkNotNullArgument(cmp, "comparator is null");
		
		m_src = src;
		m_k = k;
		m_cmptor = cmp;
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}

	@Override
	public synchronized FOption<T> next() {
		if ( m_k == 0 ) {
			return FOption.empty();
		}
		if ( m_queue == null ) {
			// 넘치면 비교 순서에서 가장 뒤의 원소를 버리므로, 비교 순서상 앞쪽 k개가 남는다.
			MinMaxPriorityQueue<Node> queue = MinMaxPriorityQueue.maximumSize(m_k).create();
			m_src.forEach(v -> queue.add(new Node(v)));
			m_queue = queue;
		}
		
		Node node = m_queue.poll();
		return (node != null) ? FOption.of(node.m_value) : FOption.empty();
	}
	
	// 큐는 null을 담을 수 없으므로 원소를 감싼다(비교자가 허용하면 null 원소도 고를 수 있다).
	private final class Node implements Comparable<Node> {
		private final T m_value;
		
		Node(T value) {
			m_value = value;
		}

		@Override
		public int compareTo(Node other) {
			return m_cmptor.compare(m_value, other.m_value);
		}
	}
}
