package utils;

import java.io.Serializable;
import java.util.Objects;

/**
 * 두 값을 묶은 2-튜플.
 * <p>
 * 두 원소는 {@code public final} 필드이므로 생성 후 다른 객체로 바꿀 수 없다(원소 객체 자체의
 * 불변성은 보장하지 않는다). 원소는 {@code null}일 수 있다. {@link #equals(Object)}는 클래스가
 * 정확히 {@code Tuple}인 객체에 대해서만 두 원소를 {@link Objects#equals(Object, Object)}로 비교한다.
 *
 * @param <T1>	첫 번째 원소의 타입.
 * @param <T2>	두 번째 원소의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public final class Tuple<T1,T2> implements Serializable, ITuple<T1,T2> {
	private static final long serialVersionUID = 1L;
	
	/**
	 * 첫 번째 원소. {@code null}일 수 있다.
	 */
	public final T1 _1;
	/**
	 * 두 번째 원소. {@code null}일 수 있다.
	 */
	public final T2 _2;
	
	/**
	 * 두 값으로 {@link Tuple}을 만든다.
	 *
	 * @param <T1>	첫 번째 원소의 타입.
	 * @param <T2>	두 번째 원소의 타입.
	 * @param t1	첫 번째 원소. {@code null}을 허용한다.
	 * @param t2	두 번째 원소. {@code null}을 허용한다.
	 * @return	새 {@link Tuple} 객체.
	 */
	public static <T1,T2> Tuple<T1,T2> of(T1 t1, T2 t2) {
		return new Tuple<>(t1, t2);
	}
	
	/**
	 * 세 값으로 {@link Tuple3}을 만든다. {@link Tuple3#of(Object, Object, Object)}와 같다.
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
		return Tuple3.of(t1, t2, t3);
	}
	
	/**
	 * 네 값으로 {@link Tuple4}를 만든다.
	 *
	 * @param <T1>	첫 번째 원소의 타입.
	 * @param <T2>	두 번째 원소의 타입.
	 * @param <T3>	세 번째 원소의 타입.
	 * @param <T4>	네 번째 원소의 타입.
	 * @param t1	첫 번째 원소.
	 * @param t2	두 번째 원소.
	 * @param t3	세 번째 원소.
	 * @param t4	네 번째 원소.
	 * @return	새 {@link Tuple4} 객체.
	 */
	public static <T1,T2,T3,T4> Tuple4<T1,T2,T3,T4> of(T1 t1, T2 t2, T3 t3, T4 t4) {
		return new Tuple4<>(t1, t2, t3, t4);
	}
	
	private Tuple(T1 t1, T2 t2) {
		_1 = t1;
		_2 = t2;
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
	public Tuple<T2,T1> swap() {
		return Tuple.of(_2, _1);
	}
	
	/**
	 * 첫 번째 원소를 키로, 두 번째 원소를 값으로 하는 {@link KeyValue}로 변환한다.
	 *
	 * @return	변환된 {@link KeyValue} 객체.
	 * @throws IllegalArgumentException	첫 번째 원소({@link #_1})가 {@code null}인 경우.
	 */
	public KeyValue<T1,T2> toKeyValue() {
		return KeyValue.of(_1, _2);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || obj.getClass() != Tuple.class ) {
			return false;
		}
		
		@SuppressWarnings("unchecked")
		Tuple<T1,T2> other = (Tuple<T1,T2>)obj;
		return Objects.equals(_1, other._1) && Objects.equals(_2, other._2);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(_1, _2);
	}

	@Override
	public String toString() {
		return String.format("(%s, %s)", _1, _2);
	}
}
