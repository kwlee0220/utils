package utils.unchecked;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class IgnoreErrorHandler<T,R> implements ExceptionHandler<T,R> {
	@Override
	public R handle(ExceptionCase<T> ecase) throws RuntimeException {
		return null;
	}
}
