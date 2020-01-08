package utils.stream;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import utils.Utilities;
import utils.func.FOption;
import utils.func.Try;
import utils.thread.ExecutorAware;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class PrefetchStream<T> implements FStream<T>, TimedFStream<T>, ExecutorAware {
	private final FStream<T> m_src;
	private final SuppliableFStream<T> m_buffer;
	private Executor m_executor = null;
	private AtomicBoolean m_prefetching = new AtomicBoolean(false);
	
	PrefetchStream(FStream<T> src, int prefetchSize) {
		m_src = src;
		m_buffer = new SuppliableFStream<>(prefetchSize);
	}
	
	PrefetchStream(FStream<T> src) {
		m_src = src;
		m_buffer = new SuppliableFStream<>();
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
		if ( m_buffer.emptySlots() >= m_buffer.capacity()/2
			&& m_prefetching.compareAndSet(false, true) ) {
			Utilities.runAsync(m_executor, m_prefetcher);
		}
		
		return m_buffer.next();
	}

	@Override
	public FOption<Try<T>> next(long timeout, TimeUnit tu) {
		if ( m_buffer.emptySlots() >= m_buffer.capacity()/2
			&& m_prefetching.compareAndSet(false, true) ) {
			Utilities.runAsync(m_executor, m_prefetcher);
		}

		return m_buffer.next(timeout, tu);
	}
	
	private final Runnable m_prefetcher = new Runnable() {
		@Override
		public void run() {
			while ( m_buffer.capacity()-m_buffer.size() > 0
					&& m_src.next().ifPresent(m_buffer::supply)
									.ifAbsent(m_buffer::closeQuietly)
									.isPresent() );
			m_prefetching.set(false);
		}
	};
}
