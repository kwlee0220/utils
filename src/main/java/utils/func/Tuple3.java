package utils.func;

import java.util.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Tuple3<T1,T2,T3> {
	public final T1 _1;
	public final T2 _2;
	public final T3 _3;
	
	public static <T1,T2,T3> Tuple3<T1,T2,T3> of(T1 t1, T2 t2, T3 t3) {
		return new Tuple3<>(t1, t2, t3);
	}
	
	Tuple3(T1 t1, T2 t2, T3 t3) {
		_1 = t1;
		_2 = t2;
		_3 = t3;
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
