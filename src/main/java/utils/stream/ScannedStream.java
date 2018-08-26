package utils.stream;

import java.util.function.BinaryOperator;

import com.google.common.base.Preconditions;

import io.vavr.control.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ScannedStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private final BinaryOperator<T> m_combine;
	private Option<T> m_current;
	
	ScannedStream(FStream<T> src, BinaryOperator<T> combine) {
		Objects.requireNonNull(src);
		Objects.requireNonNull(combine);
		
		m_src = src;
		m_combine = combine;
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}

	@Override
	public Option<T> next() {
		if ( m_current == null ) {
			return m_current = m_src.next();
		}
		else {
			return m_current = m_src.next()
									.map(t -> m_combine.apply(m_current.get(), t));
		}
	}
}
