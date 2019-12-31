package utils.async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface StartableExecution<T> extends Execution<T> {
	/**
	 * 비동기 연산을 시작시킨다.
	 * <p>
	 * 본 메소드은 연산이 종료되기 전에 반환된다.
	 */
	public void start();
}
