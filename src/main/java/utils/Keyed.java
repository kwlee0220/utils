package utils;

/**
 * 키를 가진 객체를 나타내는 인터페이스.
 *
 * @param <T>	키의 타입.
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
