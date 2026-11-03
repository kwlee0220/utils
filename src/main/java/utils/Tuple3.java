package utils;

import java.util.Objects;

/**
 * 세 값을 묶은 3-튜플.
 * <p>
 * 세 원소는 {@code public final} 필드이므로 생성 후 다른 객체로 바꿀 수 없다(원소 객체 자체의
 * 불변성은 보장하지 않는다). 원소는 {@code null}일 수 있다. {@link #equals(Object)}는 클래스가
 * 정확히 {@code Tuple3}인 객체에 대해서만 세 원소를 {@link Objects#equals(Object, Object)}로 비교한다.
 * {@link Tuple}과 달리 {@link java.io.Serializable}을 구현하지 않는다.
 *
 * @param <T1>	첫 번째 원소의 타입.
 * @param <T2>	두 번째 원소의 타입.
 * @param <T3>	세 번째 원소의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public final class Tuple3<T1,T2,T3> implements ITuple3<T1,T2,T3> {
	/**
	 * 첫 번째 원소. {@code null}일 수 있다.
	 */
	public final T1 _1;
	/**
	 * 두 번째 원소. {@code null}일 수 있다.
	 */
	public final T2 _2;
	/**
	 * 세 번째 원소. {@code null}일 수 있다.
	 */
	public final T3 _3;
	
	/**
	 * 세 값으로 {@link Tuple3}을 만든다.
	 *
	 * @param <T1>	첫 번째 원소의 타입.
	 * @param <T2>	두 번째 원소의 타입.
	 * @param <T3>	세 번째 원소의 타입.
	 * @param t1	첫 번째 원소. {@code null}을 허용한다.
	 * @param t2	두 번째 원소. {@code null}을 허용한다.
	 * @param t3	세 번째 원소. {@code null}을 허용한다.
	 * @return	새 {@link Tuple3} 객체.
	 */
	public static <T1,T2,T3> Tuple3<T1,T2,T3> of(T1 t1, T2 t2, T3 t3) {
		return new Tuple3<>(t1, t2, t3);
	}
	
	private Tuple3(T1 t1, T2 t2, T3 t3) {
		_1 = t1;
		_2 = t2;
		_3 = t3;
	}
	
	@Override
	public T1 _1() {
		return _1;
	}

	@Override
	public T2 _2() {
		return _2;
	}

	@Override
	public T3 _3() {
		return _3;
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || obj.getClass() != Tuple3.class ) {
			return false;
		}
		
		@SuppressWarnings("unchecked")
		Tuple3<T1,T2,T3> other = (Tuple3<T1,T2,T3>)obj;
		return Objects.equals(_1, other._1) && Objects.equals(_2, other._2)
				&& Objects.equals(_3, other._3);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(_1, _2, _3);
	}
	
	@Override
	public String toString() {
		return String.format("(%s, %s, %s)", _1, _2, _3);
	}
}
