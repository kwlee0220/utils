package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Guards;

/**
 * <code>ThreadedAsyncExecution</code>는 비동기 연산 구현을 지원하는 추상 클래스이다.
 * <p>
 * 일반적으로 동기적으로 수행되는 작업을 비동기 연산으로 구현할 때 유용하게 사용될 수 있는
 * 장점이 있으나, {@link AbstractAsyncExecution}을 상속하는 방법에 비해 구현할 수 있는
 * 비동기 연산에 제약을 받는다.
 * <p>
 * ThreadedAsyncExecution을 상속받는 클래스는 반드시 다음의 두가지 메소드를 재정의하여야 한다.
 * <dl>
 * <dt>{@link #runTask()}:
 * <dd>
 * 비동기적으로 수행될 작업을 구현할 메소드로 대상 작업을 동기적으로 수행되는 것을 가정한다.
 * 즉 본 메소드가 호출되면 지정된 작업이 시작되고 메소드의 반환은 일반적으로 해당 작업을
 * 모두 완료될 때까지 대기 후 작업의 결과 값을 반환 값으로 반환되어야 한다.
 * ThreadedAsyncExecution은 runTask()이 정상적으로 호출되어 반환되는 경우
 * 이를 지정된 작업이 정상적으로 수행 완료된 것으로 간주하기 때문에, 만일 작업 수행 중
 * 타 쓰레드의 cancelTask() 메소드 호출 또는 기타 다른 이유로 인해
 * <b>작업이 중지되는 경우는 반드시 CancellationException 또는 InterruptedException 예외를
 * 발생시켜야 한다</b>.
 * 만일 그렇지 않은 경우는 작업 수행이 정상적으로 종료된 것으로 간주된다.</dd>
 * <dt>{@link #cancelTask()}:
 * <dd>
 * runTask() 호출로 수행 중인 작업을 중지시키기 위해 필요한 작업을 구현한다.
 * ThreadedAsyncExecution을 상속하여 구현된 비동기 연산의 수행 중 {@link AsyncExecution#cancel()}이
 * 호출되는 경우 본 메소드가 호출된다. 본 메소드는 일반적으로 수행 중인 작업을 중지시키기 위한
 * 작업을 수행한다. 
 * 만일 본 메소드 호출 당시 비동기 작업이 수행 중이지 않은 경우는 호출이 무시된다.
 * 또한 메소드 호출 중 발생되는 모든 예외는 모두 내부적으로 처리되어야 한다. cancelTask()를
 * 통한 작업 중지는 *best-effort* 의미를 갖기 때문에 메소드 호출로 반드시 대상 비동기 작업이
 * 중지되지 않아도 된다.
 * </dl>
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ThreadedAsyncExecution<T> extends AbstractAsyncExecution<T> {
	private static final Logger s_logger = LoggerFactory.getLogger(ThreadedAsyncExecution.class);

	private volatile CompletableFuture<Void> m_worker = null;

	protected abstract T runTask() throws Throwable;
	protected void cancelTask() throws Throwable {
		getLogger().debug("interrupt worker thread due to no canceler");
		
		if ( m_worker != null ) {
			m_worker.cancel(true);
		}
	}
	
	protected ThreadedAsyncExecution() {
		setLogger(s_logger);
	}

	@Override
	public void start() throws IllegalStateException {
		m_worker = CompletableFuture.runAsync(new ThreadedTask());
	}

	@Override
	public boolean cancel() {
		if ( !super.cancel() ) {
			return false;
		}
		
		try {
			cancelTask();
			
			return true;
		}
		catch ( Throwable e ) {
			notifyFailed(e);
			
			return false;
		}
	}
	
	class ThreadedTask implements Runnable {
		public void run() {
			try {
				if ( !notifyStarted() ) {
					return;
				}
			}
			catch ( Exception e ) {
				notifyFailed(e);
				return;
			}

			try {
				T result = runTask();

				ImplState state = Guards.get(m_aopLock, () -> m_aopState);
				switch ( state ) {
					case RUNNING:
						notifyCompleted(result);
						break;
					case CANCELLING:
						notifyCancelled();
						break;
					case FAILED:
						break;
					default:
						throw new AssertionError();
				}
			}
			catch ( CancellationException | InterruptedException e ) {
				// runTask() 수행 중 중단됨을 알려온 경우
				notifyCancelled();
			}
			catch ( Throwable e ) {
				notifyFailed(e);
			}
		}
	}
}
