package utils;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;

import lombok.experimental.Delegate;

/**
 * {@link Keyed}를 구현한 여러 래퍼 클래스(키, 키가 붙은 리스트·맵 항목·튜플)를 모아 둔 정적 유틸리티.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Keyeds {
	private Keyeds() {
		throw new AssertionError("Should not be called: class=" + Keyeds.class);
	}
	
	/**
	 * 키 값 하나만 가지는 {@link Keyed} 구현.
	 *
	 * @param <K>	키 값의 타입
	 */
	public static class Key<K> implements Keyed<K> {
		private final K m_key;
		
		/**
		 * 주어진 키 값으로 {@link Key} 객체를 생성한다. {@code null} 키도 그대로 받는다.
		 *
		 * @param <K>	키 값의 타입
		 * @param key	키 값
		 * @return		생성된 {@link Key} 객체.
		 */
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
		 * 빈 리스트는 {@link List#of()}이므로 요소를 추가할 수 없다.
		 * 
		 * @param <K>	키 값의 타입
		 * @param <T>	리스트에 포함된 요소의 타입
		 * @param key	키 값
		 * @throws IllegalArgumentException	{@code key}가 {@code null}인 경우.
		 * @return		생성된 {@link KeyedList} 객체.
		 */
		public static <K, T> KeyedList<K, T> emptyListOfKey(K key) {
			return new KeyedList<>(key, List.of());
		}
		
		/**
		 * 주어진 키 값과 리스트로 구성된 {@link KeyedList} 객체를 생성한다.
		 * 리스트를 복사하지 않고 그대로 감싸므로, 리스트 연산은 모두 {@code list}에 위임된다.
		 * 
		 * @param <K>	키 값의 타입
		 * @param <T>	리스트에 포함된 요소의 타입
		 * @param key	키 값
		 * @param list	리스트
		 * @return		생성된 {@link KeyedList} 객체.
		 * @throws IllegalArgumentException	{@code key} 또는 {@code list}가 {@code null}인 경우.
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
	
	/**
	 * {@link Map.Entry}를 감싸 그 키를 {@link Keyed#key()}로 노출하는 클래스.
	 * {@link Map.Entry}의 메소드는 모두 감싼 항목에 위임된다.
	 *
	 * @param <K>	키의 타입
	 * @param <V>	값의 타입
	 */
	public static class KeyedMapEntry<K,V> implements Keyed<K>, Map.Entry<K,V> {
		@Delegate private final Map.Entry<K,V> m_entry;
		
		/**
		 * 주어진 맵 항목을 감싼 {@link KeyedMapEntry} 객체를 생성한다.
		 *
		 * @param <K>	키의 타입
		 * @param <V>	값의 타입
		 * @param entry	감쌀 맵 항목
		 * @return		생성된 {@link KeyedMapEntry} 객체.
		 * @throws IllegalArgumentException	{@code entry}가 {@code null}인 경우.
		 */
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
	
	/**
	 * 키 값이 부가된 2-튜플 클래스. {@link ITuple}의 메소드는 모두 감싼 튜플에 위임된다.
	 *
	 * @param <K>	키 값의 타입
	 * @param <TL>	첫 번째 요소의 타입
	 * @param <TR>	두 번째 요소의 타입
	 */
	public static class KeyedTuple<K,TL extends Keyed<K>,TR extends Keyed<K>> implements Keyed<K>, ITuple<TL,TR> {
    	private final K m_key;
    	@Delegate private final ITuple<TL,TR> m_tuple;
    	
		/**
		 * 주어진 키 값과 튜플로 구성된 {@link KeyedTuple} 객체를 생성한다.
		 * 키 값이 튜플 요소들의 키와 같은지는 검사하지 않는다.
		 *
		 * @param <K>	키 값의 타입
		 * @param <TL>	첫 번째 요소의 타입
		 * @param <TR>	두 번째 요소의 타입
		 * @param key	키 값
		 * @param tuple	튜플
		 * @return		생성된 {@link KeyedTuple} 객체.
		 * @throws IllegalArgumentException	{@code key} 또는 {@code tuple}이 {@code null}인 경우.
		 */
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
	
	/**
	 * 키 값이 부가된 3-튜플 클래스. {@link ITuple3}의 메소드는 모두 감싼 튜플에 위임된다.
	 *
	 * @param <K>	키 값의 타입
	 * @param <T1>	첫 번째 요소의 타입
	 * @param <T2>	두 번째 요소의 타입
	 * @param <T3>	세 번째 요소의 타입
	 */
	public static class KeyedTuple3<K,T1 extends Keyed<K>,T2 extends Keyed<K>, T3 extends Keyed<K>>
																	implements Keyed<K>, ITuple3<T1,T2,T3> {
    	private final K m_key;
    	@Delegate private final ITuple3<T1,T2,T3> m_tuple;
    	
		/**
		 * 주어진 키 값과 튜플로 구성된 {@link KeyedTuple3} 객체를 생성한다.
		 * 키 값이 튜플 요소들의 키와 같은지는 검사하지 않는다.
		 *
		 * @param <K>	키 값의 타입
		 * @param <T1>	첫 번째 요소의 타입
		 * @param <T2>	두 번째 요소의 타입
		 * @param <T3>	세 번째 요소의 타입
		 * @param key	키 값
		 * @param tuple	튜플
		 * @return		생성된 {@link KeyedTuple3} 객체.
		 * @throws IllegalArgumentException	{@code key} 또는 {@code tuple}이 {@code null}인 경우.
		 */
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
		
		/**
		 * 감싸고 있는 원래 튜플을 반환한다.
		 *
		 * @return	감싼 튜플.
		 */
		public ITuple3<T1, T2, T3> downcast() {
			return m_tuple;
		}
	}
	
	/**
	 * {@link KeyedTuple}에 세 번째 요소를 덧붙여 같은 키의 {@link KeyedTuple3}를 만든다.
	 *
	 * @param <K>	키 값의 타입
	 * @param <T1>	첫 번째 요소의 타입
	 * @param <T2>	두 번째 요소의 타입
	 * @param <T3>	세 번째 요소의 타입
	 * @param kt	원래 키 튜플
	 * @param v3	덧붙일 세 번째 요소
	 * @return		생성된 {@link KeyedTuple3} 객체.
	 * @throws IllegalArgumentException	{@code kt} 또는 {@code v3}가 {@code null}인 경우.
	 */
	public static <K, T1 extends Keyed<K>, T2 extends Keyed<K>, T3 extends Keyed<K>>
	KeyedTuple3<K, T1, T2, T3> extend(KeyedTuple<K,T1,T2> kt, T3 v3) {
		Preconditions.checkArgument(kt != null, "KeyedTuple is null");
		Preconditions.checkArgument(v3 != null, "v3 is null");
		
		return KeyedTuple3.of(kt.key(), Tuple.of(kt._1(), kt._2(), v3));
	}
}
