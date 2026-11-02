package utils.thread;

/**
 * <code>InterruptibleWork</code>는 작업 수행 중 강제로 취소할 수 있는 작업의 인터페이스를 정의한다.
 * 
 * @author Kang-Woo Lee
 * @see RecurringWork
 */
public interface InterruptibleWork {
	public void interrupt();
}