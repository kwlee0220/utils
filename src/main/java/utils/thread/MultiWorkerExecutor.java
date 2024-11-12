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
import utils.stream.FStream;
import utils.stream.FStreams.AbstractFStream;

/**
 * {@code MultiWorkerExecutor}는 복수 개의 쓰레드로 운영되는 job executor를 구현한다.
 * <p>
 * 수행할 job은 {@link Callable}로 구현되어야 하고, {@link #submit} 메소드를 통해
 * 제공된다.
 * <p>
 * Submit된 작업의 수행 결과는 {@code MultiWorkerExecutor}는 {@link FStream} 인터페이스를
 * 따르기 때문에 그 수행이 종료되면 {@link FStream#next}를 통해 얻을 수 있다.
 * Submit된 작업을 수행 시간이 서로 다를 수 있기 때문에 {@link FStream#next}를 통해 얻는
 * 작업 수행 결과는 MultiWorkerExecutor에 submit된 순서와 다를 수 있다.
 * {@code #next()} 호출 당시 수행 중인 작업이 없거나, 모두 수행 중인 경우에는
 * submit된 작업이 종료될 때까지 대기하게 된다.
 * 만일 {@link #endOfSubmit()}가 호출된 경우에는 더 이상의 추가의 작업 submission이 없다는
 * 것을 의미하기 때문에 {@link #next}의 호출 결과는 {@link  FOption#empty()}가 반환된다.
 * <p>
 * MultiWorkerExecutor의 {@link #close}가 호출된 경우에는 추가의 submit은 불가하며
 * 호출된 경우에는 {@link IllegalStateException} 예외가 발생된다.
 * <p>
 * MultiWorkerExecutor의 수행 가능한 쓰레드 수의 제한이 필요한 경우에는
 * MultiWorkerExecutor를  생성할 때 인자로 제공되는 {@link Executor}의 쓰레드 수를 설정하여
 * 제공하는 방식을 사용한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MultiWorkerExecutor<T> extends AbstractFStream<Try<T>> {
	private final CompletionService<T> m_completeService;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private int m_remains = 0;
	@GuardedBy("m_guard") private boolean m_eos = false;
	@GuardedBy("m_guard") private List<Thread> m_waiters = Lists.newArrayList();
	
	public MultiWorkerExecutor(Executor executor) {
		m_completeService = new ExecutorCompletionService<>(executor);
	}

	@Override
	protected void closeInGuard() throws Exception { }

	@Override
	public FOption<Try<T>> nextInGuard() {
		if ( isClosed() ) {
			return FOption.empty();
		}
		
		try {
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
