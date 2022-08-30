package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import utils.Throwables;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractLoopExecution<T> extends AbstractAsyncExecution<T> implements CancellableWork {
	private boolean m_loopFinished = false;
	
	protected abstract void initializeLoop() throws Exception;
	protected abstract FOption<T> isLoopFinished();
	protected abstract void iterate() throws Exception;
	protected abstract void finalizeLoop() throws Exception;
	
	@Override
	public final void start() {
		if ( !notifyStarting() ) {
			throw new IllegalStateException("cannot start because invalid state: state=" + getState());
		}
		
		Runnable work = () -> loop();
		Executor exector = getExecutor();
		if ( exector != null ) {
			exector.execute(work);
		}
		else {
			Thread thread = new Thread(work);
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
			throw new IllegalStateException("cannot start this execution because invalid state: state=" + getState());
		}
		
		while ( true ) {
			if ( isCancelRequested() ) {
				break;
			}
			
			FOption<T> result = isLoopFinished();
			if ( result.isPresent() ) {
				notifyCompleted(result.getUnchecked());
				try {
					finalizeLoop();
				}
				catch ( Exception e ) {
					notifyFailed(Throwables.unwrapThrowable(e));
				}
				break;
			}
			
			try {
				iterate();
			}
			catch ( InterruptedException | CancellationException e ) {
				notifyCancelled();
			}
			catch ( Throwable e ) {
				notifyFailed(Throwables.unwrapThrowable(e));
			}
		}
		
		m_aopGuard.runAndSignalAll(() -> m_loopFinished = true);
	}
}
