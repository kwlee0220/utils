package utils;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface Keyed<T> {
	/**
	 * 키 값을 반환한다.
	 * 
	 * @return 키 값
	 */
    public @NonNull T key();
}
