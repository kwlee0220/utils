package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedBiConsumer<S,T> {
	public void accept(S input1, T input2) throws Throwable;
	
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
