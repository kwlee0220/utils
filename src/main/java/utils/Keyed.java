package utils;

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
    public T key();
}
