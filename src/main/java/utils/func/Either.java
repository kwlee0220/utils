package utils.func;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import utils.Preconditions;


/**
 * 두 가지 타입 중 하나의 값을 담을 수 있는 컨테이너 클래스.
 * <p>
 * Either 객체는 Left 값 또는 Right 값 중 정확히 하나의 값만을 가진다.
 * <p>
 * Left 값과 Right 값은 각각 {@link FOption}으로 래핑되어 제공되지만,
 * 반드시 둘 중 하나는 {@link FOption#empty() empty} 상태여야 한다.
 * 즉, Left 값과 Right 값이 동시에 존재하거나, 둘 다 존재하지 않는 상태는 허용되지 않는다.
 *
 * @param <L>	Left 값의 타입.
 * @param <R>	Right 값의 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Either<L,R> {
	private final FOption<L> m_left;
	private final FOption<R> m_right;
	
	/**
	 * Left 값을 담는 {@link Either}를 생성한다.
	 *
	 * @param <L>	Left 값의 타입.
	 * @param <R>	Right 값의 타입.
	 * @param value	설정할 Left 값.
	 * @return	Left 값을 포함한 {@link Either} 객체.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}인 경우.
	 */
	public static <L,R> Either<L,R> left(L value) {
		Preconditions.checkNotNullArgument(value, "Left value cannot be null");

		return new Either<>(FOption.of(value), FOption.empty());
	}
	
	/**
	 * Right 값을 담는 {@link Either}를 생성한다.
	 *
	 * @param <L>	Left 값의 타입.
	 * @param <R>	Right 값의 타입.
	 * @param value	설정할 Right 값.
	 * @return	Right 값을 포함한 {@link Either} 객체.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}인 경우.
	 */
	public static <L,R> Either<L,R> right(R value) {
		Preconditions.checkNotNullArgument(value, "Right value cannot be null");
		
		return new Either<>(FOption.empty(), FOption.of(value));
	}
	
	private Either(FOption<L> left, FOption<R> right) {
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
	 * Left 값을 {@link FOption}으로 반환한다.
	 *
	 * @return	Left 값을 담은 {@link FOption}. Left 값이 없으면 {@link FOption#empty()}.
	 */
	public FOption<L> left() {
		return m_left;
	}
	
	/**
	 * Right 값을 {@link FOption}으로 반환한다.
	 *
	 * @return	Right 값을 담은 {@link FOption}. Right 값이 없으면 {@link FOption#empty()}.
	 */
	public FOption<R> right() {
		return m_right;
	}
	
	/**
	 * Left 값을 반환한다.
	 *
	 * @return	Left 값.
	 * @throws NoSuchValueException	Left 값이 존재하지 않는 경우.
	 */
	public L getLeft() {
		return m_left.getOrThrow(() -> new NoSuchValueException("left"));
	}
	
	/**
	 * Right 값을 반환한다.
	 *
	 * @return	Right 값.
	 * @throws NoSuchValueException	Right 값이 존재하지 않는 경우.
	 */
	public R getRight() {
		return m_right.getOrThrow(() -> new NoSuchValueException("right"));
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
	public void match(Consumer<? super L> leftConsumer, Consumer<? super R> rightConsumer) {
		Preconditions.checkNotNullArgument(leftConsumer, "leftConsumer cannot be null");
		Preconditions.checkNotNullArgument(rightConsumer, "rightConsumer cannot be null");

		m_left.ifPresent(leftConsumer);
		m_right.ifPresent(rightConsumer);
	}

	/**
	 * Left 값과 Right 값에 각각 대응하는 함수를 적용하여 동일한 타입의 값으로 합친다.
	 *
	 * @param <U>		결과 값의 타입.
	 * @param ifLeft	Left 값이 존재할 때 적용할 함수.
	 * @param ifRight	Right 값이 존재할 때 적용할 함수.
	 * @return			선택된 함수의 결과 값.
	 */
	public <U> U fold(Function<? super L, ? extends U> ifLeft,
						Function<? super R, ? extends U> ifRight) {
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
	 * @param <U>		변환된 Left 값의 타입.
	 * @param mapper	Left 값에 적용할 함수.
	 * @return			변환된 {@link Either}.
	 */
	public <U> Either<U,R> mapLeft(Function<? super L, ? extends U> mapper) {
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
	 * @param <U>		변환된 Right 값의 타입.
	 * @param mapper	Right 값에 적용할 함수.
	 * @return			변환된 {@link Either}.
	 */
	public <U> Either<L,U> mapRight(Function<? super R, ? extends U> mapper) {
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
	 * @param <U>		변환된 Left 값의 타입.
	 * @param mapper	Left 값에 적용할 함수.
	 * @return			변환된 {@link Either}.
	 * @throws IllegalStateException	{@code mapper}가 컨테이너 대신 {@code null}을 반환한 경우.
	 */
	@SuppressWarnings("unchecked")
	public <U> Either<U,R>
	flatMapLeft(Function<? super L, ? extends Either<? extends U, ? extends R>> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper cannot be null");

		if ( m_left.isPresent() ) {
			// Either는 불변이라 Either<? extends U, ? extends R>를 Either<U,R>로 보아도 안전하다.
			return (Either<U,R>)FOption.checkMapped(mapper.apply(m_left.get()));
		}
		else {
			return Either.right(m_right.get());
		}
	}

	/**
	 * Right 값에 {@link Either}를 반환하는 함수를 적용한다. Left 값은 그대로 유지된다.
	 *
	 * @param <U>		변환된 Right 값의 타입.
	 * @param mapper	Right 값에 적용할 함수.
	 * @return			변환된 {@link Either}.
	 * @throws IllegalStateException	{@code mapper}가 컨테이너 대신 {@code null}을 반환한 경우.
	 */
	@SuppressWarnings("unchecked")
	public <U> Either<L,U>
	flatMapRight(Function<? super R, ? extends Either<? extends L, ? extends U>> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper cannot be null");

		if ( m_right.isPresent() ) {
			return (Either<L,U>)FOption.checkMapped(mapper.apply(m_right.get()));
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
	public Either<R,L> swap() {
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
			return m_left.equals(other.m_left) && m_right.equals(other.m_right);
		}
		else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(m_left, m_right);
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
