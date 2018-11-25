package utils.stream;

import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import io.vavr.CheckedRunnable;
import utils.Throwables;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DoubleFStreamImpl implements DoubleFStream {
	private final String m_name;
	private final Supplier<Double> m_supplier;
	private final CheckedRunnable m_closer;
	private boolean m_closed = false;
	
	public DoubleFStreamImpl(String name, Supplier<Double> nextSupplier, CheckedRunnable closer) {
		m_name = name;
		m_supplier = nextSupplier;
		m_closer = closer;
	}
	
	public DoubleFStreamImpl(String name, Supplier<Double> nextSupplier) {
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
	public Double next() {
		Preconditions.checkState(!m_closed, "DoubleFStream is closed already");
		
		return m_supplier.get();
	}
	
	@Override
	public String toString() {
		return String.format("%s%s", m_name, m_closed ? "(closed)" : "");
	}
	
	static class TakenStream implements DoubleFStream {
		private final DoubleFStream m_src;
		private long m_remains;
		
		TakenStream(DoubleFStream src, long count) {
			Preconditions.checkArgument(count >= 0, "count < 0");
			
			m_src = src;
			m_remains = count;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public Double next() {
			if ( m_remains <= 0 ) {
				return null;
			}
			else {
				--m_remains;
				return m_src.next();
			}
		}
	}
}
