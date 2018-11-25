package utils.stream;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import io.vavr.CheckedRunnable;
import utils.Throwables;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class LongFStreamImpl implements LongFStream {
	private final String m_name;
	private final Supplier<Long> m_supplier;
	private final CheckedRunnable m_closer;
	private boolean m_closed = false;
	
	LongFStreamImpl(String name, Supplier<Long> nextSupplier, CheckedRunnable closer) {
		m_name = name;
		m_supplier = nextSupplier;
		m_closer = closer;
	}
	
	public LongFStreamImpl(String name, Supplier<Long> nextSupplier) {
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
	public Long next() {
		Preconditions.checkState(!m_closed, "LongFStream is closed already");
		
		return m_supplier.get();
	}
	
	@Override
	public String toString() {
		return String.format("%s%s", m_name, m_closed ? "(closed)" : "");
	}
	
	static class TakenLongStream implements LongFStream {
		private final LongFStream m_src;
		private long m_remains;
		
		TakenLongStream(LongFStream src, long count) {
			Preconditions.checkArgument(count >= 0, "count < 0");
			
			m_src = src;
			m_remains = count;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public Long next() {
			if ( m_remains <= 0 ) {
				return null;
			}
			else {
				--m_remains;
				return m_src.next();
			}
		}
	}
	
	static class LongArrayStream implements LongFStream {
		private final Iterator<Long> m_iter;
		
		LongArrayStream(Iterator<Long> iter) {
			Objects.requireNonNull(iter);
			
			m_iter = iter;
		}

		@Override
		public void close() throws Exception { }

		@Override
		public Long next() {
			return m_iter.hasNext() ? m_iter.next() : null;
		}
	}
}
