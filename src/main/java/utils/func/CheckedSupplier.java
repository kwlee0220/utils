package utils.func;

import java.util.function.Supplier;

import utils.Throwables;

/**
 * {@link Exception}을 던질 수 있는 {@link java.util.function.Supplier} 형태의 함수 인터페이스.
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
	 * 호출 중 발생한 모든 {@link Throwable}({@link Error} 포함)은 던지지 않고
	 * {@link Try#failure(Throwable)}로 반환한다.
	 *
	 * @return	성공하면 생성된 값을 담은 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<? extends T> tryGet() {
		try {
			return Try.success(get());
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
	
	/**
	 * 이 공급자를 검사 예외를 선언하지 않는 {@link Supplier}로 변환한다.
	 * 반환된 {@link Supplier}는 {@link #get()}이 던진 예외를 감싸지 않고
	 * {@link Throwables#sneakyThrow(Throwable)}로 그대로 다시 던진다(검사 예외도 컴파일러 검사 없이 전파된다).
	 *
	 * @return	이 공급자에 위임하는 {@link Supplier}.
	 */
	public default Supplier<T> toSneakyThrowSupplier() {
		return () -> {
			try {
				return get();
			}
			catch ( Throwable e ) {
				Throwables.sneakyThrow(e);
				throw new AssertionError("Should not be here (CheckedSupplier.sneakyThrow)");
			}
		};
	}
}

