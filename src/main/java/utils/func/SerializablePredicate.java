package utils.func;

import java.io.Serializable;
import java.util.function.Predicate;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface SerializablePredicate<T> extends Predicate<T>, Serializable { }
