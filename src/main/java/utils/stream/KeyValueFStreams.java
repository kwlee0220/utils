package utils.stream;

import utils.KeyValue;
import utils.func.FOption;

/**
 * {@link KeyValueFStream} 연산자들의 구현 클래스를 모아 둔 클래스.
 * <p>
 * 포함된 구현 클래스들은 모두 패키지 내부용이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueFStreams {
	/**
	 * {@code KeyValueFStreams} 객체를 생성한다.
	 * <p>
	 * 이 클래스는 패키지 내부용 구현 클래스만 담고 있으므로 직접 생성할 필요가 없다.
	 */
	public KeyValueFStreams() { }

	static abstract class AbstractKeyValueFStream<K,V> implements KeyValueFStream<K,V> {
		private boolean m_closed = false;
		private boolean m_eos = false;
		private boolean m_initialized = false;
		
		abstract protected void closeInGuard() throws Exception;
		abstract protected FOption<KeyValue<K,V>> nextInGuard();
		protected void initialize() { }

		@Override
		public final void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;
				m_eos = true;
				closeInGuard();
			}
		}

		@Override
		public FOption<KeyValue<K,V>> next() {
			checkNotClosed();

			if ( m_eos ) {
				return FOption.empty();
			}
			if ( !m_initialized ) {
				initialize();
				m_initialized = true;
			}
			
			return nextInGuard().ifAbsent(() -> m_eos = true);
		}
		
		public boolean isClosed() {
			return m_closed;
		}
		
		public void checkNotClosed() {
			if ( m_closed ) {
				throw new IllegalStateException("already closed: " + this);
			}
		}
		
		protected void markEndOfStream() {
			m_eos = true;
		}
		
		public boolean isEndOfStream() {
			return m_eos;
		}
	}

	static class FStreamAdaptor<K,V> implements KeyValueFStream<K,V> {
		private final FStream<KeyValue<K,V>> m_base;
		
		FStreamAdaptor(FStream<KeyValue<K,V>> base) {
			m_base = base;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public FOption<KeyValue<K,V>> next() {
			return m_base.next();
		}	
	}
}
