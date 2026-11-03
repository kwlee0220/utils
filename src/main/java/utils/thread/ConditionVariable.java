package utils.thread;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

/**
 * 대기 조건 함수와 {@link Condition}을 묶어, 조건이 해소될 때까지 기다리는 기능을 제공하는 클래스.
 * <p>
 * 대기 조건 함수가 {@code true}를 반환하는 동안 계속 대기한다. 모든 대기 메소드는 {@code Condition}을
 * 만든 lock을 호출 쓰레드가 보유한 상태에서 호출해야 한다(그렇지 않으면 {@code Condition} 구현이
 * {@link IllegalMonitorStateException}을 던질 수 있다).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ConditionVariable {
	private final Condition m_cond;
	private final Supplier<Boolean> m_waitCond;
	
	/**
	 * 조건 변수를 생성한다. 인자의 {@code null} 여부는 검사하지 않는다.
	 *
	 * @param cond		signal을 기다릴 {@link Condition}.
	 * @param waitCond	대기 조건 함수. {@code true}를 반환하는 동안 대기를 계속한다.
	 */
	public ConditionVariable(Condition cond, Supplier<Boolean> waitCond) {
		m_cond = cond;
		m_waitCond = waitCond;
	}
	
	/**
	 * 대기 조건 함수가 {@code false}를 반환할 때까지 무기한 대기한다.
	 * <p>
	 * 조건이 이미 해소되어 있으면 대기 없이 바로 반환한다.
	 *
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public void await() throws InterruptedException {
		while ( m_waitCond.get() ) {
			m_cond.await();
		}
	}
	
	/**
	 * 대기 조건 함수가 {@code false}를 반환하거나 주어진 시간이 경과할 때까지 대기한다.
	 * <p>
	 * 제한 시각은 호출 시점의 시스템 시계({@link System#currentTimeMillis()})를 기준으로 계산하여
	 * {@link #awaitUntil(Date)}로 대기한다.
	 *
	 * @param time	최대 대기 시간 (0 이상).
	 * @param unit	{@code time}의 시간 단위.
	 * @return	제한 시간 내에 조건이 해소된 경우 {@code true}, 시간 초과된 경우 {@code false}.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 * @throws IllegalArgumentException	{@code time}이 음수인 경우.
	 */
	public boolean await(long time, TimeUnit unit) throws InterruptedException {
		Preconditions.checkArgument(time >= 0);
		
		final Date deadline = new Date(System.currentTimeMillis() + unit.toMillis(time));
		return awaitUntil(deadline);
	}
	
	/**
	 * 대기 조건 함수가 {@code false}를 반환하거나 주어진 제한 시각이 경과할 때까지 대기한다.
	 *
	 * @param deadline	대기 제한 시각.
	 * @return	제한 시각 전에 조건이 해소된 경우 {@code true}, 시간 초과된 경우 {@code false}.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public boolean awaitUntil(Date deadline) throws InterruptedException {
		while ( m_waitCond.get() ) {
			if ( !m_cond.awaitUntil(deadline) ) {
				return false;
			}
		}
		
		return true;
	}
}
