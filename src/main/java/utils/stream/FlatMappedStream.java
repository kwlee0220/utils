package utils.stream;

import java.util.Objects;
import java.util.function.Function;

import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class FlatMappedStream<T,S> implements FStream<S> {
	private final FStream<T> m_src;
	private final Function<? super T,? extends FStream<? extends S>> m_mapper;
	
	private FStream<? extends S> m_mapped = FStream.empty();
	
	FlatMappedStream(FStream<T> src, Function<? super T,? extends FStream<? extends S>> mapper) {
		Objects.requireNonNull(src);
		Objects.requireNonNull(mapper);
		
		m_src = src;
		m_mapper = mapper;
	}

	@Override
	public void close() throws Exception {
		m_src.close();
	}

	@Override
	public FOption<S> next() {
		if ( m_mapped == null ) {
			return FOption.empty();
		}
		
		FOption<? extends S> next;
		while ( true ) {
			if ( (next = m_mapped.next()).isPresent() ) {
				return next.map(n -> (S)n);
			}
			m_mapped.closeQuietly();
			
			FOption<T> src = m_src.next();
			if ( src.isAbsent() ) {
				m_mapped = null;
				return FOption.empty();
			}
			
			m_mapped = m_mapper.apply(src.get());
		}
	}
}
