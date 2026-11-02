package utils.thread;

import java.time.Duration;
import java.util.concurrent.ExecutionException;


/**
 * {@link RecurringWork}를 주기적으로 수행하는 스케줄.
 * <p>
 * 시간 값은 {@link Duration}으로 주고받는다.
 *
 * @author Kang-Woo Lee
 */
public interface RecurringSchedule {
	/**
	 * 스케줄의 상태.
	 * <p>
	 * 시작 전 상태는 따로 없으며 {@link #STOPPED}로 보고한다.
	 */
	public enum State {
		/** {@link RecurringWork#onStarted(RecurringSchedule)}를 수행 중. */
		STARTING,
		/** {@link RecurringWork#perform()}을 수행 중. */
		WORKING,
		/** 다음 수행을 기다리는 중. */
		IDLE,
		/** 종료가 요청되어 정리 중. */
		STOPPING,
		/** 종료됨. 시작 전도 이 값이다. */
		STOPPED
	};

	/**
	 * 현재 상태를 반환한다.
	 *
	 * @return	상태. 시작 전이면 {@link State#STOPPED}.
	 */
	public State getState();

	/**
	 * 기록된 실패 원인을 반환한다.
	 *
	 * @return	{@code onStarted()}나 {@code perform()}이 던진 예외. 없으면 {@code null}.
	 */
	public Throwable getFailureCause();

	/**
	 * 반복 작업 주기를 반환한다.
	 *
	 * @return	반복 작업 주기
	 */
	public Duration getInterval();

	/**
	 * 반복 작업 주기를 설정한다.
	 *
	 * @param interval	반복 작업 주기 (1ms 이상)
	 * @throws IllegalArgumentException	{@code interval}이 {@code null}이거나 1ms 미만인 경우
	 */
	public void setInterval(Duration interval);

	/**
	 * 반복 작업 주기를 밀리초로 설정한다.
	 *
	 * @param millis	반복 작업 주기 (단위: milli-second)
	 * @deprecated	{@link #setInterval(Duration)}을 쓴다.
	 */
	@Deprecated
	public default void setInterval(long millis) {
		setInterval(Duration.ofMillis(millis));
	}

	/**
	 * 다음 주기를 기다리지 않고 바로 {@link RecurringWork#perform()}을 수행하도록 요청한다.
	 *
	 * @return	요청을 받았으면 {@code true}, 받을 수 없는 상태(예: 수행 중, 시작 전, 종료 후)여서 버렸으면 {@code false}.
	 * @throws UnsupportedOperationException	구현이 즉시 수행을 지원하지 않는 경우. ({@link RecurringScheduleThread}는
	 * 										지원한다.)
	 */
	public boolean performNow();

	/**
	 * 반복 수행 작업을 시작시킨다.
	 * <p>
	 * 작업이 시작되면 미리 지정된 {@link RecurringWork}객체의 {@link RecurringWork#onStarted(RecurringSchedule)}
	 * 메소드가 호출되고, 이후 지정된 주기로 {@link RecurringWork#perform()}가 호출된다. 본 메소드는
	 * {@code onStarted()}가 끝날 때까지 기다린다.
	 *
	 * @throws ExecutionException	{@link RecurringWork#onStarted(RecurringSchedule)} 메소드 호출이
	 * 								예외를 발생시킨 경우. 스케줄은 반복을 시작하지 않고 종료된다.
	 * @throws IllegalStateException	이미 시작한 적이 있는 경우(재시작은 지원하지 않는다).
	 * @throws InterruptedException	{@code onStarted()}를 기다리는 중 인터럽트된 경우.
	 */
	public void start() throws ExecutionException, IllegalStateException, InterruptedException;

	/**
	 * 수행 중인 반복 수행 작업이 종료되도록 요청한다.
	 * <p>
	 * 작업 종료가 요청되면, 현재 해당 {@link RecurringWork#perform()}의 수행 여부에 따라
	 * <ul>
	 * 	<li> 수행 중이지 않고, 다음 번 iteration을 대기 중인 경우는 바로
	 * 		{@link RecurringWork#onStopped()} 메소드가 호출되고 종료되고,
	 * 		대기 중인 작업은 취소된다.
	 * <li> 수행 중인 경우는 종료 요청이 발생함을 알리는 마킹을 설정한 후,
	 * 		인자 {@code mayInterruptIfRunning} 값의 여부에 따라 <code>true</code>인 경우는 수행 중인
	 * 		경우는 작업 수행 쓰레드에 {@link Thread#interrupt()} 메소드를 호출하고 반환되고,
	 * 		{@code false}인 경우는 바로 반환된다.
	 * 		인터럽트 또는 작업 완료로 {@link RecurringWork#perform()}이 반환되면
	 * 		종료 요청 마커에 따라 {@link RecurringWork#onStopped()} 메소드가 호출되고 종료된다.
	 * </ul>
	 * 그러므로, 많은 경우 {@link RecurringWork#onStopped()}가 호출되고 실제 반복 수행 작업이
	 * 종료되기 전이 함수는 반환될 수 있다. 시작 전에 부르면 바로 {@link State#STOPPED}가 된다.
	 *
	 * @param mayInterruptIfRunning	호출시 {@link RecurringWork#perform()}이 수행 중인 경우
	 * 							수행 쓰레드의 {@link Thread#interrupt()} 메소드 호출 여부.
	 * 							true인 경우는 호출하고 false인 경우는 호출하지 않는다.
	 */
	public void stop(boolean mayInterruptIfRunning);

	/**
	 * 반복 수행 작업이 완전히 종료될 때까지 대기한다.
	 *
	 * @throws InterruptedException	대기가 인터럽트에 의해 중지된 경우.
	 * @throws IllegalStateException	{@link RecurringWork#perform()} 수행 쓰레드가 자신의 스케줄에 대해 호출하는
	 * 									경우(교착이 되므로).
	 */
	public void waitForStopped() throws InterruptedException;

	/**
	 * 반복 수행 작업이 완전히 종료될 때까지 주어진 시간 내에서 대기한다.
	 *
	 * @param timeout	제한 시간 (non-null, 0 이상)
	 * @return 주어진 시간내에 종료하는 경우는 true, 그렇지 않고 반환되는 경우는 false.
	 * @throws InterruptedException	대기가 인터럽트에 의해 중지된 경우.
	 * @throws IllegalArgumentException	{@code timeout}이 {@code null}이거나 음수인 경우.
	 * @throws IllegalStateException	{@link RecurringWork#perform()} 수행 쓰레드가 자신의 스케줄에 대해 호출하는
	 * 									경우(교착이 되므로).
	 */
	public boolean waitForStopped(Duration timeout) throws InterruptedException;

	/**
	 * 반복 수행 작업이 완전히 종료될 때까지 주어진 밀리초 내에서 대기한다.
	 *
	 * @param timeout	제한시간 (단위: milli-second)
	 * @return 주어진 시간내에 종료하는 경우는 true, 그렇지 않고 반환되는 경우는 false.
	 * @throws InterruptedException	대기가 인터럽트에 의해 중지된 경우.
	 * @deprecated	{@link #waitForStopped(Duration)}을 쓴다.
	 */
	@Deprecated
	public default boolean waitForStopped(long timeout) throws InterruptedException {
		return waitForStopped(Duration.ofMillis(Math.max(0, timeout)));
	}
}
