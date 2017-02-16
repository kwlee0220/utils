package utils.thread;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;


/**
 *
 * @author Kang-Woo Lee
 */
public interface RecurringSchedule {
	public enum State {
		STARTING,
		WORKING,
		IDLE,
		STOPPING,
		STOPPED
	};

	public State getState();
	
	public Throwable getFailureCause();
	
	/**
	 * 반복 작업 주기를 반환한다.
	 * 
	 * @return	반복 작업 주기 (단위: millseconds)
	 */
	public long getInterval();
	
	/**
	 * 반복 작업 주기를 설정한다.
	 * 
	 * @param millis	반복 작업 주기 (단위: millseconds)
	 */
	public void setInterval(long millis);
	
	public void performNow();

	/**
	 * 반복 수행 작업을 시작시킨다.
	 * <p>
	 * 작업이 시작되면 미리 지정된 {@link RecurringWork}객체의 {@link RecurringWork#onStarted(RecurringSchedule)}
	 * 메소드가 호출되고, 이후 지정된 주기로 {@link RecurringWork#perform()}가 호출된다.
	 *
	 * @throws ExecutionException	{@link RecurringWork#onStarted(RecurringSchedule)} 메소드 호출이
	 * 								예외를 발생시킨 경우.
	 * @throws IllegalStateException	작업이 시작된 경우.
	 * @throws InterruptedException	작업 시장 중 강제 중단된 경우.
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
	 * 종료되기 전이 함수는 반환될 수 있다.
	 *
	 * @param mayInterruptIfRunning	호출시 {@link RecurringWork#perform()}이 수행 중인 경우
	 * 							수행 쓰레드의 {@link Thread#interrupt()} 메소드 호출 여부.
	 * 							true인 경우는 호출하고 false인 경우는 호출하지 않는다.
	 */
	public void stop(boolean mayInterruptIfRunning);

	/**
	 * 반복 수행 작업이 완전히 종료될 때까지 대기한다.
	 * <p>
	 * 만일 이 함수를 {@link RecurringWork#perform()} 수행 중 해당 쓰레드에서 호출하면
	 * deadlock 발생될 여지가 있어 {@link RuntimeException} 예외가 발생된다.
	 *
	 * @throws InterruptedException	대기가 인터럽트에 의해 중지된 경우.
	 * @throws RuntimeException		{@link RecurringWork#perform()} 수행 쓰레드가 자신의
	 * 								스케줄에 대해 호출하는 경우.
	 */
	public void waitForStopped() throws InterruptedException;

	/**
	 * 반복 수행 작업이 완전히 종료될 때까지 주어진 시간 내에서 대기한다.
	 * 만일 주어진 시간 내에 스케쥴이 완료되지 않으면 {@link TimeoutException} 예외가 발생된다.
	 * <p>
	 * 만일 이 함수를 {@link RecurringWork#perform()} 수행 중 해당 쓰레드에서 호출하면
	 * {@link RuntimeException} 예외가 발생된다.
	 * 
	 * @param timeout	제한시간 (단위: milli-second)
	 * @return 주어진 시간내에 종료하는 경우는 true, 그렇지 않고 반환되는 경우는 false.
	 * @throws InterruptedException	대기가 인터럽트에 의해 중지된 경우.
	 * @throws RuntimeException		{@link RecurringWork#perform()} 수행 쓰레드가 자신의
	 * 								스케줄에 대해 호출하는 경우.
	 */
	public boolean waitForStopped(long timeout) throws InterruptedException;

	public void setLogger(Logger logger);
}
