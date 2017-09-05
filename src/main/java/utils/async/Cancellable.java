package utils.async;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Cancellable {
	/**
	 * 현 작업을 취소시킨다.
	 * 
	 * @return 작업이 이미 종료되어 취소될 수 없는 경우는 {@code false},
	 * 			그렇지 않으면 {@code true}.
	 */
	public boolean cancel();

	/**
	 * 작업의 종료 여부를 반환한다.
	 * 
	 * @return	종료 여부 (성공적으로 완료된 것과 취소된 것도 포함)
	 */
	public boolean isDone();

	/**
	 * 작업이 성공적으로 종료되었는지 여부 반환한다.
	 * 
	 * @return	성공적 종료 여부.
	 */
	public boolean isCompleted();

	/**
	 * 작업이 수행 도중에 취소되었는지 여부 반환한다.
	 * 
	 * @return	취소 여부.
	 */
	public boolean isCancelled();
}
