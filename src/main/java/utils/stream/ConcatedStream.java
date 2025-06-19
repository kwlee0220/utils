package utils.stream;

import org.checkerframework.checker.nullness.qual.Nullable;

import utils.Utilities;
import utils.func.FOption;
import utils.func.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ConcatedStream<T> implements FStream<T> {
	private final FStream<FStream<T>> m_fact;
	@Nullable private FStream<T> m_current = FStream.empty();	// null이면 end-of-stream됨을 의미함.
	
	ConcatedStream(FStream<FStream<T>> fact) {
		Utilities.checkNotNullArgument(fact, "fact is null");
		
		m_fact = fact;
	}

	@Override
	public void close() throws Exception {
		if ( m_current != null ) {
			m_current.closeQuietly();
			m_current = null;
		}
		
		m_fact.close();
	}
	
	static class A { }
	static class B extends A { }

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
			Try.run(m_current::close);
			
			FOption<FStream<T>> nextStream = m_fact.next();
			if ( nextStream.isAbsent() ) {
				m_current = null;
				return FOption.empty();
			}
			
			m_current = nextStream.get();
		}
	}
}
