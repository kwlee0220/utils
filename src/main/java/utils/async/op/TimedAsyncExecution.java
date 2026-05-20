package utils.async.op;


import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Preconditions;
import utils.async.AbstractAsyncExecution;
import utils.async.CancellableWork;
import utils.async.StartableExecution;


/**
 * <code>TimedAsyncExecution</code>은 주어진 비동기 수행을 주어진 시간동안만 수행시키는
 * 비동기 수행 클래스를 정의한다.
 * <p>
 * 주어진 대상 비동기 수행(target_aop)이 정해진 제한 시간(timeout) 내에 수행 여부에 따라 본 연산의 수행 결과는
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
 *	<dd> target_aop를 cancel시키고 본 연산은 취소된 것으로 간주된다.
 * </dl>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TimedAsyncExecution<T> extends AbstractAsyncExecution<T> implements CancellableWork {
	private static final Logger s_logger = LoggerFactory.getLogger(TimedAsyncExecution.class);

	private final StartableExecution<T> m_target;
	private final Duration m_timeout;
	private final ScheduledExecutorService m_scheduler;

	private volatile Future<?> m_cancelFuture;
	private volatile boolean m_timedOutCancelRequested = false;

	/**
	 * 시간 제한 수행 비동기 수행 객체를 생성한다.
	 *
	 * @param aexec		제한 시간 동안 수행될 대상 비동기 수행 객체.
	 * @param timeout	제한 시간.
	 * @param scheduler	타임아웃 트리거에 사용할 스케줄러.
	 * @throws IllegalArgumentException	{@literal aexec}, {@literal timeout}, {@literal scheduler} 중
	 * 								하나라도 {@code null}인 경우.
	 */
	public TimedAsyncExecution(StartableExecution<T> aexec, Duration timeout, ScheduledExecutorService scheduler) {
		Preconditions.checkNotNullArgument(aexec, "aexec");
		Preconditions.checkNotNullArgument(timeout, "timeout");
		Preconditions.checkNotNullArgument(scheduler, "scheduler");

		m_target = aexec;
		m_timeout = timeout;
		m_scheduler = scheduler;

		setLogger(s_logger);
	}
	
	/**
	 * 본 실행의 대상이 되는 비동기 수행 객체를 반환한다.
	 *
	 * @return 제한 시간 동안 수행될 대상 비동기 수행 객체.
	 */
	public final StartableExecution<T> getTargetOperation() {
		return m_target;
	}

	/**
	 * 본 실행에 적용된 제한 시간을 반환한다.
	 *
	 * @return 제한 시간.
	 */
	public final Duration getTimeout() {
		return m_timeout;
	}

	/**
	 * 본 실행이 타임아웃으로 인해 취소되었는지 여부를 반환한다.
	 * <p>
	 * 외부에서 {@link #cancel(boolean)} 호출로 취소된 경우와 대상 실행이 자체적으로
	 * 취소된 경우에는 {@code false}를 반환한다.
	 * <p>
	 * 본 메소드는 본 실행이 종료된 뒤에 호출하는 것을 전제로 한다. 타임아웃 트리거 발생
	 * 직후 ~ 대상 실행이 실제 CANCELLED 상태에 도달하기 전 짧은 윈도우에서는 {@code false}를
	 * 반환할 수 있다.
	 *
	 * @return 제한 시간 초과로 인해 취소가 트리거되었으면 {@code true}.
	 */
	public boolean isTimedoutCancelled() {
		return m_timedOutCancelRequested && m_target.isCancelled();
	}

	@Override
	public String toString() {
		return "Timed[aop=" + m_target + ", timeout=" + m_timeout + "]";
	}

	/**
	 * 대상 비동기 수행을 시작한다.
	 * <p>
	 * 대상이 RUNNING 상태로 전이되면 타임아웃 트리거를 스케줄러에 등록하고 본 실행도
	 * RUNNING 상태로 전이한다. 대상이 종료되면 그 결과(성공/실패/취소)를 본 실행에
	 * 그대로 전이시킨다.
	 */
	@Override
	public void start() {
		if ( !notifyStarting() ) {
			return;
		}

		m_target.whenStarted(this::onTargetStarted);
		m_target.whenFinished(result -> {
			Future<?> cancelFuture = m_cancelFuture;
			if ( cancelFuture != null ) {
				cancelFuture.cancel(false);
			}
			result.ifSuccessful(this::notifyCompleted)
				.ifFailed(this::notifyFailed)
				.ifNone(this::onTargetCancelled);
		});

		m_target.start();
	}

	/**
	 * 대상 비동기 수행에 대해 강제 취소를 요청한다.
	 * <p>
	 * {@link CancellableWork} 구현으로서, 본 실행에 대한 {@link #cancel(boolean)} 호출
	 * 시 내부적으로 호출된다. 타임아웃 발생 시에도 본 메소드를 통해 대상이 취소된다.
	 *
	 * @return 대상 실행의 {@code cancel(true)} 결과.
	 */
	@Override
	public boolean cancelWork() {
		return m_target.cancel(true);
	}
	
	/**
	 * 대상 실행이 RUNNING 상태에 진입했을 때 호출되는 콜백.
	 * <p>
	 * 타임아웃 트리거를 스케줄러에 등록하고 본 실행을 RUNNING 상태로 전이한다.
	 * 본 실행이 이미 종료/취소되어 {@link #notifyStarted()}가 실패하면 등록한
	 * 트리거를 즉시 취소한다.
	 */
	private void onTargetStarted() {
		Future<?> future = m_scheduler.schedule(this::onTimeout, m_timeout.toMillis(),
												TimeUnit.MILLISECONDS);
		if ( notifyStarted() ) {
			m_cancelFuture = future;
		}
		else {
			future.cancel(false);
		}
	}

	/**
	 * 대상 실행이 취소되어 종료되었을 때 호출되는 콜백.
	 * <p>
	 * 본 실행을 CANCELLED 상태로 전이시킨다. 외부 {@link #cancel(boolean)} 호출에
	 * 의한 취소든 타임아웃 트리거에 의한 취소든 동일하게 처리되며, 타임아웃에 의한
	 * 취소 여부는 {@link #isTimedoutCancelled()}로 구분할 수 있다.
	 * <p>
	 * 예약된 타임아웃 트리거의 취소는 {@link #start()}의 {@code whenFinished} 콜백
	 * 진입부에서 모든 종료 경로에 대해 일괄적으로 수행하므로 본 메소드에서는 다루지 않는다.
	 */
	private void onTargetCancelled() {
		notifyCancelled();
	}

	/**
	 * 타임아웃 발생 시 스케줄러에 의해 호출되는 콜백.
	 * <p>
	 * 본 실행이 아직 종료되지 않은 경우 타임아웃 플래그를 설정하고 {@code cancel(true)}를
	 * 호출하여 {@link CancellableWork} 경로를 통해 대상 실행을 강제 취소시킨다.
	 * 이미 종료된 경우 아무 작업도 하지 않는다.
	 */
	private void onTimeout() {
		if ( isDone() ) {
			return;
		}

		m_timedOutCancelRequested = true;
		cancel(true);
	}
}