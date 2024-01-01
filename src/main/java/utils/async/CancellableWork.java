package utils.async;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface CancellableWork {
	/**
	 * 작업을 중단시킨다.
	 * 함수는 작업 중단이 실제로 마무리되기 전에 반환될 수 있기 때문에, 본 메소드 호출한 결과로
	 * 바로 종료되는 것을 의미하지 않는다.
	 * 또한 메소드 호출 당시 작업 상태에 따라 중단 요청을 무시되기도 한다.
	 * 이는 본 메소드의 반환값이 {@code false}인 경우는 요청이 명시적으로 무시된 것을 의미한다.
	 * 반환 값이 {@code true}인 경우는 중단 요청이 접수되어 중단 작업이 시작된 것을 의미한다.
	 * 물론, 이때도 중단이 반드시 성공하는 것을 의미하지 않는다.
	 * 
	 * @return	중단 요청의 접수 여부.
	 */
	public boolean cancelWork();
	
//	public boolean isCancelled();
//
//	/**
//	 * 비동기 작업이 종료될 때까지 대기한다.
//	 * 
//	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
//	 */
//	public void waitForDone() throws InterruptedException;
//
//	/**
//	 * 본 작업이 종료될 때까지 제한된 시간 동안만 대기한다.
//	 * 
//	 * @param timeout	대기시간
//	 * @param unit		대기시간 단위
//	 * @return	제한시간 전에 성공적으로 반환하는 경우는 {@code true},
//	 * 			대기 중에 제한시간 경과로 반환되는 경우는 {@code false}.
//	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
//	 */
//	public boolean waitForDone(long timeout, TimeUnit unit) throws InterruptedException;
//	
//	public void whenDone(Runnable handler);
}
