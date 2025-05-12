package utils;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface ITuple<T1,T2> {
	public T1 _1();
	public T2 _2();
	
	public default T1 left() {
		return _1();
	}
	
	public default T2 right() {
		return _2();
	}
	
	public ITuple<T2,T1> swap();
}
