package utils.stream;

import java.util.Objects;
import java.util.function.Supplier;

import io.vavr.CheckedRunnable;
import utils.Throwables;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class KVFStreamImpl<K,V> implements KVFStream<K,V> {
	private final String m_name;
	private final Supplier<KeyValue<K,V>> m_supplier;
	private final CheckedRunnable m_closer;
	private boolean m_closed = false;
	
	KVFStreamImpl(String name, Supplier<KeyValue<K,V>> supplier, CheckedRunnable closer) {
		Objects.requireNonNull(supplier);
		
		m_name = name;
		m_supplier = supplier;
		m_closer = closer;
	}
	
	KVFStreamImpl(String name, Supplier<KeyValue<K,V>> supplier) {
		this(name, supplier, null);
	}
	
	public void close() throws Exception {
		if ( !m_closed && m_closer != null ) {
			try {
				m_closer.run();
			}
			catch ( Throwable e ) {
				Throwables.throwIfInstanceOf(e, Exception.class);
				throw Throwables.toRuntimeException(e);
			}
		}
	}

	@Override
	public KeyValue<K,V> next() {
		return m_supplier.get();
	}
	
	@Override
	public String toString() {
		return String.format("%s%s", m_name, m_closed ? "(closed)" : "");
	}
}
