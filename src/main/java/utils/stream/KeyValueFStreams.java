package utils.stream;

import utils.KeyValue;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueFStreams {
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
