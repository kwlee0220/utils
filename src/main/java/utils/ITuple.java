package utils;


/**
 * 두 값으로 이루어진 순서쌍.
 * <p>
 * 대표 구현은 불변 클래스 {@link Tuple}이다. 각 값은 {@link #_1()}·{@link #_2()} 또는 같은 값을 돌려주는
 * {@link #left()}·{@link #right()}로 읽는다. 값으로 {@code null}을 허용할지는 구현이 정한다.
 *
 * @param <T1>	첫 번째 값의 타입.
 * @param <T2>	두 번째 값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface ITuple<T1,T2> {
	/**
	 * 첫 번째 값을 반환한다.
	 *
	 * @return	첫 번째 값.
	 */
	public T1 _1();

	/**
	 * 두 번째 값을 반환한다.
	 *
	 * @return	두 번째 값.
	 */
	public T2 _2();
	
	/**
	 * 첫 번째 값을 반환한다. {@link #_1()}과 같다.
	 *
	 * @return	첫 번째 값.
	 */
	public default T1 left() {
		return _1();
	}
	
	/**
	 * 두 번째 값을 반환한다. {@link #_2()}와 같다.
	 *
	 * @return	두 번째 값.
	 */
	public default T2 right() {
		return _2();
	}
	
	/**
	 * 두 값의 순서를 바꾼 순서쌍을 반환한다. 이 객체는 바뀌지 않는다.
	 *
	 * @return	{@code (_2(), _1())}로 이루어진 순서쌍.
	 */
	public ITuple<T2,T1> swap();
}
