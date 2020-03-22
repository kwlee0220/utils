package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedBiConsumer<S,T> {
	public void accept(S input1, T input2) throws Throwable;
}
