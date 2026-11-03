package utils.func;


/**
 * {@link Exception}을 던질 수 있는 {@link java.util.function.Supplier} 형태의 함수 인터페이스.
 * <p>
 * 표준 {@code Supplier}로 바꾸려면 {@link UncheckedSupplier}({@code sneakyThrow}·{@code lift} 등)를 쓴다.
 *
 * @param <T>	공급하는 값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedSupplier<T> {
	/**
	 * 값을 생성하여 반환한다.
	 *
	 * @return	생성된 값.
	 * @throws Exception	함수 수행 중 오류가 발생한 경우.
	 */
	public T get() throws Exception;
	
	/**
	 * {@link #get()}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 예외는 던지지 않고 {@link Try#failure(Throwable)}로 반환한다. 단 {@link Error}는 잡지 않고
	 * 그대로 전파된다.
	 *
	 * @return	성공하면 생성된 값을 담은 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<T> tryGet() {
		try {
			return Try.success(get());
		}
		catch ( Exception e ) {
			return Try.failure(e);
		}
	}
	
}
