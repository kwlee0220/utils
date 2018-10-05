package utils.async;

import java.util.concurrent.TimeUnit;


/**
 * @param <T>	비동기 연산의 결과 타입
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncExecution<T> extends Execution<T> {
	
	/**
	 * 비동기 작업 시작을 요청한다.
	 * 함수는 비동기 작업이 실제로 시작되기 전에 반환될 수 있기 때문에, 본 메소드의
	 * 반환이 작업을 시작되었음을 의미하지 않는다.
	 * 비동기 작업이 성공적으로 시작될 때까지 대기하려는 경우는 명시적으로
	 * {@link #waitForStarted()} 또는 {@link #waitForStarted(long, TimeUnit)}를
	 * 호출하여야 한다.
	 */
	public void start() throws IllegalStateException;
}
