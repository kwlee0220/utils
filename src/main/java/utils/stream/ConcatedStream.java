package utils.stream;

import javax.annotation.Nullable;

import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ConcatedStream<T> implements FStream<T> {
	private final FStream<? extends FStream<? extends T>> m_fact;
	@Nullable private FStream<T> m_current = FStream.empty();	// null이면 end-of-stream됨을 의미함.
	
	ConcatedStream(FStream<? extends FStream<? extends T>> fact) {
		m_fact = fact;
	}

	@Override
	public void close() throws Exception {
		if ( m_current != null ) {
			Try.run(m_current::close);
			m_current = null;
		}
		
		m_fact.close();
	}

	@Override
	public Option<T> next() {
		if ( m_current == null ) {
			return Option.none();
		}
		
		Option<T> next;
		while ( true ) {
			if ( (next = m_current.next()).isDefined() ) {
				return next;
			}
			Try.run(m_current::close);
			
			Option<? extends FStream<? extends T>> nextStream = m_fact.next();
			if ( nextStream.isEmpty() ) {
				m_current = null;
				return Option.none();
			}
			
			m_current = (FStream<T>)nextStream.get();
		}
	}
}
