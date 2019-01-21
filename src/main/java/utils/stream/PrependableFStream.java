package utils.stream;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class PrependableFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private final List<T> m_prefix = Lists.newArrayList();
	private boolean m_closed = false;
	
	PrependableFStream(FStream<T> src) {
		Objects.requireNonNull(src);
		
		m_src = src;
	}

	@Override
	public void close() throws Exception {
		m_closed = true;
		m_prefix.clear();
		m_src.close();
	}

	public FOption<T> peekNext() {
		if ( m_closed ) {
			return FOption.empty();
		}
		
		if ( m_prefix.isEmpty()) {
			return m_src.next().ifPresent(m_prefix::add);
		}
		else {
			return FOption.of(m_prefix.get(m_prefix.size()-1));
		}
	}
	
	public boolean hasNext() {
		if ( m_prefix.size() > 0 ) {
			return true;
		}
		
		return m_src.next()
					.ifPresent(m_prefix::add)
					.isPresent();
	}

	@Override
	public FOption<T> next() {
		if ( m_prefix.isEmpty() ) {
			return m_src.next();
		}
		else {
			return FOption.of(m_prefix.remove(m_prefix.size()-1));
		}
	}

	public void prepend(T value) {
		Preconditions.checkState(!m_closed);
		
		m_prefix.add(value);
	}
	
	public void forEachWhile(Predicate<? super T> predicate, Consumer<? super T> effect) {
		FOption<T> onext;
		while ( (onext = next()).isPresent() ) {
			if ( onext.filter(predicate).ifPresent(effect).isAbsent() ) {
				prepend(onext.get());
				break;
			}
		}
	}
}
