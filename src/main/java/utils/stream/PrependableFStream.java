package utils.stream;

import java.util.List;
import java.util.Objects;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PrependableFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private final List<Option<T>> m_prefix = Lists.newArrayList();
	private boolean m_closed = false;
	
	PrependableFStream(FStream<T> src) {
		Objects.requireNonNull(src);
		
		m_src = src;
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}

	public Option<T> peekNext() {
		if ( m_prefix.isEmpty()) {
			return m_src.next()
						.peek(v -> m_prefix.add(0, Option.some(v)));
		}
		else {
			return m_prefix.get(0);
		}
	}
	
	public boolean hasNext() {
		return peekNext().isDefined();
	}

	@Override
	public Option<T> next() {
		if ( m_prefix.isEmpty() ) {
			return m_src.next();
		}
		else {
			return m_prefix.remove(0);
		}
	}

	public void prepend(T value) {
		Preconditions.checkState(!m_closed);
		
		m_prefix.add(0, Option.some(value));
	}
}
