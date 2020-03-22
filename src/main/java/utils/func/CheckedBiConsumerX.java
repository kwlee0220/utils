package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedBiConsumerX<S,T,X extends Throwable> {
	public void accept(S input1, T input2) throws X;
}
