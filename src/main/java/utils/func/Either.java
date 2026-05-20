package utils.func;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import utils.Preconditions;


/**
 * 두 가지 타입 중 하나의 값을 담을 수 있는 컨테이너 클래스.
 * <p>
 * Either 객체는 Left 값 또는 Right 값 중 정확히 하나의 값만을 가진다.
 * <p>
 * Left 값과 Right 값은 각각 {@link Optional}으로 래핑되어 제공되지만,
 * 반드시 둘 중 하나는 {@link Optional#empty() empty} 상태여야 한다.
 * 즉, Left 값과 Right 값이 동시에 존재하거나, 둘 다 존재하지 않는 상태는 허용되지 않는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Either<T1,T2> {
	private final Optional<T1> m_left;
	private final Optional<T2> m_right;
	
	/**
	 * Left 값을 담는 {@link Either}를 생성한다.
	 *
	 * @param <T1>	Left 값의 타입.
	 * @param <T2>	Right 값의 타입.
	 * @param value	설정할 Left 값.
	 * @return	Left 값을 포함한 {@link Either} 객체.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}인 경우.
	 */
	public static <T1,T2> Either<T1,T2> left(T1 value) {
		Preconditions.checkNotNullArgument(value, "Left value cannot be null");

		return new Either<>(Optional.of(value), Optional.empty());
	}
	
	/**
	 * Right 값을 담는 {@link Either}를 생성한다.
	 *
	 * @param <T1>	Left 값의 타입.
	 * @param <T2>	Right 값의 타입.
	 * @param value	설정할 Right 값.
	 * @return	Right 값을 포함한 {@link Either} 객체.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}인 경우.
	 */
	public static <T1,T2> Either<T1,T2> right(T2 value) {
		Preconditions.checkNotNullArgument(value, "Right value cannot be null");
		
		return new Either<>(Optional.empty(), Optional.of(value));
	}
	
	private Either(Optional<T1> left, Optional<T2> right) {
		m_left = left;
		m_right = right;
	}
	
	/**
	 * Left 값이 설정되어 있는지 여부를 반환한다.
	 *
	 * @return	Left 값이 존재하면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isLeft() {
		return m_left.isPresent();
	}
	
	/**
	 * Right 값이 설정되어 있는지 여부를 반환한다.
	 *
	 * @return	Right 값이 존재하면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isRight() {
		return m_right.isPresent();
	}
	
	/**
	 * Left 값을 {@link Optional}로 반환한다.
	 *
	 * @return	Left 값을 담은 {@link Optional}. Left 값이 없으면 {@link Optional#empty()}.
	 */
	public Optional<T1> left() {
		return m_left;
	}
	
	/**
	 * Right 값을 {@link Optional}로 반환한다.
	 *
	 * @return	Right 값을 담은 {@link Optional}. Right 값이 없으면 {@link Optional#empty()}.
	 */
	public Optional<T2> right() {
		return m_right;
	}
	
	/**
	 * Left 값을 반환한다.
	 *
	 * @return	Left 값.
	 * @throws NoSuchValueException	Left 값이 존재하지 않는 경우.
	 */
	public T1 getLeft() {
		return m_left.orElseThrow(() -> new NoSuchValueException("left"));
	}
	
	/**
	 * Right 값을 반환한다.
	 *
	 * @return	Right 값.
	 * @throws NoSuchValueException	Right 값이 존재하지 않는 경우.
	 */
	public T2 getRight() {
		return m_right.orElseThrow(() -> new NoSuchValueException("right"));
	}
	
	/**
	 * 저장된 값의 위치에 따라 대응하는 소비자를 호출한다.
	 * <p>
	 * Left 값이 존재하면 {@code leftConsumer}만 호출되고,
	 * Right 값이 존재하면 {@code rightConsumer}만 호출된다.
	 *
	 * @param leftConsumer	Left 값이 존재할 때 호출할 소비자.
	 * @param rightConsumer	Right 값이 존재할 때 호출할 소비자.
	 */
	public void match(Consumer<T1> leftConsumer, Consumer<T2> rightConsumer) {
		Preconditions.checkNotNullArgument(leftConsumer, "leftConsumer cannot be null");
		Preconditions.checkNotNullArgument(rightConsumer, "rightConsumer cannot be null");

		m_left.ifPresent(leftConsumer);
		m_right.ifPresent(rightConsumer);
	}

	/**
	 * Left 값과 Right 값에 각각 대응하는 함수를 적용하여 동일한 타입의 값으로 합친다.
	 *
	 * @param <R>		결과 값의 타입.
	 * @param ifLeft	Left 값이 존재할 때 적용할 함수.
	 * @param ifRight	Right 값이 존재할 때 적용할 함수.
	 * @return			선택된 함수의 결과 값.
	 */
	public <R> R fold(Function<? super T1, ? extends R> ifLeft,
						Function<? super T2, ? extends R> ifRight) {
		Preconditions.checkNotNullArgument(ifLeft, "ifLeft cannot be null");
		Preconditions.checkNotNullArgument(ifRight, "ifRight cannot be null");

		if ( m_left.isPresent() ) {
			return ifLeft.apply(m_left.get());
		}
		else {
			return ifRight.apply(m_right.get());
		}
	}

	/**
	 * Left 값에 함수를 적용한 새 {@link Either}를 반환한다. Right 값은 그대로 유지된다.
	 *
	 * @param <R>		변환된 Left 값의 타입.
	 * @param mapper	Left 값에 적용할 함수.
	 * @return			변환된 {@link Either}.
	 */
	public <R> Either<R,T2> mapLeft(Function<? super T1, ? extends R> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper cannot be null");

		if ( m_left.isPresent() ) {
			return Either.left(mapper.apply(m_left.get()));
		}
		else {
			return Either.right(m_right.get());
		}
	}

	/**
	 * Right 값에 함수를 적용한 새 {@link Either}를 반환한다. Left 값은 그대로 유지된다.
	 *
	 * @param <R>		변환된 Right 값의 타입.
	 * @param mapper	Right 값에 적용할 함수.
	 * @return			변환된 {@link Either}.
	 */
	public <R> Either<T1,R> mapRight(Function<? super T2, ? extends R> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper cannot be null");

		if ( m_right.isPresent() ) {
			return Either.right(mapper.apply(m_right.get()));
		}
		else {
			return Either.left(m_left.get());
		}
	}

	/**
	 * Left 값에 {@link Either}를 반환하는 함수를 적용한다. Right 값은 그대로 유지된다.
	 *
	 * @param <R>		변환된 Left 값의 타입.
	 * @param mapper	Left 값에 적용할 함수.
	 * @return			변환된 {@link Either}.
	 */
	public <R> Either<R,T2> flatMapLeft(Function<? super T1, Either<R,T2>> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper cannot be null");

		if ( m_left.isPresent() ) {
			return mapper.apply(m_left.get());
		}
		else {
			return Either.right(m_right.get());
		}
	}

	/**
	 * Right 값에 {@link Either}를 반환하는 함수를 적용한다. Left 값은 그대로 유지된다.
	 *
	 * @param <R>		변환된 Right 값의 타입.
	 * @param mapper	Right 값에 적용할 함수.
	 * @return			변환된 {@link Either}.
	 */
	public <R> Either<T1,R> flatMapRight(Function<? super T2, Either<T1,R>> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper cannot be null");

		if ( m_right.isPresent() ) {
			return mapper.apply(m_right.get());
		}
		else {
			return Either.left(m_left.get());
		}
	}

	/**
	 * Left와 Right를 뒤바꾼 새 {@link Either}를 반환한다.
	 *
	 * @return	Left와 Right가 교환된 {@link Either}.
	 */
	public Either<T2,T1> swap() {
		if ( m_left.isPresent() ) {
			return Either.right(m_left.get());
		}
		else {
			return Either.left(m_right.get());
		}
	}

	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj instanceof Either<?,?> other ) {
			return Objects.equals(m_left.orElse(null), other.m_left.orElse(null))
					&& Objects.equals(m_right.orElse(null), other.m_right.orElse(null));
		}
		else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(m_left.orElse(null), m_right.orElse(null));
	}
	
	@Override
	public String toString() {
		if ( m_left.isPresent() ) {
			return String.format("Either(left=%s)", m_left.get());
		}
		else {
			return String.format("Either(right=%s)", m_right.get());
		}
	}
}
