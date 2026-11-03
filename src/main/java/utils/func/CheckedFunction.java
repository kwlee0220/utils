package utils.func;

import java.util.function.Function;

import utils.Throwables;

/**
 * {@link Exception}을 던질 수 있는 {@link java.util.function.Function} 형태의 함수 인터페이스.
 *
 * @param <T>	입력 인자의 타입.
 * @param <R>	함수 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedFunction<T,R> {
	/**
	 * 주어진 인자에 함수를 적용한 결과를 반환한다.
	 *
	 * @param input	입력 인자.
	 * @return	함수 적용 결과.
	 * @throws Exception	함수 수행 중 오류가 발생한 경우.
	 */
	public R apply(T input) throws Exception;
	
	/**
	 * {@link #apply(Object)}를 호출하고 그 결과를 {@link Try}로 감싸 반환한다.
	 * 호출 중 발생한 모든 {@link Throwable}({@link Error} 포함)은 던지지 않고
	 * {@link Try#failure(Throwable)}로 반환한다.
	 *
	 * @param input	입력 인자.
	 * @return	성공하면 함수 결과를 담은 성공 {@link Try}, 예외가 발생하면 그 예외를 담은 실패 {@link Try}.
	 */
	public default Try<? extends R> tryApply(T input) {
		try {
			return Try.success(apply(input));
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
	
	/**
	 * 이 함수를 검사 예외를 선언하지 않는 {@link Function}으로 변환한다.
	 * 반환된 {@link Function}은 {@link #apply(Object)}가 던진 예외를 감싸지 않고
	 * {@link Throwables#sneakyThrow(Throwable)}로 그대로 다시 던진다(검사 예외도 컴파일러 검사 없이 전파된다).
	 *
	 * @return	이 함수에 위임하는 {@link Function}.
	 */
	public default Function<T,R> toSneakyThrowFunction() {
		return (in) -> {
			try {
				return apply(in);
			}
			catch ( Throwable e ) {
				Throwables.sneakyThrow(e);
				throw new AssertionError("Should not be here (CheckedFunction.sneakyThrow)");
			}
		};
	}
}
