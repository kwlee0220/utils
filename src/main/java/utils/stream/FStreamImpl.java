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
public class FStreamImpl<T> implements FStream<T> {
	private final String m_name;
	private final Supplier<Option<? extends T>> m_supplier;
	private final CheckedRunnable m_closer;
	private boolean m_closed = false;
	
	public FStreamImpl(String name, Supplier<Option<? extends T>> nextSupplier,
						CheckedRunnable closer) {
		m_name = name;
		m_supplier = nextSupplier;
		m_closer = closer;
	}
	
	public FStreamImpl(String name, Supplier<Option<? extends T>> nextSupplier) {
		this(name, nextSupplier, null);
	}

	@Override
	public void close() throws Exception {
		if ( !m_closed && m_closer != null ) {
			try {
				m_closer.run();
			}
			catch ( Throwable e ) {
				Throwables.throwIfInstanceOf(e, Exception.class);
				Throwables.throwAsRuntimeException(e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Option<T> next() {
		Preconditions.checkState(!m_closed, "FStream is closed already");
		
		return (Option<T>)m_supplier.get();
	}
	
	@Override
	public String toString() {
		return String.format("%s%s", m_name, m_closed ? "(closed)" : "");
	}
}
