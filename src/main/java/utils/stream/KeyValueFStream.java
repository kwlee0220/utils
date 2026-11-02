package utils.stream;


import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.Keyed;
import utils.Tuple;
import utils.func.CheckedBiConsumerX;
import utils.func.FOption;
import utils.stream.FStreams.PeekedStream;

/**
 * {@link KeyValue} 원소의 키와 값을 따로 다루는 연산을 더한 {@link FStream}.
 * <p>
 * 원소는 {@code KeyValue<K,V>}이며, {@link FStream}의 모든 연산을 그대로 쓸 수 있다. 여기에 더해 키와 값을
 * 함수의 인자로 따로 받는 변환·조회({@link #map(BiFunction)}, {@link #forEach(BiConsumer)} 등), 키 또는 값만
 * 바꾸는 변환({@link #mapKey(Function)}, {@link #mapValue(Function)}), 키 기준의 그룹화·정렬·대응
 * ({@link #groupByKey()}, {@link #sortByKey()}, {@link #match(Map)}), {@link Map}으로의 수집
 * ({@link #toMap()}, {@link #toListMap()})을 제공한다.
 * <p>
 * {@link FStream}과 마찬가지로 지연 평가되며, 결과를 모으는 연산이나 {@code forEach} 계열은 스트림을 끝까지 소비한다.
 * 같은 키가 여러 번 나올 수 있으며 키의 유일성은 보장하지 않는다.
 * <pre>{@code
 * Map<String,Integer> stock = Map.of("apple", 3, "pear", 0, "plum", 5);
 * Map<String,Integer> doubled = KeyValueFStream.from(stock)
 *                                              .filterValue(n -> n > 0)
 *                                              .mapValue(n -> n * 2)
 *                                              .toMap();     // {apple=6, plum=10}
 * }</pre>
 *
 * @param <K>	키 타입.
 * @param <V>	값 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface KeyValueFStream<K,V> extends FStream<KeyValue<K,V>> {
	/**
	 * 주어진 {@link KeyValue}들을 차례로 반환하는 스트림을 생성한다.
	 *
	 * @param <K>		키 타입.
	 * @param <V>		값 타입.
	 * @param values	반환할 {@link KeyValue}들.
	 * @return	생성된 스트림.
	 * @throws IllegalArgumentException	{@code values}가 {@code null}인 경우.
	 */
	@SafeVarargs
	public static <K,V> KeyValueFStream<K,V> of(KeyValue<K,V>... values) {
		Preconditions.checkArgument(values != null, "null values");

		return from(Arrays.asList(values));
	}

	/**
	 * {@link KeyValue}를 원소로 하는 {@link FStream}을 {@code KeyValueFStream}으로 감싼다.
	 * <p>
	 * 원소를 복사하지 않으며, 반환된 스트림을 닫으면 {@code stream}도 닫힌다.
	 *
	 * @param <K>		키 타입.
	 * @param <V>		값 타입.
	 * @param stream	감쌀 스트림.
	 * @return	생성된 스트림.
	 * @throws IllegalArgumentException	{@code stream}이 {@code null}인 경우.
	 */
	public static <K,V> KeyValueFStream<K,V>
	from(FStream<? extends KeyValue<? extends K,? extends V>> stream) {
		Preconditions.checkArgument(stream != null, "stream is null");

		return new DefaultKeyValueFStream<>(narrow(stream));
	}

	/**
	 * 주어진 {@link Iterable}의 {@link KeyValue}들을 차례로 반환하는 스트림을 생성한다.
	 *
	 * @param <K>	키 타입.
	 * @param <V>	값 타입.
	 * @param iter	{@link KeyValue}들의 {@link Iterable}.
	 * @return	생성된 스트림.
	 * @throws IllegalArgumentException	{@code iter}가 {@code null}인 경우.
	 */
	public static <K,V> KeyValueFStream<K,V>
	from(Iterable<? extends KeyValue<? extends K,? extends V>> iter) {
		Preconditions.checkArgument(iter != null, "null iterable");

		return new DefaultKeyValueFStream<>(narrow(FStream.from(iter)));
	}

	/**
	 * 주어진 {@link Iterator}가 반환하는 {@link KeyValue}들을 차례로 반환하는 스트림을 생성한다.
	 * <p>
	 * 스트림은 {@code iter}를 그대로 소비하므로 한 번만 순회할 수 있다.
	 *
	 * @param <K>	키 타입.
	 * @param <V>	값 타입.
	 * @param iter	{@link KeyValue}들의 {@link Iterator}.
	 * @return	생성된 스트림.
	 * @throws IllegalArgumentException	{@code iter}가 {@code null}인 경우.
	 */
	public static <K,V> KeyValueFStream<K,V> from(Iterator<KeyValue<K,V>> iter) {
		Preconditions.checkArgument(iter != null, "null iterable");

		return new DefaultKeyValueFStream<>(FStream.from(iter));
	}

	/**
	 * 주어진 {@link Map}객체에 포함된 모든 key-value pair를 반환하는 {@link FStream}를 생성한다.
	 * <p>
	 * 생성된 스트림은 입력 Map객체에 포함된 key-value pair에 해당하는 {@link KeyValue} 객체들을 반환한다.
	 * 반환 순서는 {@code kvMap}의 순회 순서를 따른다.
	 *
	 * @param <K> {@link Map}객체의 key type.
	 * @param <V> {@link Map}객체의 value type.
	 * @param kvMap	입력 {@link Map} 객체.
	 * @return FStream 객체
	 * @throws IllegalArgumentException	{@code kvMap}이 {@code null}인 경우.
	 */
	public static <K,V> KeyValueFStream<K,V> from(Map<K,V> kvMap) {
		Preconditions.checkArgument(kvMap != null, "kvMap is null");

		return FStream.from(kvMap.entrySet())
						.toKeyValueStream(KeyValue::from);
	}

	/**
	 * {@link Keyed} 객체들을 각 객체의 키({@link Keyed#key()})와 객체 자신의 쌍으로 반환하는 스트림을 생성한다.
	 *
	 * @param <K>	키 타입.
	 * @param <V>	{@link Keyed} 객체 타입.
	 * @param iter	{@link Keyed} 객체들의 {@link Iterable}.
	 * @return	생성된 스트림.
	 * @throws IllegalArgumentException	{@code iter}가 {@code null}인 경우.
	 */
	public static <K,V extends Keyed<K>> KeyValueFStream<K,V> fromKeyed(Iterable<V> iter) {
		Preconditions.checkArgument(iter != null, "null iterable");

		Function<V,KeyValue<K,V>> mapper = kv -> KeyValue.of(kv.key(), kv);
		return FStream.from(iter).toKeyValueStream(mapper);
	}

	/**
	 * 각 원소의 키만 반환하는 스트림을 생성한다.
	 *
	 * @return	키 스트림.
	 */
	public default FStream<K> keys() {
		return map(kv -> kv.key());
	}

	/**
	 * 각 원소의 값만 반환하는 스트림을 생성한다.
	 *
	 * @return	값 스트림.
	 */
	public default FStream<V> values() {
		return map(kv -> kv.value());
	}

	/**
	 * 키가 주어진 조건을 만족하는 원소만 반환하는 스트림을 생성한다.
	 *
	 * @param pred	키에 대한 조건.
	 * @return	걸러진 스트림.
	 * @throws IllegalArgumentException	{@code pred}가 {@code null}인 경우.
	 */
	public default KeyValueFStream<K,V> filterKey(Predicate<? super K> pred) {
		Preconditions.checkArgument(pred != null, "predicate is null");

		return from(filter(kv -> pred.test(kv.key())));
	}

	/**
	 * 값이 주어진 조건을 만족하는 원소만 반환하는 스트림을 생성한다.
	 *
	 * @param pred	값에 대한 조건.
	 * @return	걸러진 스트림.
	 * @throws IllegalArgumentException	{@code pred}가 {@code null}인 경우.
	 */
	public default KeyValueFStream<K,V> filterValue(Predicate<? super V> pred) {
		Preconditions.checkArgument(pred != null, "predicate is null");

		return from(filter(kv -> pred.test(kv.value())));
	}

	/**
	 * 각 원소의 키와 값을 인자로 받는 함수를 적용한 결과의 스트림을 생성한다.
	 *
	 * @param <S>		결과 타입.
	 * @param mapper	키와 값을 받아 결과를 만드는 함수.
	 * @return	변환된 스트림.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public default <S> FStream<S> map(BiFunction<? super K, ? super V, ? extends S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return map(kv -> mapper.apply(kv.key(), kv.value()));
	}

	/**
	 * 값은 그대로 두고 키만 바꾼 스트림을 생성한다.
	 *
	 * @param <S>		새 키 타입.
	 * @param mapper	기존 키로 새 키를 만드는 함수.
	 * @return	키가 바뀐 스트림. {@code mapper}가 {@code null} 키를 내면 그 원소를 꺼낼 때
	 * 			{@link NullPointerException}이 난다({@link KeyValue#mapKey(Function)}와 같다; 2026-11-02 이전에는
	 * 			{@link IllegalArgumentException}이었다).
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public default <S> KeyValueFStream<S,V> mapKey(Function<? super K,? extends S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return from(map(kv -> kv.mapKey(mapper)));
	}

	/**
	 * 값은 그대로 두고, 키와 값으로 새 키를 만들어 키만 바꾼 스트림을 생성한다.
	 * <p>
	 * 2026-11-02 이전의 이름은 {@code mapKey(BiFunction)}이었다. {@link #mapKey(Function)}와 오버로드되어
	 * {@code mapKey(Objects::toString)}처럼 오버로드가 있는 메소드 참조가 모호해 컴파일되지 않았으므로 이름을
	 * 바꿨다(별칭 없음).
	 *
	 * @param <S>		새 키 타입.
	 * @param mapper	기존 키와 값으로 새 키를 만드는 함수.
	 * @return	키가 바뀐 스트림. {@code mapper}가 {@code null} 키를 내면 그 원소를 꺼낼 때
	 * 			{@link NullPointerException}이 난다({@link KeyValue#mapKey(Function)}와 같다; 2026-11-02 이전에는
	 * 			{@link IllegalArgumentException}이었다).
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public default <S> KeyValueFStream<S,V>
	mapKeyWithValue(BiFunction<? super K,? super V,? extends S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return from(map(kv -> kv.mapKey(mapper)));
	}

	/**
	 * 키는 그대로 두고 값만 바꾼 스트림을 생성한다.
	 *
	 * @param <U>		새 값 타입.
	 * @param mapper	기존 값으로 새 값을 만드는 함수.
	 * @return	값이 바뀐 스트림.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public default <U> KeyValueFStream<K,U> mapValue(Function<? super V,? extends U> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return from(map(kv -> KeyValue.of(kv.key(), mapper.apply(kv.value()))));
	}

	/**
	 * 키는 그대로 두고, 키와 값으로 새 값을 만들어 값만 바꾼 스트림을 생성한다.
	 * <p>
	 * 2026-11-02 이전의 이름은 {@code mapValue(BiFunction)}이었다. {@link #mapValue(Function)}와 오버로드되어
	 * {@code mapValue(Objects::toString)}처럼 오버로드가 있는 메소드 참조가 모호해 컴파일되지 않았으므로 이름을
	 * 바꿨다(별칭 없음).
	 *
	 * @param <U>		새 값 타입.
	 * @param mapper	기존 키와 값으로 새 값을 만드는 함수.
	 * @return	값이 바뀐 스트림.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public default <U> KeyValueFStream<K,U>
	mapValueWithKey(BiFunction<? super K,? super V,? extends U> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return from(map(kv -> KeyValue.of(kv.key(), mapper.apply(kv.key(), kv.value()))));
	}

	/**
	 * 각 원소의 키와 값으로 새 {@link KeyValue}를 만들어 키와 값을 함께 바꾼 스트림을 생성한다.
	 *
	 * @param <K2>		새 키 타입.
	 * @param <V2>		새 값 타입.
	 * @param mapper	기존 키와 값으로 새 {@link KeyValue}를 만드는 함수.
	 * @return	변환된 스트림.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public default <K2,V2>
	KeyValueFStream<K2, V2> mapKeyValue(BiFunction<? super K, ? super V, KeyValue<K2,V2>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return from(map(kv -> mapper.apply(kv.key(), kv.value())));
	}

	/**
	 * 각 원소의 키와 값으로 스트림을 만들고, 그 스트림들을 차례로 이어 붙인 스트림을 생성한다.
	 *
	 * @param <T>		결과 원소 타입.
	 * @param mapper	키와 값으로 스트림을 만드는 함수.
	 * @return	이어 붙인 스트림.
	 * @throws IllegalArgumentException	{@code mapper}가 {@code null}인 경우.
	 */
	public default <T> FStream<T> flatMap(BiFunction<? super K,? super V,FStream<T>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return flatMap(kv -> mapper.apply(kv.key(), kv.value()));
	}

	/**
	 * 모든 원소에 대해 키와 값을 인자로 주어진 작업을 수행한다. 스트림을 끝까지 소비한다.
	 *
	 * @param effect	키와 값을 받는 작업.
	 * @throws IllegalArgumentException	{@code effect}가 {@code null}인 경우.
	 */
	public default void forEach(BiConsumer<? super K,? super V> effect) {
		Preconditions.checkArgument(effect != null, "effect is null");

		forEach(kv -> effect.accept(kv.key(), kv.value()));
	}

	/**
	 * 모든 원소에 대해 키와 값을 인자로, 검사 예외를 던질 수 있는 작업을 수행한다.
	 * <p>
	 * 작업이 예외를 던지면 그 자리에서 멈추고 예외를 그대로 전달한다. 정상 종료든 예외든 끝나면 스트림을 닫는다.
	 *
	 * @param <X>		작업이 던질 수 있는 예외 타입.
	 * @param effect	키와 값을 받는 작업.
	 * @throws X	작업이 예외를 던진 경우.
	 * @throws IllegalArgumentException	{@code effect}가 {@code null}인 경우.
	 */
	public default <X extends Throwable>
	void forEachOrThrow(CheckedBiConsumerX<? super K,? super V,X> effect) throws X {
		utils.Preconditions.checkNotNullArgument(effect, "effect is null");

		try {
			FOption<KeyValue<K,V>> next;
			while ( (next = next()).isPresent() ) {
				KeyValue<K,V> kv = next.get();
				effect.accept(kv.key(), kv.value());
			}
		}
		finally {
			closeQuietly();
		}
	}

	/**
	 * 원소를 바꾸지 않고, 각 원소가 반환될 때 주어진 작업을 수행하는 스트림을 생성한다(디버깅·로깅용).
	 *
	 * @param effect	각 원소에 수행할 작업.
	 * @return	원소가 같은 스트림.
	 * @throws IllegalArgumentException	{@code effect}가 {@code null}인 경우.
	 */
	public default KeyValueFStream<K,V> peek(Consumer<? super KeyValue<K,V>> effect) {
		utils.Preconditions.checkNotNullArgument(effect, "effect is null");

		return from(new PeekedStream<>(this, effect));
	}

	/**
	 * 원소들을 키별로 모아 값 목록으로 묶는다. 스트림을 끝까지 소비한다.
	 * <p>
	 * 그룹(키)은 처음 나온 순서를, 각 키의 값 목록은 스트림에 나온 순서를 따른다. {@code null} 값도 목록에 그대로 들어간다({@link KeyValue}가
	 * {@code null} 값을 허용하므로). 결과를 맵으로 받으려면 {@link #toListMap()}을 쓴다(키·값 목록이 같다).
	 * <p>
	 * 종단 연산이므로 정상 종료든 예외든 본 스트림을 닫는다.
	 * 2026-11-02 이전에는 {@code null} 값을 {@link IllegalArgumentException}으로 거부했다.
	 *
	 * @return	키별 값 목록을 담은 {@link KeyedGroups}.
	 */
	public default KeyedGroups<K,V> groupByKey() {
		return collect(KeyedGroups.create(), (g,kv) -> g.add(kv.key(), kv.value()));
	}

	/**
	 * 키의 자연 순서로 정렬한 스트림을 생성한다.
	 * <p>
	 * 키는 서로 비교할 수 있는 {@link Comparable}이어야 한다. 지연 평가되지 않는다: 호출하는 시점에 원본을 끝까지
	 * 읽어 메모리에 적재한 뒤 정렬하고 원본을 닫는다. 그래서 무한 스트림이나 매우 큰 스트림에는 쓸 수 없다
	 * ({@link FStream#sort(java.util.Comparator)} 참조).
	 *
	 * @return	정렬된 스트림.
	 * @throws ClassCastException	키가 {@link Comparable}이 아니거나 서로 비교할 수 없는 경우.
	 */
	public default KeyValueFStream<K,V> sortByKey() {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		FStream<KeyValue<K,V>> sorted = sort((kv1,kv2) -> ((Comparable)kv1.key()).compareTo(kv2.key()));
		return from(sorted);
	}

