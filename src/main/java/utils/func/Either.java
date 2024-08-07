package utils.func;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Either<T1,T2> implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private final FOption<T1> m_left;
	private final FOption<T2> m_right;
	
	public static <T1,T2> Either<T1,T2> left(T1 value) {
		return new Either<>(FOption.of(value), FOption.empty());
	}
	
	public static <T1,T2> Either<T1,T2> right(T2 value) {
		return new Either<>(FOption.empty(), FOption.of(value));
	}
	
	private Either(FOption<T1> left, FOption<T2> right) {
		m_left = left;
		m_right = right;
	}
	
	public boolean isLeft() {
		return m_left.isPresent();
	}
	
	public boolean isRight() {
		return m_right.isPresent();
	}
	
	public FOption<T1> left() {
		return m_left;
	}
	
	public FOption<T2> right() {
		return m_right;
	}
	
	public T1 getLeft() {
		return m_left.getOrThrow(() -> new NoSuchValueException("left"));
	}
	
	public T2 getRight() {
		return m_right.getOrThrow(() -> new NoSuchValueException("right"));
	}
	
	public <S> S mapGet(Function<T1,S> leftMapper, Function<T2,S> rightMapper) {
		return m_left.map(leftMapper)
					.orElse(() -> m_right.map(rightMapper))
					.get();
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
			return String.format("Either(left=%s)", m_left.getUnchecked());
		}
		else {
			return String.format("Either(right=%s)", m_right.getUnchecked());
		}
	}
}
