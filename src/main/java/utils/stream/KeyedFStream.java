package utils.stream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface KeyedFStream<K,T> extends FStream<T> {
	public K getKey();
}
