package utils.func;

import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Tuple4<T1,T2,T3,T4> {
	public final T1 _1;
	public final T2 _2;
	public final T3 _3;
	public final T4 _4;
	
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
