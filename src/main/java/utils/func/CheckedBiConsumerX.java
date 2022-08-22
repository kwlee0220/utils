package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedBiConsumerX<S,T,X extends Throwable> {
	public void accept(S input1, T input2) throws X;
	
	public default Try<Void> tryAccept(S input1, T input2) {
		try {
			accept(input1, input2);
			return Try.success(null);
		}
		catch ( Throwable e ) {
			return Try.failure(e);
		}
	}
}
