package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import utils.Throwables;
import utils.func.FOption;
import utils.func.Try;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractLoopExecution<T> extends AbstractAsyncExecution<T> implements CancellableWork {
	private boolean m_loopFinished = false;
	
	/**
	 * Loop을 수행하기 위해 초기화 작업을 수행한다.
	 * 
	 * @throws Exception 	초기화 과정에서 예외가 발생한 경우.
	 */
	protected abstract void initializeLoop() throws Exception;
	
	/**
	 * Loop의 한 번의 iteration 작업을 수행한다.
	 * 
	 * @param loopIndex		loop 인덱스. 0부터 시작함.
	 * @return		loop 수행으로 최종적으로 생성된 결과.
	 * 				반환 값이 {@code null}이거나 {@link FOption#empty()}인 경우는
	 * 				추가 iteration이 더 필요하다는 의미이고,
	 * 				그렇지 않은 경우는 더 이상의 iteration이 필요없어서 loop Execution이
	 * 				종료되어야 한다는 의미이다.
	 * @throws Exception	iteration 작업 중 예외가 발생한 경우.
	 */
	protected abstract FOption<T> iterate(long loopIndex) throws Exception;
	
	/**
	 * Loop 작업이 종료된 후 cleanup 작업을 수행한다.
	 * 
	 * @throws Exception	cleanup 작업 중 예외가 발생한 경우.
	 */
	protected abstract void finalizeLoop() throws Exception;
	
	@Override
	public final void start() {
		if ( !notifyStarting() ) {
			throw new IllegalStateException("cannot start because invalid state: state=" + getState());
		}
		
		Executor exector = getExecutor();
		if ( exector != null ) {
			exector.execute(this::loop);
		}
		else {
			Thread thread = new Thread(this::loop);
			thread.start();
		}
	}

	@Override
	public boolean cancelWork() {
		try {
			m_aopGuard.awaitUntil(() -> m_loopFinished);
			return true;
		}
		catch ( InterruptedException e ) {
			throw new ThreadInterruptedException("interrupted");
		}
	}
	
	private void loop() {
		try {
			initializeLoop();
		}
		catch ( Exception e ) {
			notifyFailed(e);
			return;
		}
		if ( !notifyStarted() ) {
			throw new IllegalStateException("cannot start loop execution because invalid state: state=" + getState());
		}
		
		try {
			loopCore();
		}
		finally {
			Try.run(this::finalizeLoop)
				.ifFailed(this::notifyFailed);
			m_aopGuard.runAndSignalAll(() -> m_loopFinished = true);
		}
	}
	
	private void loopCore() {
		long iterCount = -1;
		while ( true ) {
			if ( isCancelRequested() ) {
				notifyCancelled();
				return;
			}
			
			// 더 이상 loop을 진행할 필요가 있는지 조사하여
			// 필요없는 경우 (즉, isLoopFinished() 함수가 최종 결과 객체를 반환하는 경우)에는
			// loop을 종료시킨다.
			try {
				FOption<T> result = iterate(++iterCount);
				if ( result != null && result.isPresent() ) {
					notifyCompleted(result.getUnchecked());
					return;
				}
			}
			catch ( InterruptedException | CancellationException e ) {
				notifyCancelled();
				return;
			}
			catch ( Throwable e ) {
				notifyFailed(Throwables.unwrapThrowable(e));
				return;
			}
		}
	}
}
