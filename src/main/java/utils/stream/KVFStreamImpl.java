package utils.stream;

import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import io.vavr.CheckedRunnable;
import io.vavr.control.Option;
import utils.Throwables;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class KVFStreamImpl<K,V> implements KVFStream<K,V> {
	private final Supplier<Option<KeyValue<K,V>>> m_supplier;
	private final CheckedRunnable m_closer;
	
	KVFStreamImpl(Supplier<Option<KeyValue<K,V>>> supplier, CheckedRunnable closer) {
		Preconditions.checkNotNull(supplier);
		
		m_supplier = supplier;
		m_closer = closer;
	}
	
	KVFStreamImpl(Supplier<Option<KeyValue<K,V>>> supplier) {
		this(supplier, null);
	}
	
	public void close() throws Exception {
		if ( m_closer != null) {
			try {
				m_closer.run();
			}
			catch ( Throwable e ) {
				Throwables.throwIfInstanceOf(e, Exception.class);
				Throwables.throwAsRuntimeException(e);
			}
		}
	}

	@Override
	public Option<KeyValue<K,V>> next() {
		return m_supplier.get();
	}
}
