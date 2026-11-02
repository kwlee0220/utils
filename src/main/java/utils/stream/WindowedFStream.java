package utils.stream;

import java.util.List;

import com.google.common.collect.Lists;

import utils.Preconditions;
import utils.func.FOption;
import utils.stream.FStreams.SingleSourceStream;


/**
 * 입력을 길이 {@code size}의 창(window)으로 묶어, 창마다 원소 리스트를 내보내는 스트림. 각 창은 직전 창의 시작
 * 위치에서 {@code step}만큼 이동한 위치에서 시작한다. {@link FStream#window(int, int)}의 구현이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
class WindowedFStream<T> extends SingleSourceStream<T,List<T>> {
	private final int m_size;
	private final int m_step;
	private final List<T> m_buffer;
	
	private boolean m_endOfSource = false;
	
	WindowedFStream(FStream<T> src, int size, int step) {
		super(src);
		Preconditions.checkArgument(size > 0, "size > 0, but: %s", size);
		Preconditions.checkArgument(step > 0, "step > 0, but: %s", step);
		
		m_size = size;
		m_step = step;
		m_buffer = Lists.newArrayListWithExpectedSize(size);
	}
	
	public int getSize() {
		return m_size;
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
		// 버퍼 앞쪽은 한 번에 지운다(원소마다 remove(0)을 하면 버퍼 크기에 비례해 비싸다).
		int removed = Math.min(m_step, m_buffer.size());
		m_buffer.subList(0, removed).clear();
		
		// 건너뛸 수가 버퍼보다 많으면 나머지는 입력에서 읽어 버린다.
		for ( int i = removed; i < m_step; ++i ) {
			if ( src.next().isAbsent() ) {
				m_endOfSource = true;
				break;
			}
		}
	}
	
	private void fill(FStream<T> src) {
		while ( !m_endOfSource && m_buffer.size() < m_size ) {
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
