package utils.stream;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.func.FOption;
import utils.func.Try;

/**
 * 하위 스트림 공급 스트림 {@code fact}가 내놓는 하위 스트림들을 차례로 이어 붙인 스트림.
 * {@link FStream#concat(FStream)}과 {@link FStream#flatMap(java.util.function.Function)}의 구현이다.
 * <p>
 * 닫으면 읽던 하위 스트림과 {@code fact}를 닫고, 이후 {@link #next()}는 {@link IllegalStateException}을 던진다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
class ConcatenatedFStream<T> implements FStream<T> {
	private final FStream<FStream<T>> m_fact;
	private @Nullable FStream<T> m_current = FStream.empty();	// null이면 end-of-stream됨을 의미함.
	private boolean m_closed = false;
	
	ConcatenatedFStream(FStream<FStream<T>> fact) {
		Preconditions.checkNotNullArgument(fact, "fact is null");
		
		m_fact = fact;
	}

	@Override
	public void close() throws Exception {
		if ( m_closed ) {
			return;
		}
		m_closed = true;
		
		if ( m_current != null ) {
			m_current.closeQuietly();
			m_current = null;
		}
		
		m_fact.close();
	}

	@Override
	public FOption<T> next() {
		if ( m_closed ) {
			throw new IllegalStateException("already closed: " + this);
		}
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
