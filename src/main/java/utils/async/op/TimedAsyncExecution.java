package utils.async.op;


import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.vavr.control.Try;
import net.jcip.annotations.GuardedBy;
import utils.Guard;
import utils.async.AbstractAsyncExecution;
import utils.async.AsyncExecution;
import utils.async.CancellableWork;


/**
 * <code>TimedAsyncExecution</code>은 주어진 비동기 수행을 주어진 시간동안만 수행시키는
 * 비동기 수행 클래스를 정의한다.
 * <p>
 * 주어진 대상 비동기 수행(target_aop)이 정해진 제한 시간(timout) 내에 수행 여부에 따라 본 연산의 수행 결과는
 * 다음과 같이 정의된다.
 * <dl>
 * 	<dt> 제한된 시간 내에 종료된 경우</dt>
 * 	<dd> target_aop의 수행 종료 상태에 따라,
 * 		<dl>
 * 		<dt> 성공적으로 완료된 경우:
 * 			<dd> 본 연산은 성공적으로 완료된 것으로 설정되고, target_aop의 결과 값을 결과 값으로 한다.
 * 		<dt> 실패한 경우:
 * 			<dd> 본 연산은 실패한 것으로 설정되고, target_aop에서 발생된 예외를 실패 원인 예외로 설정한다.
 * 		<dt> 취소된 경우:
 * 			<dd> 본 연산은 취소된 것으로 설정된다.
 * 		</dl>
 *	<dt> 제한된 시간 내에 종료되지 못한 경우
 *	<dd> 시간 초과 처리 비동기 수행 (timout_aop)의 유무에 따라,
 *		<dl>
 *		<dt> timout_aop이 설정되지 않은 경우.
 *			<dd> 본 연산은 취소된 것으로 간주된다.
 *		<dt> timout_aop이 설정된 경우.
 *			<dd> timeout_aop를 시작시키고, 그 수행이 종료될 때까지 본 연산은 수행이 지속된다.
 *				이 시기에 본 연산을 강제 종료시키면 timeout_aop가 종료된다.
 *				timeout_aop가 성공적으로 완료되면 전체 연산을 성공적으로 완료된 것으로 간주되고,
 *				timeout_aop의 결과 값이 전체 연산의 결과 값으로 설정된다.
 *				만일 timeout_aop의 수행 결과가 중단되거나 실패하는 경우는 전체 연산은 중단된 것으로
 *				간주된다.
 *		</dl>
 * </dl>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TimedAsyncExecution<T> extends AbstractAsyncExecution<T>
									implements CancellableWork {
	static final Logger s_logger = LoggerFactory.getLogger("AOP.TIMED");

	private static final int STATE_IDLE = 0;			// 비동기 수행이 수행중인 상태.
	private static final int STATE_RUNNING = 1;			// 비동기 수행이 수행중인 상태.
	private static final int STATE_TIMEOUT_CANCEL = 2;	// 시간초과가 발생하여 cancel되는 상태.
	private static final int STATE_PARENT_CANCEL = 3;	// TimedAsyncExecution 수준에서 cancel되는 상태.
	private static final int STATE_TARGET_CANCEL = 4;	// Target AsyncExecution 수준에서 cancel되는 상태.

	private final AsyncExecution<T> m_target;
	private final long m_timeout;
	private final TimeUnit m_unit;
	private final ScheduledExecutorService m_scheduler;
	private volatile Future<?> m_future;

	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private int m_istate = STATE_IDLE;

	/**
	 * 시간 제한 수행 비동기 수행 객체를 생성한다.
	 *
	 * @param aop	제한 시간 동안 수행될 대상 비동기 수행 객체.
	 * @param timeout	제한 시간 (단위: millisecond)
	 * @throws IllegalArgumentException	{@literal aop}, <code>executor</code>가 <code>null</code>인 경우거나
	 * 								{@literal timeout}이 음수이거나 0인 경우.
	 */
	TimedAsyncExecution(AsyncExecution<T> aexec, long timeout, TimeUnit unit,
						ScheduledExecutorService scheduler) {
		Objects.requireNonNull(aexec, "AsyncExecution");
		Objects.requireNonNull(unit, "TimeUnit");
		Objects.requireNonNull(scheduler, "ScheduledExecutorService");
		Preconditions.checkArgument(timeout >= 0,
								"timeout should be greater than zero: timeout=" + timeout);

		m_target = aexec;
		m_timeout = timeout;
		m_unit = unit;
		m_scheduler = scheduler;
	}
	
	public final AsyncExecution<T> getTargetOperation() {
		return m_target;
	}

	public final long getTimeout() {
		return m_timeout;
	}
	
	public boolean isTimedout() {
		return m_guard.get(() -> m_istate == STATE_TIMEOUT_CANCEL);
	}

	@Override
	public String toString() {
		return "Timed[aop=" + m_target + ", timeout=" + m_timeout + "]";
	}

	@Override
	public void start() {
		if ( !notifyStarting() ) {
			return;
		}
		
		m_target.whenStarted(this::onTargetStarted);
		m_target.whenFinished(r -> r.ifCompleted(this::notifyCompleted)
								.ifFailed(this::notifyFailed)
								.ifCancelled(this::onTargetCancelled));
		m_guard.run(() -> m_istate = STATE_IDLE, false);
		
		m_target.start();
	}

	@Override
	public boolean cancelWork() {
		// TimedAsyncExecution을 cancel 시킨 경우:
		// (이 경우와 m_target AsyncExecution이 cancel되어 간접적으로
		// TimedAsyncExecution이 cancel되는 경우를 따로 처리해야 한다.)
		int istate = m_guard.get(() -> {
			if ( m_istate == STATE_RUNNING ) {
				m_istate = STATE_PARENT_CANCEL;
			}
			return m_istate;
		});
		Preconditions.checkState(istate > STATE_RUNNING);
		
		if ( istate == STATE_PARENT_CANCEL ) {
			getLogger().debug("cancelling: {}", this);

			// 이후 timeout이 발생되어도 cancel이 trigger되지 않도록 future를 cancel시킨다.
			m_future.cancel(false);
			return m_target.cancel(true);
		}
		else {
			return true;
		}
	}
	
	private void onTargetStarted() {
		m_future = m_scheduler.schedule(this::onTimeout, m_timeout, m_unit);
		m_guard.run(() -> m_istate = STATE_RUNNING, true);
		notifyStarted();
	}
	
	private void onTargetCancelled() {
		int istate = m_guard.get(() -> {
			if ( m_istate == STATE_RUNNING ) {
				m_istate = STATE_TARGET_CANCEL;
			}
			return m_istate;
		});
		
		// 이후 timeout이 발생되어도 cancel이 trigger되지 않도록 future를 cancel시킨다.
		m_future.cancel(false);
		notifyCancelled();
	}

	private void onTimeout() {
		// 'onTargetStarted()' 메소드에서 timeout이 설정될 때, 'notifyStarted()' 메소드가
		// 호출되기도 전에 timeout이 발생될 수도 있기 때문에 timeout시 본 실행이 start될 때까지
		// 대기하도록 한다.
		Try.run(this::waitForStarted);
		
		int istate = m_guard.get(() -> {
			if ( m_istate == STATE_RUNNING ) {
				m_istate = STATE_TIMEOUT_CANCEL;
			}
			return m_istate;
		});
		if ( istate == STATE_TIMEOUT_CANCEL ) {
			m_target.cancel(true);
		}
	}
}