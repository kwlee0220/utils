package utils.func;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import utils.Indexed;
import utils.KeyValue;
import utils.Preconditions;
import utils.Tuple;
import utils.stream.FStream;

/**
 * 컬렉션과 맵을 대상으로 자주 사용하는 탐색, 변환, 치환, 조인 유틸리티를 제공한다.
 * <p>
 * 대부분의 메서드는 입력 컬렉션을 순회하여 새 컬렉션을 생성하거나,
 * 일부 메서드는 입력 컬렉션 자체를 수정한 뒤 결과를 반환한다.
 * 수정 여부와 {@code null} 처리 방식은 각 메서드의 Javadoc을 따른다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Funcs {
	private Funcs() {
		throw new AssertionError("Should not be called: class=" + Funcs.class);
	}
	
	/**
	 * 주어진 목록들 중에서 조건을 만족하는 임의의 한 목록을 반환한다.
	 * 목록들 중에서 조건을 만족하는 것이 여러개인 경우에는 임의로 하나만 선택된다.
	 * <p>
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param <T>
	 * @param coll	검색 대상 목록들.
	 * @param pred	검색 조건
	 * @return	조건을 만족하는 목록. 만일 조건을 만족하는 것이 없는 경우에는
	 * 			{@code null}이 반환된다.
	 */
	public static <T> T findFirst(Iterable<T> coll, Predicate<? super T> pred) {
		for ( T v : coll ) {
			if ( pred.test(v) ) {
				return v;
			}
		}
		return null;
	}
	
	/**
	 * 주어진 목록들 중에서 조건을 만족하는 임의의 한 목록을 반환한다.
	 * 목록들 중에서 조건을 만족하는 것이 여러개인 경우에는 임의로 하나만 선택된다.
	 * <p>
	 * 조건을 만족하는 목록이 없는 경우에는 {@code defaultVal}이 반환된다.
	 * 
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 * 
	 * @param <T>
	 * @param coll	검색 대상 목록들.
	 * @param pred	검색 조건
	 * @param defaultVal	조건을 만족하는 목록이 없는 경우 반환할 기본값.
	 * @return	조건을 만족하는 목록. 만일 조건을 만족하는 것이 없는 경우에는
	 *             {@code defaultVal}이 반환된다.
	 */
	public static <T> T findFirst(Iterable<T> coll, Predicate<? super T> pred, T defaultVal) {
		for ( T v : coll ) {
			if ( pred.test(v) ) {
				return v;
			}
		}
		return defaultVal;
	}

	/**
	 * 주어진 리스트에 포함된 목록들 중에서 조건을 만족하는 임의의 한 목록을 반환한다. 
	 * 목록들 중에서 조건을 만족하는 것이 여러개인 경우에는 가장 처음으로 조건을 만족하는 목록이 선택된다.
	 * <p>
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param <T>
	 * @param list	검색 대상 목록들.
	 * @param pred	검색 조건
	 * @return	조건을 만족하는 목록. 만일 조건을 만족하는 것이 없는 경우에는
	 * 			{@code null}이 반환된다.
	 */
	public static <T> Indexed<T> findFirstIndexed(List<T> list, Predicate<? super T> pred) {
		int idx = 0;
		for ( T v : list ) {
			if ( pred.test(v) ) {
				return Indexed.with(v, idx);
			}
			++idx;
		}
		return null;
	}

	/**
	 * 주어진 목록들 중에서 첫번재 목록을 반환한다.
	 * <p>
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param <T>
	 * @param iterable	목록 리스트.
	 * @return	첫번째 목록. 리스트가 빈 경우에는 {@code null}이 반환된다.
	 */
	public static <T> T getFirst(Iterable<T> iterable) {
		Preconditions.checkNotNullArgument(iterable, "iterable was null");
		
		Iterator<T> iter = iterable.iterator();
		return iter.hasNext() ? iter.next() : null;
	}

	/**
	 * 주어진 반복자에서 첫 번째 원소를 반환한다.
	 * <p>
	 * 반복자에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param <T> 반복자 원소 타입.
	 * @param iter 대상 반복자.
	 * @return 첫 번째 원소. 반복자가 비어 있으면 {@code null}.
	 */
	public static <T> T getFirst(Iterator<T> iter) {
		Preconditions.checkNotNullArgument(iter, "iter was null");
		
		return iter.hasNext() ? iter.next() : null;
	}

	/**
	 * 주어진 목록들 중에서 마지막 목록을 반환한다.
	 * <p>
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param <T>	목록의 원소 타입.
	 * @param list	목록 리스트.
	 * @return	마지막 목록. 리스트가 빈 경우에는 {@code null}이 반환된다.
	 */
	public static <T> T getLast(List<T> list) {
		Preconditions.checkNotNullArgument(list, "list was null");

		if ( list.isEmpty() ) {
			return null;
		}
		return list.get(list.size()-1);
	}
	
	/**
	 * 주어진 목록에서 주어진 조건을 만족하는 목록의 존재 여부를 반환한다.
	 *
	 * @param <T>	목록의 원소 타입.
	 * @param iterable	검색 대상 목록들.
	 * @param pred		검색 조건
	 * @return	존재하는 경우에는 {@code true}, 그렇지 않은 경우에는 {@code false}.
	 */
	public static <T> boolean exists(Iterable<T> iterable, Predicate<? super T> pred) {
		for ( T v : iterable ) {
			if ( pred.test(v) ) {
				return true;
			}
		}
		return false;
	}
	/**
	 * 주어진 반복자에서 조건을 만족하는 원소가 하나라도 존재하는지 여부를 반환한다.
	 * <p>
	 * 검사 과정에서 반복자는 소비된다.
	 *
	 * @param <T> 반복자 원소 타입.
	 * @param iter 검색 대상 반복자.
	 * @param pred 검색 조건.
	 * @return 조건을 만족하는 원소가 존재하면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public static <T> boolean exists(Iterator<T> iter, Predicate<? super T> pred) {
		while ( iter.hasNext() ) {
			if ( pred.test(iter.next()) ) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 주어진 목록에서 모든 목록이 주어진 조건을 만족하는지 여부를 반환한다.
	 *
	 * @param <T>	목록의 원소 타입.
	 * @param iterable 검색 대상 목록들.
	 * @param pred     검색 조건
	 * @return 모든 목록이 조건을 만족하는 경우에는 {@code true}, 그렇지 않은 경우에는 {@code false}.
	 */
	public static <T> boolean all(Iterable<T> iterable, Predicate<? super T> pred) {
		for ( T v : iterable ) {
			if ( !pred.test(v) ) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * 주어진 iterable에서 {@link Slice} 조건에 해당하는 구간만 잘라 리스트로 반환한다.
	 *
	 * @param <T> 원소 타입.
	 * @param iterable 대상 iterable.
	 * @param slice 추출할 구간 정보.
	 * @return 잘라낸 원소들의 리스트.
	 */
	public static <T> List<T> slice(List<T> iterable, Slice slice) {
		return FStream.from(iterable).slice(slice).toList();
	}
	
	/**
	 * 주어진 목록들에 대해 주어진 조건을 만족하는 element로 구성된 목록들을 반환한다.
	 *
	 * @param <T>  목록의 원소 타입.
	 * @param list Iterable 객체.
	 * @param pred 목록의 필터링을 수행할 Predicate 객체.
	 * @return 필터링된 목록들의 리스트.
	 */
	public static <T> List<T> filter(Iterable<T> list, Predicate<? super T> pred) {
		List<T> mapped = new ArrayList<>();
		for ( T item : list ) {
			if ( pred.test(item) ) {
				mapped.add(item);
			}
		}
		return mapped;
	}
	
	/**
	 * 주어진 집합들에 대해 주어진 조건을 만족하는 element로 구성된 집합들을 반환한다.
	 * <p>
	 * 결과는 {@link java.util.HashSet}이며, 입력이 {@link java.util.LinkedHashSet} /
	 * {@link java.util.SortedSet} 등 순서/정렬을 보장하는 집합이어도 그 보장은 유지되지 않는다.
	 *
	 * @param <T>  집합의 원소 타입.
	 * @param list Set 객체.
	 * @param pred 집합의 필터링을 수행할 Predicate 객체.
	 * @return 필터링된 집합들의 집합.
	 */
	public static <T> Set<T> filter(Set<T> list, Predicate<? super T> pred) {
		return FStream.from(list).filter(pred).toSet();
	}
	
	/**
	 * 주어진 목록들에 대해 주어진 매퍼 함수를 적용한 결과 목록들을 반환한다.
	 *
	 * @param <T>	입력 목록의 원소 타입.
	 * @param <S>	출력 목록의 원소 타입.
	 * @param list   Iterable 객체.
	 * @param mapper 목록의 매핑을 수행할 Function 객체.
	 * @return 매핑된 목록들의 리스트.
	 */
	public static <T,S> List<S> map(Iterable<T> list, Function<? super T, ? extends S> mapper) {
		List<S> mapped = new ArrayList<>();
		for ( T item : list ) {
			mapped.add(mapper.apply(item));
		}
		return mapped;
	}

	/**
	 * 주어진 집합들에 대해 주어진 매퍼 함수를 적용한 결과 집합들을 반환한다.
	 *
	 * @param <T>    입력 집합의 원소 타입.
	 * @param <S>    출력 집합의 원소 타입.
	 * @param set    Set 객체.
	 * @param mapper 집합의 매핑을 수행할 Function 객체.
	 * @return 매핑된 집합들의 집합.
	 */
	public static <T,S> Set<S> map(Set<T> set, Function<? super T, ? extends S> mapper) {
		Set<S> result = new HashSet<>();
		for ( T item : set ) {
			result.add(mapper.apply(item));
		}
		return result;
	}

	/**
	 * 주어진 목록들 중에서 주어진 조건을 만족하는 첫번째 element를 주어진 목록으로 대체시킨다.
	 * <p>
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param <T>
	 * @param list		List 객체.
	 * @param pred		목록의 대체 여부를 판단할 {@link Predicate} 객체.
	 * @param newValueGenerator	대체할 새 객체를 제공할 Supplier 객체
	 * @return	대체된 원소의 이전 값. 대체된 원소가 없었던 경우에는 {@code null}이 반환됨.
	 * @throws IllegalArgumentException {@code list}, {@code pred}, {@code newValueGenerator} 중 하나라도
	 *                                  {@code null}인 경우.
	 */
	public static <T> T replaceFirst(List<T> list, Predicate<? super T> pred,
									Function<? super T, ? extends T> newValueGenerator) {
		Preconditions.checkNotNullArgument(list, "list was null");
		Preconditions.checkNotNullArgument(pred, "Predicate was null");
		Preconditions.checkNotNullArgument(newValueGenerator, "New value generator was null");
		
		for ( int i =0; i < list.size(); ++i ) {
			T v = list.get(i);
			if ( pred.test(v) ) {
				T newVal = newValueGenerator.apply(v);
				list.set(i, newVal);
				return v;
			}
		}
		
		return null;
	}

	/**
	 * 주어진 목록들 중에서 주어진 조건을 만족하는 첫번째 element를 주어진 목록으로 대체시킨다.
	 * <p>
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param <T>
	 * @param list		Iterable 객체.
	 * @param pred		목록의 대체 여부를 판단할 {@link Predicate} 객체.
	 * @param newVal	대체할 새 객체.
	 * @return	대체된 원소의 이전 값. 대체된 원소가 없었던 경우에는 {@code null}이 반환됨.
	 * @throws IllegalArgumentException {@code list} 또는 {@code pred}가 {@code null}인 경우.
	 */
	public static <T> T replaceFirst(List<T> list, Predicate<? super T> pred, T newVal) {
		Preconditions.checkNotNullArgument(list, "list was null");
		Preconditions.checkNotNullArgument(pred, "Predicate was null");

		for ( int i =0; i < list.size(); ++i ) {
			T v = list.get(i);
			if ( pred.test(v) ) {
				list.set(i, newVal);
				return v;
			}
		}

		return null;
	}
	
	/**
	 * 목록에 포함된 원소들 중에서 주어진 조건 (pred)를 만족하는 첫번째 원소를 주어진
	 * 새 원소를 대체시킨다. 만일 조건을 만족하는 원소가 없는 경우는 새 원소를 추가시킨다.
	 * <p>
	 * 새 원소는 조건을 만족하는 원소를 인자로 하는 {@link Function}의 결과 값을 통해 생성된다.
	 * 만일 조건을 만족하는 원소가 없는 경우에는 {@code null}을 인자로하는 {@link Function}의
	 * 결과 값을 사용한다.
	 * <p>
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 * 
	 * @param <T>
	 * @param list	목록 객체.
	 * @param pred	대체 대상을 판단할 {@link Predicate} 객체.
	 * @param newValueGenerator	대체(추가)할 새 값을 생성하기 위한 함수 객체.
	 * @return	대체된 원소의 이전 값. 대체된 원소가 없어 값이 추가된 경우에는 {@code null}이 반환됨.
	 */
	public static <T> T replaceOrInsertFirst(List<T> list, Predicate<? super T> pred,
											Function<? super T, ? extends T> newValueGenerator) {
		Preconditions.checkNotNullArgument(list, "list was null");
		Preconditions.checkNotNullArgument(pred, "Predicate was null");
		Preconditions.checkNotNullArgument(newValueGenerator, "New value generator was null");

		T old = replaceFirst(list, pred, newValueGenerator);
		if ( old == null ) {
			list.add(newValueGenerator.apply(null));
		}
		
		return old;
	}
	
	/**
	 * 주어진 iterable에서 첫 번째 원소를 제거하고 반환한다.
	 * <p>
	 * iterable이 비어 있으면 {@code null}을 반환한다.
	 * 반복자가 {@link Iterator#remove()}를 지원하지 않으면 예외가 발생할 수 있다.
	 * <p>
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param <T> 원소 타입.
	 * @param iterable 대상 iterable.
	 * @return 제거된 첫 번째 원소. 비어 있으면 {@code null}.
	 */
	public static <T> T removeFirst(Iterable<T> iterable) {
		Iterator<T> iter = iterable.iterator();
		if ( iter.hasNext() ) {
			T removed = iter.next();
			iter.remove();
			return removed;
		}
		else {
			return null;
		}
	}
	
	/**
	 * 주어진 목록들 중에서 주어진 조건을 만족하는 첫번째 element를 삭제한다.
	 * 만일 목록 내에 같은 조건의 목록이 여러개 있는 경우에는 그 중 하나의 목록만 삭제된다.
	 * <p>
	 * 목록에 null이 포함되지 않는 것을 가정하고, 그렇지 않은 경우에는
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param <T>
	 * @param iterable	Collection 객체.
	 * @param pred	Element의 삭제 여부를 판달할 Predicate 객체.
	 * @return	삭제가 성공한 경우는 삭제된 element, 그렇지 않은 경우는 {@code null}.
	 */
	public static <T> T removeFirstIf(Iterable<T> iterable, Predicate<? super T> pred) {
		Iterator<T> iter = iterable.iterator();
		while ( iter.hasNext() ) {
			T elm = iter.next();
			if ( pred.test(elm) ) {
				iter.remove();
				return elm;
			}
		}
		
		return null;
	}
	
	/**
	 * 주어진 iterable에서 조건을 만족하는 모든 원소를 제거하고, 제거된 원소들을 반환한다.
	 * <p>
	 * iterable의 {@link Iterator#remove()}가 지원되지 않으면 예외가 발생한다.
	 *
	 * @param <T> 원소 타입.
	 * @param values 대상 iterable. 호출 후 제거된 원소들이 빠진 상태가 된다.
	 * @param pred 제거 조건.
	 * @return 제거된 원소들의 리스트(원본 순서). 제거된 게 없으면 빈 리스트.
	 */
	public static <T> List<T> removeIf(Iterable<T> values, Predicate<? super T> pred) {
		List<T> removed = new ArrayList<>();
		Iterator<T> iter = values.iterator();
		while ( iter.hasNext() ) {
			T elm = iter.next();
			if ( pred.test(elm) ) {
				iter.remove();
				removed.add(elm);
			}
		}

		return removed;
	}
	
	/**
	 * 주어진 {@link Map}에 등록된 entry들 중에서 주어진 조건 (pred)를 만족하는 것들을 삭제한다.
	 * 
	 * @param <K>
	 * @param <V>
	 * @param map	삭제를 수행할 대상 Map 객체.
	 * @param pred	삭제 조건.
	 * @return		삭제된 {@link Map.Entry} 객체들.
	 */
	public static <K,V>
	List<KeyValue<K,V>> removeIf(Map<K,V> map, BiPredicate<? super K, ? super V> pred) {
		List<KeyValue<K,V>> removeds = new ArrayList<>();
		
		Iterator<Map.Entry<K, V>> iter = map.entrySet().iterator();
		while ( iter.hasNext() ) {
			Map.Entry<K, V> ent = iter.next();
			if ( pred.test(ent.getKey(), ent.getValue()) ) {
				removeds.add(KeyValue.from(ent));
				iter.remove();
			}
		}

		return removeds;
	}
	
	/**
	 * 주어진 키가 {@link Map}에 존재하는 경우에만 consumer를 호출한다.
	 * <p>
	 * 값이 {@code null}이어도 키가 존재하면 consumer가 호출되며,
	 * 이 경우 consumer의 두 번째 인자로 {@code null}이 전달된다.
	 *
	 * @param map 조회 대상 맵.
	 * @param key 조회할 키.
	 * @param consumer 키가 존재할 때 호출할 consumer.
	 */
	public static <K,V> void acceptIfPresent(Map<K,V> map, K key, BiConsumer<? super K, ? super V> consumer) {
		if ( map.containsKey(key) ) {
			consumer.accept(key, map.get(key));
		}
	}
	
	/**
	 * 두 개의 {@link Map} 객체를 내부 조인(inner join)한다.
	 * 
	 * @param <K>      키 타입
	 * @param <V1>     왼쪽 맵의 값 타입
	 * @param <V2>     오른쪽 맵의 값 타입
	 * @param leftMap  왼쪽 맵 객체
	 * @param rightMap 오른쪽 맵 객체
	 * @return 두 맵에 공통으로 존재하는 키만 포함하는 조인된 맵 객체.
	 *         오른쪽 맵의 값이 {@code null}이어도 키만 존재하면 결과에 포함된다.
	 */
	public static <K,V1,V2> Map<K,Tuple<V1,V2>> innerJoin(Map<K,V1> leftMap, Map<K,V2> rightMap) {
		Map<K, Tuple<V1, V2>> joined = new HashMap<>();
		for ( Map.Entry<K, V1> leftEnt : leftMap.entrySet() ) {
			if ( rightMap.containsKey(leftEnt.getKey()) ) {
				joined.put(leftEnt.getKey(), Tuple.of(leftEnt.getValue(), rightMap.get(leftEnt.getKey())));
			}
		}

		return joined;
	}
	
	/**
	 * 두 개의 {@link Map} 객체를 왼쪽 외부 조인(left outer join)한다.
	 * 
	 * @param <K>      키 타입
	 * @param <V1>     왼쪽 맵의 값 타입
	 * @param <V2>     오른쪽 맵의 값 타입
	 * @param leftMap  왼쪽 맵 객체
	 * @param rightMap 오른쪽 맵 객체
	 * @return 왼쪽 맵의 모든 키를 포함하는 조인된 맵 객체.
	 *         오른쪽 맵에 키가 없거나 값이 {@code null}인 경우 두 번째 원소는 {@code null}이다.
	 */
	public static <K, V1, V2> Map<K, Tuple<V1, V2>> leftOuterJoin(Map<K, V1> leftMap, Map<K, V2> rightMap) {
		Map<K, Tuple<V1, V2>> joined = new HashMap<>();
		for ( Map.Entry<K, V1> leftEnt : leftMap.entrySet() ) {
			V2 rightVal = rightMap.get(leftEnt.getKey());
			joined.put(leftEnt.getKey(), Tuple.of(leftEnt.getValue(), rightVal));
		}

		return joined;
	}
	
	/**
	 * 두 개의 {@link Map} 객체를 오른쪽 외부 조인(right outer join)한다.
	 * 
	 * @param <K>      키 타입
	 * @param <V1>     왼쪽 맵의 값 타입
	 * @param <V2>     오른쪽 맵의 값 타입
	 * @param leftMap  왼쪽 맵 객체
	 * @param rightMap 오른쪽 맵 객체
	 * @return 오른쪽 맵의 모든 키를 포함하는 조인된 맵 객체.
	 *         왼쪽 맵에 키가 없거나 값이 {@code null}인 경우 첫 번째 원소는 {@code null}이다.
	 */
	public static <K, V1, V2> Map<K, Tuple<V1, V2>> rightOuterJoin(Map<K, V1> leftMap, Map<K, V2> rightMap) {
		Map<K, Tuple<V1, V2>> joined = new HashMap<>();
		for ( Map.Entry<K, V2> rightEnt : rightMap.entrySet() ) {
			V1 leftVal = leftMap.get(rightEnt.getKey());
			joined.put(rightEnt.getKey(), Tuple.of(leftVal, rightEnt.getValue()));
		}

		return joined;
	}
	
	/**
	 * 두 개의 {@link Map} 객체를 완전 외부 조인(full outer join)한다.
	 * 
	 * @param <K>      키 타입
	 * @param <V1>     왼쪽 맵의 값 타입
	 * @param <V2>     오른쪽 맵의 값 타입
	 * @param leftMap  왼쪽 맵 객체
	 * @param rightMap 오른쪽 맵 객체
	 * @return 양쪽 맵의 모든 키를 포함하는 조인된 맵 객체.
	 *         어느 한쪽 값이 없거나 {@code null}인 경우 해당 tuple 원소는 {@code null}이다.
	 */
	public static <K, V1, V2> Map<K, Tuple<V1, V2>> fullOuterJoin(Map<K, V1> leftMap, Map<K, V2> rightMap) {
		Map<K, Tuple<V1, V2>> joined = new HashMap<>();
		for ( Map.Entry<K, V1> leftEnt : leftMap.entrySet() ) {
			V2 rightVal = rightMap.get(leftEnt.getKey());
			joined.put(leftEnt.getKey(), Tuple.of(leftEnt.getValue(), rightVal));
		}
		for ( Map.Entry<K, V2> rightEnt : rightMap.entrySet() ) {
			if ( !joined.containsKey(rightEnt.getKey()) ) {
				V1 leftVal = leftMap.get(rightEnt.getKey());
				joined.put(rightEnt.getKey(), Tuple.of(leftVal, rightEnt.getValue()));
			}
		}

		return joined;
	}
	
	/**
	 * 주어진 집합을 복사한 뒤 원소 하나를 추가한 새 집합을 반환한다.
	 *
	 * @param <T> 원소 타입.
	 * @param set 원본 집합.
	 * @param elm 추가할 원소.
	 * @return 원본 집합을 복사하고 원소를 추가한 새 집합.
	 */
	public static <T> Set<T> addCopy(Set<T> set, T elm) {
		Set<T> added = new HashSet<>(set);
		added.add(elm);
		return added;
	}
	
	/**
	 * 주어진 리스트를 복사한 뒤 원소 하나를 추가한 새 리스트를 반환한다.
	 *
	 * @param <T> 원소 타입.
	 * @param list 원본 리스트.
	 * @param elm 추가할 원소.
	 * @return 원본 리스트를 복사하고 원소를 추가한 새 리스트.
	 */
	public static <T> List<T> addCopy(List<T> list, T elm) {
		List<T> added = new ArrayList<>(list);
		added.add(elm);
		return added;
	}
	
	/**
	 * 주어진 리스트를 복사한 뒤 지정된 원소를 제거한 새 리스트를 반환한다.
	 *
	 * @param <T> 원소 타입.
	 * @param list 원본 리스트.
	 * @param elm 제거할 원소.
	 * @return 원본 리스트를 복사하고 원소를 제거한 새 리스트.
	 */
	public static <T> List<T> removeCopy(List<T> list, T elm) {
		List<T> removed = new ArrayList<>(list);
		removed.remove(elm);
		return removed;
	}
	
	/**
	 * 주어진 집합을 복사한 뒤 지정된 원소를 제거한 새 집합을 반환한다.
	 *
	 * @param <T> 원소 타입.
	 * @param set 원본 집합.
	 * @param elm 제거할 원소.
	 * @return 원본 집합을 복사하고 원소를 제거한 새 집합.
	 */
	public static <T> Set<T> removeCopy(Set<T> set, T elm) {
		Set<T> removed = new HashSet<>(set);
		removed.remove(elm);
		return removed;
	}
	
	/**
	 * 주어진 iterable에서 key가 최소인 원소를 반환한다.
	 *
	 * @param <T> 원소 타입.
	 * @param <K> 비교 key 타입.
	 * @param list 대상 iterable.
	 * @param keyer 비교 key 추출 함수.
	 * @return 최소 key를 갖는 원소. 비어 있으면 {@code null}.
	 */
	public static <T, K extends Comparable<K>>
	T min(Iterable<? extends T> list, Function<? super T,? extends K> keyer) {
		return FStream.from(list).min(keyer).orElse(null);
	}
	
	/**
	 * 주어진 iterable에서 key가 최대인 원소를 반환한다.
	 *
	 * @param <T> 원소 타입.
	 * @param <K> 비교 key 타입.
	 * @param list 대상 iterable.
	 * @param keyer 비교 key 추출 함수.
	 * @return 최대 key를 갖는 원소. 비어 있으면 {@code null}.
	 */
	public static <T, K extends Comparable<K>>
	T max(Iterable<? extends T> list, Function<? super T,? extends K> keyer) {
		return FStream.from(list).max(keyer).orElse(null);
	}
	
	/**
	 * 주어진 리스트에서 key가 최대가 되는 원소의 인덱스를 반환한다.
	 * <p>
	 * 리스트가 비어 있으면 {@code -1}을 반환한다.
	 * 동일한 최대 key가 여러 개면 가장 먼저 나온 원소의 인덱스를 반환한다.
	 * {@code keyer}는 비교 가능한 non-null key를 반환해야 한다.
	 *
	 * @param list 대상 리스트.
	 * @param keyer 비교 key 추출 함수.
	 * @return 최대 key를 갖는 원소의 인덱스. 리스트가 비어 있으면 {@code -1}.
	 */
	public static <T,K extends Comparable<K>>
	int argmax(List<? extends T> list, Function<? super T,? extends K> keyer) {
		int maxIdx = -1;
		K maxValue = null;
		for ( int i =0; i < list.size(); ++i ) {
			K key = keyer.apply(list.get(i));
			if ( maxValue == null ) {
				maxValue = key;
				maxIdx = 0;
			}
			else if ( key.compareTo(maxValue) > 0 ) {
				maxValue = key;
				maxIdx = i;
			}
		}
		return maxIdx;
	}
	
	/**
	 * 주어진 리스트에서 key가 최소가 되는 원소의 인덱스를 반환한다.
	 * <p>
	 * 리스트가 비어 있으면 {@code -1}을 반환한다.
	 * 동일한 최소 key가 여러 개면 가장 먼저 나온 원소의 인덱스를 반환한다.
	 * {@code keyer}는 비교 가능한 non-null key를 반환해야 한다.
	 *
	 * @param list 대상 리스트.
	 * @param keyer 비교 key 추출 함수.
	 * @return 최소 key를 갖는 원소의 인덱스. 리스트가 비어 있으면 {@code -1}.
	 */
	public static <T,K extends Comparable<K>>
	int argmin(List<? extends T> list, Function<? super T,? extends K> keyer) {
		int minIdx = -1;
		K minValue = null;
		for ( int i =0; i < list.size(); ++i ) {
			K key = keyer.apply(list.get(i));
			if ( minValue == null ) {
				minValue = key;
				minIdx = 0;
			}
			else if ( key.compareTo(minValue) < 0 ) {
				minValue = key;
				minIdx = i;
			}
		}
		return minIdx;
	}
	
	/**
	 * 두 컬렉션이 공통 원소를 하나라도 가지는지 여부를 반환한다.
	 *
	 * @param <T> 원소 타입.
	 * @param set1 첫 번째 컬렉션.
	 * @param set2 두 번째 컬렉션.
	 * @return 공통 원소가 하나라도 있으면 {@code true}, 없으면 {@code false}.
	 */
	public static <T> boolean intersects(Collection<? extends T> set1, Collection<? extends T> set2) {
		return FStream.from(set1).exists(v -> set2.contains(v));
	}
	
	/**
	 * 주어진 컬렉션들을 순서대로 이어 붙인 리스트를 반환한다.
	 * <p>
	 * 이름이 "union"이지만 <b>중복은 제거하지 않으며</b>, set 의미의 합집합이 아닌 concat에 가깝다.
	 *
	 * @param <T> 원소 타입.
	 * @param clArray 이어 붙일 컬렉션들.
	 * @return 모든 컬렉션의 원소를 순서대로 담은 새 리스트.
	 */
	@SafeVarargs
	public static <T> List<T> union(Collection<? extends T>... clArray) {
		List<T> union = new ArrayList<>();
		for ( Collection<? extends T> cl: clArray ) {
			union.addAll(cl);
		}
		return union;
	}

	/**
	 * 조건을 만족하는 원소와 만족하지 않는 원소를 두 리스트로 분할하여 반환한다.
	 *
	 * @param <T> 원소 타입.
	 * @param values 대상 iterable.
	 * @param pred 분할 조건.
	 * @return ({@code pred}가 {@code true}인 원소, {@code false}인 원소) 두 리스트의 {@link Tuple}.
	 */
	public static <T> Tuple<List<T>,List<T>> partition(Iterable<T> values, Predicate<? super T> pred) {
		List<T> trueCollection = new ArrayList<>();
		List<T> falseCollection = new ArrayList<>();
		for ( T elm: values ) {
			if ( pred.test(elm) ) {
				trueCollection.add(elm);
			}
			else {
				falseCollection.add(elm);
			}
		}
		
		return Tuple.of(trueCollection, falseCollection);
	}
	
	/**
	 * 리스트를 좌측에서 우측으로 순회하며 누적한다.
	 * <p>
	 * 결과 = {@code fold(...fold(fold(init, e0), e1)..., eN)}.
	 * 빈 리스트는 {@code init}을 그대로 반환한다.
	 *
	 * @param <T> 입력 원소 타입.
	 * @param <U> 누적 결과 타입.
	 * @param list 대상 리스트.
	 * @param init 초기 누적 값.
	 * @param fold 누적 함수 (현재 누적, 다음 원소) → 새 누적.
	 * @return 누적된 최종 값.
	 */
	public static <T,U> U foldLeft(List<? extends T> list, U init, BiFunction<? super U, ? super T, ? extends U> fold) {
		U accum = init;
		for ( T t: list ) {
			accum = fold.apply(accum, t);
		}
		return accum;
	}

	/**
	 * 누적 값이 {@code stopper}와 같아지면 즉시 중단하는 left-fold.
	 * <p>
	 * 동등성은 {@link Object#equals(Object)}로 비교한다. 누적 값이 {@code null}이 될 수 있으면
	 * NPE가 발생할 수 있으므로 caller가 보장해야 한다.
	 *
	 * @param <T> 입력 원소 타입.
	 * @param <U> 누적 결과 타입.
	 * @param list 대상 리스트.
	 * @param init 초기 누적 값.
	 * @param stopper 일치 시 즉시 중단할 sentinel 값.
	 * @param fold 누적 함수.
	 * @return 중단 시 {@code stopper}, 끝까지 진행 시 최종 누적 값.
	 */
	public static <T,U> U foldLeft(List<T> list, U init, U stopper, BiFunction<U, T, U> fold) {
		U accum = init;
		for ( T t: list ) {
			accum = fold.apply(accum, t);
			if ( Objects.equals(accum, stopper) ) {
				return accum;
			}
		}
		return accum;
	}

	/**
	 * 커링된 함수형 인자를 받는 {@link #foldLeft(List, Object, BiFunction)} 변형.
	 *
	 * @param <T> 입력 원소 타입.
	 * @param <U> 누적 결과 타입.
	 * @param list 대상 리스트.
	 * @param identity 초기 누적 값.
	 * @param fold {@code u -> t -> u'} 형태의 커링된 누적 함수.
	 * @return 누적된 최종 값.
	 */
	public static <T,U> U foldLeft(List<T> list, U identity, Function<U, Function<T, U>> fold) {
		return foldLeft(list, identity, (u, t)->fold.apply(u).apply(t));
	}

	/**
	 * 리스트를 우측에서 좌측으로 순회하며 누적한다.
	 * <p>
	 * 결과 = {@code fold(e0, fold(e1, fold(..., fold(eN, init))))}.
	 * 빈 리스트는 {@code init}을 그대로 반환한다.
	 *
	 * @param <T> 입력 원소 타입.
	 * @param <U> 누적 결과 타입.
	 * @param list 대상 리스트.
	 * @param init 초기 누적 값.
	 * @param fold 누적 함수 (현재 원소, 누적) → 새 누적.
	 * @return 누적된 최종 값.
	 */
	public static <T,U> U foldRight(List<? extends T> list, U init,
									BiFunction<? super T, ? super U, ? extends U> fold) {
		U accum = init;
		for ( int i = list.size()-1; i >= 0; --i ) {
			final T t = list.get(i);
			accum = fold.apply(t, accum);
		}
		return accum;
	}

	/**
	 * 누적 값이 {@code stopper}와 같아지면 즉시 중단하는 right-fold.
	 * <p>
	 * 동등성은 {@link Object#equals(Object)}로 비교한다. 누적 값이 {@code null}이 될 수 있으면
	 * NPE가 발생할 수 있으므로 caller가 보장해야 한다.
	 *
	 * @param <T> 입력 원소 타입.
	 * @param <U> 누적 결과 타입.
	 * @param list 대상 리스트.
	 * @param init 초기 누적 값.
	 * @param stopper 일치 시 즉시 중단할 sentinel 값.
	 * @param fold 누적 함수.
	 * @return 중단 시 {@code stopper}, 끝까지 진행 시 최종 누적 값.
	 */
	public static <T,U> U foldRight(List<T> list, U init, U stopper, BiFunction<T, U, U> fold) {
		U accum = init;
		for ( int i = list.size()-1; i >= 0; --i ) {
			final T t = list.get(i);
			accum = fold.apply(t, accum);
			if ( Objects.equals(accum, stopper) ) {
				return accum;
			}
		}
		return accum;
	}

	/**
	 * 커링된 함수형 인자를 받는 {@link #foldRight(List, Object, BiFunction)} 변형.
	 *
	 * @param <T> 입력 원소 타입.
	 * @param <U> 누적 결과 타입.
	 * @param list 대상 리스트.
	 * @param identity 초기 누적 값.
	 * @param fold {@code t -> u -> u'} 형태의 커링된 누적 함수.
	 * @return 누적된 최종 값.
	 */
	public static <T,U> U foldRight(List<T> list, U identity,
									Function<T, Function<U, U>> fold) {
		return foldRight(list, identity, (t,u) -> fold.apply(t).apply(u));
	}

	/**
	 * 리스트를 좌측에서 우측으로 순회하며 binary 연산으로 축약한다.
	 * <p>
	 * 첫 원소를 초기값으로 사용하고 두 번째 원소부터 누적한다. 빈 리스트면 {@code null}.
	 *
	 * @param <T> 원소 타입.
	 * @param list 대상 리스트.
	 * @param fold 두 원소를 결합하는 함수.
	 * @return 축약된 단일 값, 또는 빈 리스트면 {@code null}.
	 */
	public static <T> T reduce(List<T> list, BiFunction<? super T, ? super T, ? extends T> fold) {
		if ( list.isEmpty() ) {
			return null;
		}
		
		T accum = list.get(0);
		for ( int i =1; i < list.size(); ++i ) {
			final T t = list.get(i);
			accum = fold.apply(accum, t);
		}
		return accum;
	}
	
	/**
	 * 커링된 함수형 인자를 받는 {@link #reduce(List, BiFunction)} 변형.
	 *
	 * @param <T> 원소 타입.
	 * @param list 대상 리스트.
	 * @param fold {@code t -> t -> t} 형태의 커링된 결합 함수.
	 * @return 축약된 단일 값, 또는 빈 리스트면 {@code null}.
	 */
	public static <T> T reduce(List<T> list, Function<T, Function<T, T>> fold) {
		return reduce(list, (accum,t) -> fold.apply(accum).apply(t));
	}

	/**
	 * 리스트를 여러 guard로 분기하여 각각의 guard에 대응되는 sub-list로 묶어 반환한다.
	 * <p>
	 * 각 원소는 <b>처음으로 매치되는 guard</b>의 분기에만 들어가며, 어느 guard에도 매치되지 않는
	 * 원소는 결과에 포함되지 않는다(필요하면 마지막 guard로 {@code v -> true}를 넘겨 처리).
	 *
	 * @param <T> 원소 타입.
	 * @param list 대상 리스트.
	 * @param guards 분기 조건들. 길이만큼의 sub-list가 반환된다.
	 * @return 각 guard 인덱스에 대응되는 sub-list들의 리스트(길이 = {@code guards.length}).
	 */
	@SafeVarargs
	public static <T> List<List<T>> branch(List<T> list, Predicate<T>... guards) {
		List<List<T>> branches = new ArrayList<>(guards.length);
		for ( int i =0; i < guards.length; ++i ) {
			branches.add(new ArrayList<>());
		}
		
		for ( T v: list ) {
			for ( int i =0; i < guards.length; ++i ) {
				if ( guards[i].test(v) ) {
					branches.get(i).add(v);
					break;
				}
			}
		}
		
		return branches;
	}
}
