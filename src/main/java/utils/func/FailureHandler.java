package utils.func;


/**
 * 함수 실행 실패({@link FailureCase})를 처리하는 함수형 인터페이스이다.
 * <p>
 * {@code Unchecked*} 어댑터들이 감싼 함수 실행 중 예외가 발생하면 호출된다.
 * 미리 정의된 처리기는 {@link FailureHandlers}에서 얻을 수 있다.
 *
 * @param <T>	실패한 입력 데이터 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface FailureHandler<T> {
	/**
	 * 주어진 실패 정보를 처리한다.
	 *
	 * @param fcase	실패 정보.
	 */
	public void handle(FailureCase<T> fcase);
}