//	public default <R> KeyValueFStream<K,Tuple<List<V>,List<R>>> outerJoin(KeyValueFStream<K,R> right) {
//		return new OuterJoinedFStream<>(this, right);
//	}

	/**
	 * 각 원소의 키로 참조 맵을 찾아, 원래 값과 참조 맵의 값을 묶은 스트림을 생성한다.
	 * <p>
	 * 결과 원소는 {@code (키, (원래 값, 참조 맵의 값))}이다. 일치 여부는 참조 맵에 키가 있는지
	 * ({@link Map#containsKey(Object)})로 판단하므로, 참조 맵에 값이 {@code null}로 들어 있는 키도 일치한 것으로
	 * 전달된다. {@code keepUnmatched}가 {@code true}이면 일치하지 않은 원소도 참조 값 {@code null}로 유지하고,
	 * {@code false}이면 버린다. 이때는 두 경우를 튜플만으로 구분할 수 없다.
	 *
	 * @param <R>			참조 맵의 값 타입.
	 * @param lut			키로 찾을 참조 맵.
	 * @param keepUnmatched	일치하지 않은 원소를 유지할지 여부.
	 * @return	원래 값과 참조 값을 묶은 스트림.
	 */
	public default <R> KeyValueFStream<K,Tuple<V,R>> match(Map<? super K,? extends R> lut,
															boolean keepUnmatched) {
		utils.Preconditions.checkNotNullArgument(lut, "lut is null");

		return new MatchKVFStream<>(this, lut, keepUnmatched);
	}
	
	/**
	 * 일치하지 않은 원소를 버리는 {@link #match(Map, boolean)}.
	 *
	 * @param <R>	참조 맵의 값 타입.
	 * @param lut	키로 찾을 참조 맵.
	 * @return	원래 값과 참조 값을 묶은 스트림.
	 */
	public default <R> KeyValueFStream<K,Tuple<V,R>> match(Map<? super K,? extends R> lut) {
		return match(lut, false);
	}
	
	/**
	 * 각 원소의 키로 값 목록을 담은 참조 맵을 찾아, 원래 값과 목록의 각 값을 하나씩 묶어 펼친 스트림을
	 * 생성한다.
	 * <p>
	 * 키 하나에 여러 참조 값이 대응하는 일대다 조인이다. 결과 원소는 {@code (키, (원래 값, 참조 값))}이며, 원소별
	 * 결과 행 수는 다음과 같다.
	 * <table class="striped">
	 *   <caption>원소별 결과 행</caption>
	 *   <tr><th>참조 맵의 상태</th><th>{@code keepUnmatched=false}<br>(inner join)</th>
	 *       <th>{@code keepUnmatched=true}<br>(left outer join)</th></tr>
	 *   <tr><td>값이 {@code n}개인 목록 ({@code n > 0})</td><td colspan="2">{@code n}행:
	 *       {@code (v, r1)}, …, {@code (v, rn)}</td></tr>
	 *   <tr><td>빈 목록, 또는 {@code null}</td><td>0행</td><td>1행: {@code (v, null)}</td></tr>
	 *   <tr><td>키가 없음</td><td>0행</td><td>1행: {@code (v, null)}</td></tr>
	 * </table>
	 * 즉 참조 값이 하나도 없는 원소(빈 목록, {@code null} 목록, 없는 키)는 모두 "일치하지 않음"으로 같게
	 * 다룬다. 이 점에서 키의 존재만 보는 {@link #match(Map, boolean)}와 다르다({@code match}는 값이
	 * {@code null}인 키도 일치로 본다).
	 * <p>
	 * 참고:
	 * <ul>
	 *   <li>{@code keepUnmatched=true}의 {@code (v, null)} 행은 목록 안의 {@code null} 원소로 생긴 행과
	 *       튜플만으로는 구별되지 않는다.</li>
	 *   <li>참조 목록은 원소마다 한 번 순회한다({@link Iterable#iterator()}를 한 번 호출). 같은 키가
	 *   	 여러 번 나오면 그때마다 다시 순회한다.</li>
	 *   <li>lazy 연산이며, 결과 스트림을 끝까지 읽거나 닫으면 본 스트림도 닫힌다.</li>
	 * </ul>
	 *
	 * @param <R>			참조 목록의 원소 타입.
	 * @param lut			키로 찾을 참조 맵(값은 목록). 값이 {@code null}이거나 빈 목록이어도 된다.
	 * @param keepUnmatched	참조 값이 하나도 없는 원소를 {@code (v, null)} 한 행으로 유지할지 여부.
	 * @return	원래 값과 참조 값을 묶어 펼친 스트림.
	 */
	public default <R> KeyValueFStream<K,Tuple<V,R>>
	flatMatch(Map<? super K,? extends Iterable<? extends R>> lut, boolean keepUnmatched) {
		utils.Preconditions.checkNotNullArgument(lut, "lut is null");

		FStream<KeyValue<K,Tuple<V,R>>> joined = flatMap((k,v) -> {
			Iterable<? extends R> refs = lut.get(k);
			Iterator<? extends R> iter = (refs != null) ? refs.iterator() : null;
			if ( iter != null && iter.hasNext() ) {
				return FStream.<R>from(iter).map(r -> KeyValue.of(k, Tuple.of(v, r)));
			}
			// 참조 값이 하나도 없다(빈 목록, null 목록, 없는 키): 일치하지 않음.
			return keepUnmatched ? FStream.of(KeyValue.of(k, Tuple.of(v, (R)null))) : FStream.empty();
		});
		return KeyValueFStream.from(joined);
	}

	/**
	 * 참조 값이 하나도 없는 원소를 버리는(inner join) {@link #flatMatch(Map, boolean)}.
	 * <p>
	 * {@code flatMatch(lut, false)}와 같다. 참조 목록이 비어 있거나 {@code null}이거나 키가 없는 원소는 결과에
	 * 나오지 않는다.
	 *
	 * @param <R>	참조 목록의 원소 타입.
	 * @param lut	키로 찾을 참조 맵(값은 목록).
	 * @return	원래 값과 참조 값을 묶어 펼친 스트림.
	 * @see #flatMatch(Map, boolean)
	 */
	public default <R> KeyValueFStream<K,Tuple<V,R>>
	flatMatch(Map<? super K,? extends Iterable<? extends R>> lut) {
		return flatMatch(lut, false);
	}

	/**
	 * 모든 원소를 주어진 맵에 넣어 반환한다. 스트림을 끝까지 소비한다.
	 * <p>
	 * 같은 키가 여러 번 나오면 나중 값이 앞의 값을 덮어쓴다. 키 순서를 유지하려면
	 * {@link java.util.LinkedHashMap}을 넘긴다. {@link java.util.concurrent.ConcurrentHashMap}처럼
	 * {@code null} 키나 값을 받지 않는 맵을 넘기면, 그런 원소에서 그 맵의 {@link NullPointerException}이
	 * 발생한다(앞서 넣은 원소는 맵에 남는다).
	 *
	 * @param <M>	맵 타입.
	 * @param map	원소를 넣을 맵.
	 * @return	{@code map}.
	 */
	public default <M extends Map<? super K,? super V>> M toMap(M map) {
		return collect(map, (accum,kv) -> accum.put(kv.key(), kv.value()));
	}

	/**
	 * 모든 원소를 새 {@link HashMap}에 넣어 반환한다. 같은 키는 나중 값이 이긴다({@link #toMap(Map)} 참조).
	 *
	 * @return	원소를 담은 {@link HashMap}.
	 */
	public default HashMap<K,V> toMap() {
		return toMap(Maps.newHashMap());
	}

	/**
	 * 모든 원소를 키별 값 목록으로 모은 {@link HashMap}을 반환한다. 스트림을 끝까지 소비한다.
	 * <p>
	 * 같은 키의 값들은 스트림에 나온 순서대로 목록에 들어가며, {@code null} 값도 그대로 들어간다.
	 * {@link #groupByKey()}와 같은 결과를 {@link KeyedGroups} 대신 맵으로 받는 것이다.
	 * <p>
	 * 종단 연산이므로 정상 종료든 예외든 본 스트림을 닫는다.
	 *
	 * @return	키별 값 목록을 담은 {@link HashMap}.
	 */
	public default HashMap<K,List<V>> toListMap() {
		return collect(Maps.newHashMap(),
						(groups, kv) -> groups.computeIfAbsent(kv.key(), k -> Lists.newArrayList())
											.add(kv.value()));
	}

	/**
	 * 이 스트림에 주어진 스트림 변환 함수를 적용한 결과를 반환한다.
	 * <p>
	 * 여러 연산을 묶은 함수를 메소드 체인 안에서 그대로 쓰기 위한 것이다
	 * (예: {@code stream.liftKeyValues(MyOps::normalize).toMap()}).
	 *
	 * @param <K2>			결과 스트림의 키 타입.
	 * @param <V2>			결과 스트림의 값 타입.
	 * @param streamFunc	이 스트림을 받아 새 스트림을 만드는 함수.
	 * @return	{@code streamFunc}가 만든 스트림.
	 */
	public default <K2,V2>
	KeyValueFStream<K2,V2> liftKeyValues(Function<? super KeyValueFStream<K,V>,
														? extends KeyValueFStream<K2,V2>> streamFunc) {
        return streamFunc.apply(this);
    }

	/**
	 * {@code FStream<? extends KeyValue<? extends K,? extends V>>}를 {@code FStream<KeyValue<K,V>>}로 본다.
	 * <p>
	 * {@link FStream}은 생산자 전용이고 {@link KeyValue}는 불변이므로 이 변환은 타입 안전하다.
	 */
	@SuppressWarnings("unchecked")
	private static <K,V> FStream<KeyValue<K,V>>
	narrow(FStream<? extends KeyValue<? extends K,? extends V>> stream) {
		return (FStream<KeyValue<K,V>>)(FStream<?>)stream;
	}
}
