package utils.exception;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ExceptionHandler<T,R> {
	public R handle(ExceptionCase<T> ecase) throws RuntimeException;
}
