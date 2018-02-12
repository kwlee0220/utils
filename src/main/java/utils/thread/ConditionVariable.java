package utils.thread;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ConditionVariable {
	private final Condition m_cond;
	private final Supplier<Boolean> m_waitCond;
	
	public ConditionVariable(Condition cond, Supplier<Boolean> waitCond) {
		m_cond = cond;
		m_waitCond = waitCond;
	}
	
	public void await() throws InterruptedException {
		while ( m_waitCond.get() ) {
			m_cond.await();
		}
	}
	
	public boolean await(long time, TimeUnit unit) throws InterruptedException {
		Preconditions.checkArgument(time >= 0);
		
		final Date deadline = new Date(System.currentTimeMillis() + unit.toMillis(time));
		return awaitUntil(deadline);
	}
	
	public boolean awaitUntil(Date deadline) throws InterruptedException {
		while ( m_waitCond.get() ) {
			if ( !m_cond.awaitUntil(deadline) ) {
				return false;
			}
		}
		
		return true;
	}
}
