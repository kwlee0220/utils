package utils.thread;


import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;



/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CamusExecutor extends Executor {
	/**
	 * 주어진 작업을 주어진 시간 후에 수행시킨다.
	 * <p>
	 * delay가 0 또는 음수인 경우는 작업을 별도의 쓰레드에서 바로 시작시킨다.
	 *
	 * @param task	수행시킬 작업
	 * @param delay	지연 시간
	 * @param tu	지연 시간 단위.
	 * @return	요청된 지연 작업에 대한 future 객체.
	 * @throws IllegalArgumentException	task 또는 tu가 null인 경우.
	 */
	public Future<?> schedule(Runnable task, long delay, TimeUnit tu);

	/**
	 * 주어진 작업을 주어진 시간 후에 수행시킨다.
	 * <p>
	 * delay가 0 또는 음수인 경우는 해당 작업을 별도의 쓰레드에서 바로 시작시킨다.
	 *
	 * @param task	수행시킬 작업
	 * @param delayMillis	지연 시간 (단위: milli-seconds)
	 * @return	요청된 지연 작업에 대한 future 객체.
	 * @throws IllegalArgumentException	task가 null인 경우.
	 */
	public Future<?> schedule(Runnable task, long delayMillis);

	public Future<?> submit(Runnable task);
	public <T> Future<T> submit(Callable<T> task);
	public void execute(Runnable task);

	public RecurringSchedule createScheduleWithFixedRate(RecurringWork work, long initialDelay,
															long delay);
	public RecurringSchedule createScheduleWithFixedDelay(RecurringWork work, long initialDelay,
															long delay);
	public RecurringSchedule createScheduleWithFixedRate(Runnable work, long initialDelay,
															long delay);
	public RecurringSchedule createScheduleWithFixedDelay(Runnable work, long initialDelay,
															long delay);
	public void stop(boolean shutdownBaseExecutor);
}
