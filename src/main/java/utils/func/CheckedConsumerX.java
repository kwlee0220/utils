package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedConsumerX<T,X extends Throwable> {
	public void accept(T data) throws X;
}