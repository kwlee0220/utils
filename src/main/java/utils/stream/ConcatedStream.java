package utils.stream;

import javax.annotation.Nullable;

import io.vavr.control.Try;
import utils.func.FOption;

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
	public FOption<T> next() {
		if ( m_current == null ) {
			return FOption.empty();
		}
		
		FOption<T> next;
		while ( true ) {
			if ( (next = m_current.next()).isPresent() ) {
				return next;
			}
			m_current.closeQuietly();
			
			FOption<? extends FStream<? extends T>> nextStream = m_fact.next();
			if ( nextStream.isAbsent() ) {
				m_current = null;
				return FOption.empty();
			}
			
			m_current = FStream.narrow(nextStream.get());
		}
	}
}
