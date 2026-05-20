package utils.stream;

import utils.Preconditions;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface TriConsumer<S,T,R> {
	public void accept(S s, T t, R r);
	
    public default TriConsumer<S,T,R> andThen(TriConsumer<? super S, ? super T, ? super R> after) {
    	Preconditions.checkNotNullArgument(after, "after must not be null");

        return (s, t, r) -> {
            accept(s, t, r);
            after.accept(s, t, r);
        };
    }
}
