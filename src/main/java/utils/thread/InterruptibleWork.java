package utils.thread;

/**
 * <code>InterruptibleWork</code>는 작업 수행 중 강제로 취소할 수 있는 작업의 인터페이스를 정의한다.
 * 
 * @author Kang-Woo Lee
 * @see RecurringWork
 */
public interface InterruptibleWork {
	/**
	 * 수행 중인 작업을 강제로 중단시킨다.
	 * <p>
	 * {@link RecurringScheduleThread#stop(boolean)}이 작업 쓰레드가 아닌 쓰레드에서 이 메소드를 호출한다.
	 */
	public void interrupt();
}