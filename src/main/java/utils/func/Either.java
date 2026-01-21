package utils.func;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * 두 가지 타입 중 하나의 값을 담을 수 있는 컨테이너 클래스.
 * <p>
 * Either 객체는 Left 값 또는 Right 값 중 하나만을 가질 수 있으며,
 * 값이 존재하지 않는 경우도 허용한다.
 * <p>
 * Left 값과 Right 값은 각각 {@link Optional}으로 래핑되어 제공되지만,
 * 반드시 둘 중 하나는 {@link Optional#empty() empty} 상태여야 한다.
 * 즉, Left 값과 Right 값이 동시에 존재하거나, 둘 다 존재하지 않는 상태는 허용되지 않는다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Either<T1,T2> implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private final Optional<T1> m_left;
	private final Optional<T2> m_right;
	
	public static <T1,T2> Either<T1,T2> left(T1 value) {
		return new Either<>(Optional.of(value), Optional.empty());
	}
	
	public static <T1,T2> Either<T1,T2> right(T2 value) {
		return new Either<>(Optional.empty(), Optional.of(value));
	}
	
	private Either(Optional<T1> left, Optional<T2> right) {
		m_left = left;
		m_right = right;
	}
	
	public boolean isLeft() {
		return m_left.isPresent();
	}
	
	public boolean isRight() {
		return m_right.isPresent();
	}
	
	public Optional<T1> left() {
		return m_left;
	}
	
	public Optional<T2> right() {
		return m_right;
	}
	
	public T1 getLeft() {
		return m_left.orElseThrow(() -> new NoSuchValueException("left"));
	}
	
	public T2 getRight() {
		return m_right.orElseThrow(() -> new NoSuchValueException("right"));
	}
	
	public void forEach(Consumer<T1> leftConsumer, Consumer<T2> rightConsumer) {
		m_left.ifPresent(leftConsumer);
		m_right.ifPresent(rightConsumer);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || obj.getClass() != Either.class ) {
			return false;
		}
		
		@SuppressWarnings("unchecked")
		Either<T1,T2> other = (Either<T1,T2>)obj;
		return Objects.equals(m_left, other.m_left) && Objects.equals(m_right, other.m_right);
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
