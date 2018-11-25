package utils.stream;

import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import io.vavr.CheckedRunnable;
import utils.Throwables;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class IntFStreamImpl implements IntFStream {
	private final String m_name;
	private final Supplier<Integer> m_supplier;
	private final CheckedRunnable m_closer;
	private boolean m_closed = false;
	
	public IntFStreamImpl(String name, Supplier<Integer> nextSupplier, CheckedRunnable closer) {
		m_name = name;
		m_supplier = nextSupplier;
		m_closer = closer;
	}
	
	public IntFStreamImpl(String name, Supplier<Integer> nextSupplier) {
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
				throw Throwables.toRuntimeException(e);
			}
		}
	}

	@Override
	public Integer next() {
		Preconditions.checkState(!m_closed, "IntFStreamImpl is closed already");
		
		return m_supplier.get();
	}
	
	@Override
	public String toString() {
		return String.format("%s%s", m_name, m_closed ? "(closed)" : "");
	}
}
