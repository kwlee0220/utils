package utils.async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ActiveCancellable extends AbstractCancellable {
	protected abstract boolean cancelTask();
	
	@Override
	public boolean cancel() {
		m_cancellableLock.lock();
		try {
			while ( m_cancellableState == State.IDLE ) {
				m_cancellableCond.await();
			}
			if ( m_cancellableState != State.RUNNING ) {
				return false;
			}
			
			m_cancellableState = State.CANCELLING;
			m_cancellableCond.signalAll();
		}
		catch ( InterruptedException e ) {
			return false;
		}
		finally {
			m_cancellableLock.unlock();
		}
		
		if ( !cancelTask() ) {
			return false;
		}

		try {
			waitForDone();
			return true;
		}
		catch ( InterruptedException e ) {
			return false;
		}
	}
}
