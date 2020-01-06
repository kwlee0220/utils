package utils.async;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;

import com.google.common.collect.Lists;

import utils.Suppliable;
import utils.Throwables;
import utils.func.FOption;
import utils.func.Try;
import utils.stream.FStreams.AbstractFStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CompletionMonitor<T> extends AbstractFStream<Try<T>> implements Suppliable<Callable<T>> {
	private final CompletionService<T> m_completeService;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private int m_remains = 0;
	@GuardedBy("m_guard") private boolean m_eos = false;
	@GuardedBy("m_guard") private List<Thread> m_waiters = Lists.newArrayList();
	
	CompletionMonitor(Executor executor) {
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

	@Override
	public boolean isEndOfSupply() {
		return m_guard.get(() -> m_eos);
	}

	@Override
	public boolean supply(Callable<T> task) {
		return m_guard.get(() -> {
			if ( isClosed() || m_eos ) {
				return false;
			}
			else {
				m_completeService.submit(task);
				++m_remains;
				return true;
			}
		});
	}

	@Override
	public boolean supply(Callable<T> task, long timeout, TimeUnit tu)
		throws ThreadInterruptedException {
		return supply(task);
	}

	@Override
	public void endOfSupply() {
		m_guard.run(() -> m_eos = true);
	}

	@Override
	public void endOfSupply(Throwable error) {
		endOfSupply();
	}
}
