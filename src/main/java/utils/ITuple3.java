package utils;


/**
 * 세 값으로 이루어진 묶음.
 * <p>
 * 대표 구현은 불변 클래스 {@link Tuple3}이다. 각 값은 {@link #_1()}·{@link #_2()}·{@link #_3()}로 읽는다.
 * 값으로 {@code null}을 허용할지는 구현이 정한다.
 *
 * @param <T1>	첫 번째 값의 타입.
 * @param <T2>	두 번째 값의 타입.
 * @param <T3>	세 번째 값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface ITuple3<T1,T2,T3> {
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
	 * 세 번째 값을 반환한다.
	 *
	 * @return	세 번째 값.
	 */
	public T3 _3();
}
