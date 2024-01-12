package utils.stream;

import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface TriConsumer<S,T,R> {
	public void accept(S s, T t, R r);
	
    public default TriConsumer<S,T,R> andThen(TriConsumer<? super S, ? super T, ? super R> after) {
        Objects.requireNonNull(after);

        return (s, t, r) -> {
            accept(s, t, r);
            after.accept(s, t, r);
        };
    }
}
