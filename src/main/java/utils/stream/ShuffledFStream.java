package utils.stream;

import java.util.List;
import java.util.Random;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ShuffledFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private final Random m_rand;
	private List<T> m_list = null;
	
	ShuffledFStream(FStream<T> src) {
		m_src = src;
		m_rand = new Random(System.currentTimeMillis());
	}

	@Override
	public void close() throws Exception {
		m_list.clear();
		m_src.close();
	}

	@Override
	public FOption<T> next() {
		if ( m_list == null ) {
			m_list = m_src.toList();
		}
		
		while ( true ) {
			if ( m_list.isEmpty() ) {
				return FOption.empty();
			}
			
			T selected = selectOne();
			if ( selected != null ) {
				return FOption.of(selected);
			}
			
			m_list = FStream.from(m_list)
							.filter(v -> v != null)
							.toList();
		}
	}

	private T selectOne() {
		for ( int i =0; i < 5; ++i ) {
			int idx = m_rand.nextInt(m_list.size());
			T selected = m_list.get(idx);
			if ( selected != null ) {
				m_list.set(idx, null);
				
				return selected;
			}
		}
		
		return null;
	}
}
