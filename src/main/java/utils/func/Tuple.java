package utils.func;

import java.io.Serializable;
import java.util.Objects;

import utils.KeyValue;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class Tuple<T1,T2> implements Serializable, Comparable<Tuple<T1,T2>> {
	private static final long serialVersionUID = 1L;
	
	public final T1 _1;
	public final T2 _2;
	
	public static <T1,T2> Tuple<T1,T2> of(T1 t1, T2 t2) {
		return new Tuple<>(t1, t2);
	}
	
	public static <T1,T2,T3> Tuple3<T1,T2,T3> of(T1 t1, T2 t2, T3 t3) {
		return new Tuple3<>(t1, t2, t3);
	}
	
	public static <T1,T2,T3,T4> Tuple4<T1,T2,T3,T4> of(T1 t1, T2 t2, T3 t3, T4 t4) {
		return new Tuple4<>(t1, t2, t3, t4);
	}
	
	private Tuple(T1 t1, T2 t2) {
		_1 = t1;
		_2 = t2;
	}
	
	public T1 _1() {
		return _1;
	}
	
	public T2 _2() {
		return _2;
	}
	
	public T1 left() {
		return _1;
	}
	
	public T2 right() {
		return _2;
	}
	
	public Tuple<T2,T1> swap() {
		return Tuple.of(_2, _1);
	}
	
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

	@SuppressWarnings("unchecked")
	@Override
	public int compareTo(Tuple<T1, T2> other) {
		int cmp = ((Comparable<T1>)_1).compareTo(other._1);
		if ( cmp == 0 ) {
			return ((Comparable<T2>)_2).compareTo(other._2);
		}
		else {
			return cmp;
		}
	}
	
	@Override
	public String toString() {
		return String.format("(%s, %s)", _1, _2);
	}
}
