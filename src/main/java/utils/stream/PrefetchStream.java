package utils.stream;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import utils.Utilities;
import utils.func.FOption;
import utils.func.Try;
import utils.thread.ExecutorAware;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class PrefetchStream<T> implements TimedFStream<T>, ExecutorAware {
	private final FStream<T> m_src;
	private final SuppliableFStream<Try<T>> m_buffer;
	private Executor m_executor = null;
	
	PrefetchStream(FStream<T> src, int prefetchSize) {
		m_src = src;
		m_buffer = new SuppliableFStream<>(prefetchSize);
		Utilities.runAsync(m_executor, m_prefetcher);
	}
	
	PrefetchStream(FStream<T> src) {
		this(src, Integer.MAX_VALUE);
	}
	
	@Override
	public void close() throws Exception {
		m_src.closeQuietly();
		m_buffer.closeQuietly();
	}
	
	public int prefetchSize() {
		return m_buffer.capacity();
	}
	
	public boolean available() {
		return m_buffer.size() > 0;
	}
	
	@Override
	public Executor getExecutor() {
		return m_executor;
	}
	
	@Override
	public void setExecutor(Executor executor) {
		m_executor = executor;
	}
	
	@Override
	public FOption<T> next() {
		return m_buffer.next().map(t -> t.get());
	}

	@Override
	public FOption<Try<T>> next(long timeout, TimeUnit tu) {
		FOption<Try<Try<T>>> oret = m_buffer.next(timeout, tu);
		return oret.map(t -> {
			if ( t.isFailure() ) {
				return Try.failure(t.getCause());
			}
			else {
				return t.get();
			}
		});
	}
	
	@Override
	public String toString() {
		return m_buffer.toString();
	}
	
	private final Runnable m_prefetcher = new Runnable() {
		@Override
		public void run() {
			while ( true ) {
				FOption<T> onext;
				try {
					onext = m_src.next();
					if ( onext.isPresent() ) {
						m_buffer.supply(Try.success(onext.get()));
					}
					else {
						break;
					}
				}
				catch ( Exception e ) {
					m_buffer.supply(Try.failure(e));
				}
			}
		}
	};
}
