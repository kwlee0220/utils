package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface FailureHandler<T> {
	public void handle(FailureCase<? extends T> fcase);
}
