package utils;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;

import lombok.experimental.Delegate;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Keyeds {
	private Keyeds() {
		throw new AssertionError("Should not be called: class=" + Keyeds.class);
	}
	
	public static class Key<K> implements Keyed<K> {
		private final K m_key;
		
		public static <K> Key<K> of(K key) {
			return new Key<>(key);
		}
		
		private Key(K key) {
			m_key = key;
		}
		
		@Override
		public K key() {
			return m_key;
		}
		
		@Override
		public String toString() {
			return String.format("Key=%s", m_key);
		}
	}
	
	/**
	 * 키 값이 부가된 리스트 클래스를 정의한다.
	 *
	 * @author Kang-Woo Lee (ETRI)
	 * @param <K>	키 값의 타입
	 * @param <T>	리스트에 포함된 요소의 타입
	 */
	public static class KeyedList<K,T> implements Keyed<K>, List<T> {
		private final K m_key;
		@Delegate private final List<T> m_list;
		
		/**
		 * 주어진 키 값과 빈 리스트로 구성된 {@link KeyedList} 객체를 생성한다.
		 * 
		 * @param key	키 값
		 * @return		생성된 {@link KeyedList} 객체.
		 */
		public static <K, T> KeyedList<K, T> emptyListOfKey(K key) {
			return new KeyedList<>(key, List.of());
		}
		
		/**
		 * 주어진 키 값과 리스트로 구성된 {@link KeyedList} 객체를 생성한다.
		 * 
		 * @param key	키 값
		 * @param list	리스트
		 * @return		생성된 {@link KeyedList} 객체.
		 */
		public static <K,T> KeyedList<K,T> of(K key, List<T> list) {
			return new KeyedList<>(key, list);
		}
		
		private KeyedList(K key, List<T> list) {
			Preconditions.checkArgument(key != null, "key is null");
			Preconditions.checkArgument(list != null, "list is null");
			
			m_key = key;
			m_list = list;
		}
		
		@Override
		public K key() {
			return m_key;
		}
		
		@Override
		public String toString() {
			return String.format("KeyedList(%s, %s)", m_key, ""+m_list);
		}
	}
	
	public static class KeyedMapEntry<K,V> implements Keyed<K>, Map.Entry<K,V> {
		@Delegate private final Map.Entry<K,V> m_entry;
		
		public static <K, V> KeyedMapEntry<K, V> of(Map.Entry<K, V> entry) {
			return new KeyedMapEntry<>(entry);
		}
		
		private KeyedMapEntry(Map.Entry<K, V> entry) {
			Preconditions.checkArgument(entry != null, "Map.Entry is null");
			
			m_entry = entry;
		}
		
		@Override
		public K key() {
			return m_entry.getKey();
		}
	}
	
	public static class KeyedTuple<K,TL extends Keyed<K>,TR extends Keyed<K>> implements Keyed<K>, ITuple<TL,TR> {
    	private final K m_key;
    	@Delegate private final ITuple<TL,TR> m_tuple;
    	
		public static <K, TL extends Keyed<K>, TR extends Keyed<K>>
		KeyedTuple<K, TL, TR> of(K key, ITuple<TL,TR> tuple) {
			return new KeyedTuple<>(key, tuple);
		}
    	
    	private KeyedTuple(K key, ITuple<TL,TR> tuple) {
    		Preconditions.checkArgument(key != null, "key is null");
    		Preconditions.checkArgument(tuple != null, "tuple is null");
    		
    		m_key = key;
    		m_tuple = tuple;
    	}

		@Override
		public K key() {
			return m_key;
		}
	}
	
	public static class KeyedTuple3<K,T1 extends Keyed<K>,T2 extends Keyed<K>, T3 extends Keyed<K>>
																	implements Keyed<K>, ITuple3<T1,T2,T3> {
    	private final K m_key;
    	@Delegate private final ITuple3<T1,T2,T3> m_tuple;
    	
		public static <K, T1 extends Keyed<K>, T2 extends Keyed<K>, T3 extends Keyed<K>>
		KeyedTuple3<K, T1, T2, T3> of(K key, ITuple3<T1,T2,T3> tuple) {
			return new KeyedTuple3<>(key, tuple);
		}
    	
    	private KeyedTuple3(K key, ITuple3<T1,T2,T3> tuple) {
    		Preconditions.checkArgument(key != null, "key is null");
    		Preconditions.checkArgument(tuple != null, "tuple is null");
    		
    		m_key = key;
    		m_tuple = tuple;
    	}

		@Override
		public K key() {
			return m_key;
		}
		
		public ITuple3<T1, T2, T3> downcast() {
			return m_tuple;
		}
	}
	
	public static <K, T1 extends Keyed<K>, T2 extends Keyed<K>, T3 extends Keyed<K>>
	KeyedTuple3<K, T1, T2, T3> extend(KeyedTuple<K,T1,T2> kt, T3 v3) {
		Preconditions.checkArgument(kt != null, "KeyedTuple is null");
		Preconditions.checkArgument(v3 != null, "v3 is null");
		
		return KeyedTuple3.of(kt.key(), Tuple.of(kt._1(), kt._2(), v3));
	}
}
