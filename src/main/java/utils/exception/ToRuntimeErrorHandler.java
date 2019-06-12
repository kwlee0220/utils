package utils.exception;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ToRuntimeErrorHandler<T,R> implements ExceptionHandler<T,R> {
	@Override
	public R handle(ExceptionCase<T> ecase) throws RuntimeException {
		throw Throwables.toRuntimeException(ecase.getCause());
	}
}

