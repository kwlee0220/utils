package utils.thread;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;

import com.google.common.collect.Lists;

import utils.Throwables;
import utils.async.Guard;
import utils.func.FOption;
import utils.func.Try;
import utils.stream.FStreams.AbstractFStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MultiWorkerExecutor<T> extends AbstractFStream<Try<T>> {
	private final CompletionService<T> m_completeService;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private int m_remains = 0;
	@GuardedBy("m_guard") private boolean m_eos = false;
	@GuardedBy("m_guard") private List<Thread> m_waiters = Lists.newArrayList();
	
	MultiWorkerExecutor(Executor executor) {
		m_completeService = new ExecutorCompletionService<>(executor);
	}

	@Override
	protected void closeInGuard() throws Exception {
		
	}

	@Override
	public FOption<Try<T>> next() {
		if ( isClosed() ) {
			return FOption.empty();
		}
		
		try {
			@SuppressWarnings("unused")
			T result = (T)m_completeService.take().get();
			m_guard.run(() -> --m_remains);
			
			return FOption.of(Try.success(result));
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			
			// 이미 폐쇄된 경우에는 end-of-stream으로 간주한다.
			if ( isClosed() ) {
				return FOption.empty();
			}
			
			m_guard.lock();
			try {
				if ( m_eos && m_remains == 0 ) {
					return FOption.empty();
				}
				
				Throwables.sneakyThrow(e);
				throw new AssertionError();
			}
			finally {
				m_guard.unlock();
			}
		}
		catch ( Exception e ) {
			m_guard.run(() -> --m_remains);
			
			Throwable cause = Throwables.unwrapThrowable(e);
			return FOption.of(Try.failure(cause));
		}
	}

	public FOption<Try<T>> next(long timeout, TimeUnit unit) {
		if ( isClosed() ) {
			return FOption.empty();
		}
		
		m_guard.lock();
		try {
			if ( m_eos && m_remains == 0 ) {
				return FOption.empty();
			}
		}
		finally {
			m_guard.unlock();
		}
		
		try {
			Future<T> fut = m_completeService.poll(timeout, unit);
			if ( fut == null ) {
				return m_guard.get(() -> {
					if ( m_eos && m_remains == 0 ) {
						return FOption.empty();
					}
					else {
						return FOption.of(Try.failure(new TimeoutException()));
					}
				});
			}
			
			m_guard.run(() -> --m_remains);
			return FOption.of(Try.success(fut.get()));
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			
			// 이미 폐쇄된 경우에는 end-of-stream으로 간주한다.
			if ( isClosed() ) {
				return FOption.empty();
			}
			
			m_guard.lock();
			try {
				if ( m_eos && m_remains == 0 ) {
					return FOption.empty();
				}
				
				Throwables.sneakyThrow(e);
				throw new AssertionError();
			}
			finally {
				m_guard.unlock();
			}
		}
		catch ( Exception e ) {
			m_guard.run(() -> --m_remains);
			
			Throwable cause = Throwables.unwrapThrowable(e);
			return FOption.of(Try.failure(cause));
		}
	}

	public boolean isEndOfSupply() {
		return m_guard.get(() -> m_eos);
	}

	public Future<T> submit(Callable<T> task) throws IllegalStateException {
		return m_guard.getOrThrow(() -> {
			if ( isClosed() || m_eos ) {
				throw new IllegalStateException();
			}
			else {
				Future<T> fut = m_completeService.submit(task);
				++m_remains;
				return fut;
			}
		});
	}

	public void endOfSubmit() {
		m_guard.run(() -> m_eos = true);
	}
}
