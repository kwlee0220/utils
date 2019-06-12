package utils.thread;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.jcip.annotations.GuardedBy;
import utils.LoggerSettable;
import utils.exception.Throwables;



/**
 * 
 * @author Kang-Woo Lee
 */
public class Prefetcher<T> implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(Prefetcher.class);
	
	private enum PrefetchState { IDLE, REQUESTED, PREFETCHING, };
	
	private final Callable<T> m_fetcher;
	private volatile long m_timeout = -1;
    private volatile Executor m_executor = null;	// null 이면 그때그때 thread를 만들어서 수행한다.
    private volatile boolean m_reuseFlag = false;	// 사용한 pre-fetched 데이터의 재사용 여부.
    
    private volatile Logger m_logger = s_logger;
    
    private final ReentrantLock m_lock = new ReentrantLock();
    private final Condition m_cond = m_lock.newCondition();
    @GuardedBy("m_lock") private T m_prefetched;
    @GuardedBy("m_lock") private long m_fetchedMillis;
    @GuardedBy("m_lock") private Throwable m_fault;
    @GuardedBy("m_lock") private PrefetchState m_state = PrefetchState.IDLE;
	
    /**
     * Prefetcher 객체를 생성한다.
     * <p>
     * {@code reuse}가 false인 경우는 한번 prefetch된 값이 반환된 이 후에는 다음번 fetch 된 값이
     * 가용할 때까지 대기하게 되나, true인 경우는 prefetch된 값이 다음번 fetch된 값이 얻어질 때 까지
     * {@link #get()} 또는 {@link #get(long)}의 결과를 반환된다.
     * 
     * @param fetcher	가져오기 작업을 수행할 {@link Callable} 객체.
     * @param validityTimeout	Prefetch된 객체의 유효기간. (단위: milli-seconds)
     * @param reuse		사용한 pre-fetched 데이터의 재사용 여부.
     * @param executor	사용할 쓰레드 풀 객체. {@code null}인 경우는 별도의 쓰레드 풀을 사용하지 않는 것으로 간주.
     */
	public Prefetcher(Callable<T> fetcher, long validityTimeout, boolean reuse,
						@Nullable Executor executor) {
		Objects.requireNonNull(fetcher, "fetcher is null");
		Preconditions.checkArgument(validityTimeout > 0,
									"validityTimeout was invalid: value=" + validityTimeout
									+ ",  class=" + getClass().getName());
		
		m_fetcher = fetcher;
		m_timeout = validityTimeout;
		m_executor = executor;
		m_reuseFlag = reuse;

		m_prefetched = (T)new Object();
		m_fetchedMillis = 0L;	// 중요!
	}
	
	public void setReuse(boolean flag) {
		m_reuseFlag = flag;
	}

	public Executor getExecutor() {
		return m_executor;
	}

	public void setExecutor(Executor executor) {
		m_executor = executor;
	}
	
	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		Objects.requireNonNull(logger);
		
		m_logger = logger;
	}
	
	public T get() throws InterruptedException, ExecutionException {
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_prefetched != null ) {
					// prefetch 된 결과가 있는 경우.
					
					long age = System.currentTimeMillis() - m_fetchedMillis;
					if ( age <= m_timeout ) {
						// prefetch된 오래되지 않은 경우.
						T result = m_prefetched;
						if ( !m_reuseFlag ) {
							m_prefetched = null;
							m_fault = null;
						}
						
						if ( m_state == PrefetchState.IDLE ) {
							// Prefetch된 결과를 사용했으니 새롭게 fetch를 실시하여
							// 다음 결과를 얻도록 한다.
							requestPrefetchInGuard();
						}
						
						return result;
					}
					else {
						m_prefetched = null;
						m_fault = null;
					}
				}
				
				// prefetch된 결과가 없거나, pre-fetch된 것이 너무 오래된 경우.
				//
				
				if ( m_state == PrefetchState.IDLE ) {
					requestPrefetchInGuard();
				}
				
				// fetch가 끝나거나 오류가 생겨서 실패할 때까지 대기한다.
				while ( m_prefetched == null && m_fault == null ) {
					m_cond.await();
				}

				// 다음 번 결과를 위해 prefetch 시킨다.
				requestPrefetchInGuard();
				
				if ( m_prefetched != null ) {
					T result = m_prefetched;
					if ( !m_reuseFlag ) {
						m_prefetched = null;
						m_fault = null;
					}
					
					return result;
				}
				else if ( m_fault != null ) {
					// fetch 연산이 예외 발생으로 실패한 경우.
					//
					throw new ExecutionException(m_fault);
				}
				
				throw new AssertionError("should not be here");
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public T get(long timeoutMillis) throws InterruptedException, TimeoutException, ExecutionException {
		Date deadline = Date.from(Instant.now().plusMillis(timeoutMillis)); 
		
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_prefetched != null ) {
					// prefetch 된 결과가 있는 경우.
					
					long age = System.currentTimeMillis() - m_fetchedMillis;
					if ( age <= m_timeout ) {
						// prefetch된 오래되지 않은 경우.
						T result = m_prefetched;
						if ( !m_reuseFlag ) {
							m_prefetched = null;
							m_fault = null;
						}
						
						if ( m_state == PrefetchState.IDLE ) {
							// Prefetch된 결과를 사용했으니 새롭게 fetch를 실시하여
							// 다음 결과를 얻도록 한다.
							requestPrefetchInGuard();
						}
						
						return result;
					}
					else {
						m_prefetched = null;
						m_fault = null;
					}
				}
				
				// prefetch된 결과가 없거나, pre-fetch된 것이 너무 오래된 경우.
				//
				
				if ( m_state == PrefetchState.IDLE ) {
					requestPrefetchInGuard();
				}
				
				// fetch가 끝나거나 오류가 생겨서 실패할 때까지 대기한다.
				while ( m_prefetched == null && m_fault == null ) {
					if ( !m_cond.awaitUntil(deadline) ) {
						// deadline이 지난 경우.
						throw new TimeoutException("timeout=" + timeoutMillis + "ms");
					}
				}

				// 다음 번 결과를 위해 prefetch 시킨다.
				requestPrefetchInGuard();
				
				if ( m_prefetched != null ) {
					T result = m_prefetched;
					if ( !m_reuseFlag ) {
						m_prefetched = null;
						m_fault = null;
					}
					
					return result;
				}
				else if ( m_fault != null ) {
					// fetch 연산이 예외 발생으로 실패한 경우.
					//
					throw new ExecutionException(m_fault);
				}
				
				throw new AssertionError("should not be here");
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	private void requestPrefetchInGuard() {
		m_state = PrefetchState.REQUESTED;
		if ( m_executor != null ) {
			CompletableFuture.runAsync(m_prefetchingWorker, m_executor);
		}
		else {
			CompletableFuture.runAsync(m_prefetchingWorker);
		}
	}
	
	private final Runnable m_prefetchingWorker = new Runnable() {
		public void run() {
			m_lock.lock();
			try {
				if ( m_state != PrefetchState.REQUESTED ) {
					return;
				}
				m_state = PrefetchState.PREFETCHING;
				m_cond.signalAll();
			}
			finally {
				m_lock.unlock();
			}
			
			T result = null;
			Throwable error = null;
			try {
				result = m_fetcher.call();
			}
			catch ( Throwable fault ) {
				if ( getLogger() != null ) {
					getLogger().debug("failed to capture an image, cause={}", fault);
				}
				error = Throwables.unwrapThrowable(fault);
			}
			finally {
				m_lock.lock();
				try {
					m_prefetched = result;
					m_fetchedMillis = System.currentTimeMillis();
					m_fault = error;
					m_state = PrefetchState.IDLE;

					m_cond.signalAll();
				}
				finally {
					m_lock.unlock();
				}
			}
		}
	};
}
