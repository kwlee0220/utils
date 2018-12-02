package utils.stream;

import java.util.Objects;
import java.util.function.Function;

import io.vavr.control.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class FlatMappedStream<S,T> implements FStream<T> {
	private final FStream<S> m_src;
	private final Function<? super S,? extends FStream<? extends T>> m_mapper;
	
	private FStream<? extends T> m_mapped = FStream.empty();
	
	FlatMappedStream(FStream<S> src, Function<? super S,? extends FStream<? extends T>> mapper) {
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
	public Option<T> next() {
		if ( m_mapped == null ) {
			return Option.none();
		}
		
		Option<? extends T> next;
		while ( true ) {
			if ( (next = m_mapped.next()).isDefined() ) {
				return next.map(n -> (T)n);
			}
			m_mapped.closeQuietly();
			
			Option<S> src = m_src.next();
			if ( src.isEmpty() ) {
				m_mapped = null;
				return Option.none();
			}
			
			m_mapped = m_mapper.apply(src.get());
		}
	}
}
