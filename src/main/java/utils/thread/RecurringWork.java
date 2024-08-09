package utils.thread;

/**
 * <code>RecurringWork</code>는 주기적으로 반복되는 작업의 인터페이스를 정의한다.
 * 
 * @author Kang-Woo Lee
 * @see RecurringSchedule
 */
public interface RecurringWork {
	/**
	 * 작업의 주기 반복이 시작되는 것을 알린다.
	 * <p>
	 * 본 메소드는 작업 주기 반복이 시작되기 직전에 호출되기 때문에, 작업 반복을 위한 초기화 작업을
	 * 위한 구현한다. 만일 예외 발생되면 작업 반복을 취소된다.
	 * 
	 * @param schedule	작업 반복을 처리하는 스케쥴 객체. 클래스 작성자는 이를 통해 작업 반복을
	 * 					처리하는 스케쥴을 통해 명시적 반복 작업을 제어할 수 있다.
	 * @throws	Throwable	작업 반복 시작에 앞서 필요한 초기화 작업 중 오류가 발생한 경우.
	 */
	public void onStarted(RecurringSchedule schedule) throws Throwable;
	
	/**
	 * 작업의 주기 반복이 종료되는 것을 알린다.
	 */
	public void onStopped();
	
	/**
	 * 반복해서 수행할 작업을 수행한다.
	 * <p>
	 * 메소드는 작업이 종료될 때까지 대기한다.
	 * 
	 * @throws Exception	작업 수행 중 오류가 발생된 경우.
	 */
	public void perform() throws Exception;
}