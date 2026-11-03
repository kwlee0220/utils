package utils.func;

import java.io.Serializable;
import java.util.function.Predicate;

/**
 * 직렬화 가능한({@link Serializable}) {@link Predicate}이다.
 * <p>
 * 람다식의 대상 타입으로 사용하면 생성된 술어 객체를 직렬화할 수 있다.
 *
 * @param <T>	술어 입력 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface SerializablePredicate<T> extends Predicate<T>, Serializable { }
