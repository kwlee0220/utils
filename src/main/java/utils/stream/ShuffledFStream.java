package utils.stream;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import utils.func.FOption;
import utils.stream.FStreams.AbstractFStream;


/**
 * 입력 원소를 무작위 순서로 내보내는 스트림.
 * <p>
 * 첫 {@link #next()} 때 입력을 끝까지 읽어 목록으로 만든 뒤 한 번 섞고, 그 순서대로 내보낸다. {@code null} 원소도
 * 그대로 내보낸다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
class ShuffledFStream<T> extends AbstractFStream<T> {
	private final FStream<T> m_src;
	private final Random m_rand;
	private List<T> m_list = null;
	private int m_index = 0;
	
	ShuffledFStream(FStream<T> src) {
		m_src = src;
		m_rand = new Random(System.currentTimeMillis());
	}

	@Override
	protected void initialize() {
		m_list = m_src.toList();
		Collections.shuffle(m_list, m_rand);
	}

	@Override
	protected void closeInGuard() throws Exception {
		m_list = null;
		m_src.close();
	}

	@Override
	protected FOption<T> nextInGuard() {
		if ( m_index >= m_list.size() ) {
			return FOption.empty();
		}
		return FOption.of(m_list.get(m_index++));
	}
}
