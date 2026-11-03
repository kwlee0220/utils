package utils.func;


/**
 * 던질 수 있는 예외 타입을 타입 인자 {@code X}로 지정한 {@link java.util.function.Supplier} 형태의 함수 인터페이스.
 *
 * @param <T>	공급하는 값의 타입.
 * @param <X>	함수가 던질 수 있는 예외 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedSupplierX<T,X extends Throwable> {
	/**
	 * 값을 생성하여 반환한다.
	 *
	 * @return	생성된 값.
	 * @throws X	함수 수행 중 오류가 발생한 경우.
	 */
	public T get() throws X;
	
	/**
	 * {@link #get()}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 예외는 던지지 않고 {@link Try#failure(Throwable)}로 반환한다. 단 {@link Error}는 잡지 않고
	 * 그대로 전파된다.
	 * <b>주의:</b> 반환 타입이 {@code Try<? super T>}로 선언되어 있어({@link CheckedSupplier#tryGet()}는
	 * {@code Try<? extends T>}) 호출자는 결과 값을 {@code T} 타입으로 바로 꺼낼 수 없다.
	 *
	 * @return	성공하면 생성된 값을 담은 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<T> tryGet() {
		try {
			return Try.success(get());
		}
		catch ( Throwable e ) {
			if ( e instanceof Error err ) {
				throw err;
			}
			return Try.failure(e);
		}
	}
}

