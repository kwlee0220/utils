package utils;

import java.util.Objects;

/**
 * 네 개의 값을 묶는 불변(immutable) 튜플. 각 원소는 {@code null}일 수 있다.
 * <p>
 * {@link #equals(Object)}는 네 원소를 {@link java.util.Objects#equals(Object, Object)}로 비교한다.
 *
 * @param <T1>	첫 번째 원소의 타입.
 * @param <T2>	두 번째 원소의 타입.
 * @param <T3>	세 번째 원소의 타입.
 * @param <T4>	네 번째 원소의 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Tuple4<T1,T2,T3,T4> {
	/**
	 * 첫 번째 원소.
	 */
	public final T1 _1;
	/**
	 * 두 번째 원소.
	 */
	public final T2 _2;
	/**
	 * 세 번째 원소.
	 */
	public final T3 _3;
	/**
	 * 네 번째 원소.
	 */
	public final T4 _4;
	
	/**
	 * 주어진 네 값으로 튜플을 생성한다. 각 값은 {@code null}이어도 된다.
	 *
	 * @param <T1>	첫 번째 원소의 타입.
	 * @param <T2>	두 번째 원소의 타입.
	 * @param <T3>	세 번째 원소의 타입.
	 * @param <T4>	네 번째 원소의 타입.
	 * @param t1	첫 번째 원소.
	 * @param t2	두 번째 원소.
	 * @param t3	세 번째 원소.
	 * @param t4	네 번째 원소.
	 * @return	생성된 튜플.
	 */
	public static <T1,T2,T3,T4> Tuple4<T1,T2,T3,T4> of(T1 t1, T2 t2, T3 t3, T4 t4) {
		return new Tuple4<>(t1, t2, t3, t4);
	}
	
	Tuple4(T1 t1, T2 t2, T3 t3, T4 t4) {
		_1 = t1;
		_2 = t2;
		_3 = t3;
		_4 = t4;
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || obj.getClass() != Tuple4.class ) {
			return false;
		}
		
		@SuppressWarnings("unchecked")
		Tuple4<T1,T2,T3,T4> other = (Tuple4<T1,T2,T3,T4>)obj;
		return Objects.equals(_1, other._1) && Objects.equals(_2, other._2)
				&& Objects.equals(_3, other._3) && Objects.equals(_4, other._4);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(_1, _2, _3, _4);
	}
	
	@Override
	public String toString() {
		return String.format("(%s, %s, %s, %s)", _1, _2, _3, _4);
	}
}
