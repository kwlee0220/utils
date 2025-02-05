package utils.async;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

import utils.RuntimeInterruptedException;
import utils.RuntimeTimeoutException;
import utils.func.CheckedSupplier;
import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class GuardedAction<T extends GuardedAction<T>> implements Serializable {
	private static final long serialVersionUID = 1L;
	
	protected final Guard m_guard;
	@Nullable private Supplier<Boolean> m_preCondition;
	@Nullable private Date m_due;
	@Nullable private Duration m_timeout;
	
	protected GuardedAction(Guard guard) {
		Preconditions.checkArgument(guard != null, "Guard is null");
		
        m_guard = guard;
	}
	
	/**
	 * 사전 조건이 만족할 때까지 대기한다.
	 * <p>
	 * 사전 조건이 없는 경우에는 바로 반환한다.
	 * 본 메소드는 {@link Guard} 객체의 lock을 획득한 상태에서 호출되어야 한다.
	 * 
	 * @throws RuntimeTimeoutException	사전 조건 대기 시간이 경과한 경우.
	 * @throws RuntimeInterruptedException	대기 중에 interrupt가 발생한 경우.
	 */
	protected void awaitPreconditionInGuard() {
		if ( m_preCondition != null ) {
			Date due = m_due;
			if ( m_due == null ) {
				due = FOption.map(m_timeout, ts -> Date.from(Instant.now().plus(ts)));
			}
			
			while ( !m_preCondition.get() ) {
				try {
					if ( due != null ) {
						if ( !m_guard.awaitUntilInGuard(due) ) {
							TimeoutException cause = ( m_due != null )
													? new TimeoutException("due=" + due)
													: new TimeoutException("timeout=" + m_timeout);
							throw new RuntimeTimeoutException(cause);
						}
					}
					else {
						m_guard.awaitInGuard();
					}
				}
				catch ( InterruptedException e ) {
					throw new RuntimeInterruptedException(e);
				}
			}
		}
	}
	
	/**
	 * 사전 조건을 설정한다.
	 * <p>
	 * 사전 조건은 {@link CheckedSupplier}의 작업을 수행하기 전에 먼저 만족해야 하는 조건을 나타낸다.
	 * 만약 사전 조건이 설정되어 있고, 이 조건이 만족되지 않으면 계속 대기 상태로 있게 된다.
	 * 
	 * @param preCondition 사전 조건을 계산하는 {@link Supplier}.
	 * @return {@link GuardedAction} 객체.
	 */
	@SuppressWarnings("unchecked")
	public T preCondition(Supplier<Boolean> preCondition) {
		m_preCondition = preCondition;
		return (T)this;
	}
	
	/**
	 * 작업 사전 조건 대기의 최대 시각을 설정한다.
	 * <p>
	 * 사전 조건 대기 중 이 시각을 경과하는 경우 {@link TimeoutException}이 발생된다.
	 *
	 * @param due	사전 조건 대기의 최대 시각.
	 * @return {@link GuardedAction} 객체.
	 */
	@SuppressWarnings("unchecked")
	public T due(Date due) {
		m_due = due;
		return (T)this;
	}
	
	/**
	 * 작업 사전 조건 대기의 최대 시간을 설정한다.
	 * <p>
	 * 사전 조건 대기 중 이 시간을 경과하는 경우 {@link TimeoutException}이 발생된다.
	 *
	 * @param timeout	사전 조건 대기의 최대 시간.
	 * @return {@link GuardedAction} 객체.
	 */
	@SuppressWarnings("unchecked")
	public T timeout(Duration timeout) {
		m_timeout = timeout;
		return (T)this;
	}
	
	/**
	 * 작업 사전 조건 대기의 최대 시간을 설정한다.
	 * <p>
	 * 사전 조건 대기 중 이 시간을 경과하는 경우 {@link TimeoutException}이 발생된다.
	 *
	 * @param timeout	사전 조건 대기의 최대 시간.
	 * @param unit		시간 단위.
	 * @return {@link GuardedAction} 객체.
	 */
	public T timeout(long timeout, TimeUnit unit) {
		switch ( unit ) {
			case NANOSECONDS:
                return timeout(Duration.ofNanos(timeout));
            case MICROSECONDS:
                return timeout(Duration.ofNanos(TimeUnit.MICROSECONDS.toNanos(timeout)));
            case MILLISECONDS:
                return timeout(Duration.ofMillis(timeout));
            case SECONDS:
            	return timeout(Duration.ofSeconds(timeout));
            case MINUTES:
            	return timeout(Duration.ofMinutes(timeout));
            case HOURS:
            	return timeout(Duration.ofHours(timeout));
            case DAYS:
            	return timeout(Duration.ofDays(timeout));
            default:
                throw new IllegalArgumentException("Unknown TimeUnit: " + unit);
		}
	}
}
