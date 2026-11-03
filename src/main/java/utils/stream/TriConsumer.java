package utils.stream;

import utils.Preconditions;

/**
 * 세 개의 인자를 받아 결과 없이 작업을 수행하는 함수 인터페이스.
 * <p>
 * {@link java.util.function.BiConsumer}의 세 인자 버전이다.
 * 
 * @param <S>	첫 번째 인자 타입.
 * @param <T>	두 번째 인자 타입.
 * @param <R>	세 번째 인자 타입.
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface TriConsumer<S,T,R> {
	/**
	 * 주어진 세 인자로 작업을 수행한다.
	 *
	 * @param s	첫 번째 인자.
	 * @param t	두 번째 인자.
	 * @param r	세 번째 인자.
	 */
	public void accept(S s, T t, R r);
	
	/**
	 * 이 작업을 수행한 뒤 {@code after}를 같은 인자로 이어서 수행하는 {@code TriConsumer}를 반환한다.
	 * <p>
	 * 이 작업이 예외를 던지면 {@code after}는 수행되지 않는다.
	 *
	 * @param after	이어서 수행할 작업.
	 * @return	두 작업을 차례로 수행하는 {@code TriConsumer}.
	 * @throws IllegalArgumentException	{@code after}가 {@code null}인 경우.
	 */
    public default TriConsumer<S,T,R> andThen(TriConsumer<? super S, ? super T, ? super R> after) {
    	Preconditions.checkNotNullArgument(after, "after must not be null");

        return (s, t, r) -> {
            accept(s, t, r);
            after.accept(s, t, r);
        };
    }
}
