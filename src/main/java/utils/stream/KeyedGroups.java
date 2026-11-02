package utils.stream;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.func.FOption;
import utils.func.MultipleCases;


/**
 * 키별 값 목록의 모음. {@link KeyValueFStream#groupByKey()}가 만든다.
 * <p>
 * 그룹(키)은 처음 나온 순서를 유지하고, 각 그룹의 값 목록은 값이 더해진 순서를 따른다. 값 목록은 적어도 값
 * 하나를 갖는다(빈 그룹은 없다). {@code null} 값도 담을 수 있다.
 * <p>
 * <b>읽기 전용 뷰</b>: {@link #asMap()}, {@link #keySet()}, {@link #get(Object)},
 * {@link #getOrDefault(Object, List)}는 수정할 수 없는 뷰를 돌려준다. 내부 상태는 {@link #add(Object, Object)}와
 * {@link #remove(Object)}로만 바뀐다. 2026-11-02 이전에는 내부 맵과 목록을 그대로 돌려주어, 호출자가
 * {@code null} 그룹이나 빈 그룹을 넣을 수 있었다(그 뒤 {@link #size()}가 NPE). 단 {@link #fstream()}의 값 목록은
 * 내부 목록 그대로다(아래 참조).
 *
 * @param <K>	키 타입.
 * @param <V>	값 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyedGroups<K,V> implements FStreamable<KeyValue<K,List<V>>> {
	private final Map<K,List<V>> m_groups;

	/**
	 * 빈 그룹 모음을 생성한다.
	 *
	 * @param <K>	키 타입.
	 * @param <V>	값 타입.
	 * @return	빈 {@link KeyedGroups}.
	 */
	public static <K,V> KeyedGroups<K,V> create() {
		return new KeyedGroups<>();
	}

	private KeyedGroups() {
		// 처음 나온 순서를 유지한다(2026-11-02 이전에는 HashMap 순서였다).
		m_groups = Maps.newLinkedHashMap();
	}

	/**
	 * 키별 값 목록을 맵으로 본 읽기 전용 뷰를 반환한다.
	 * <p>
	 * 맵과 값 목록 모두 수정할 수 없다(수정하면 {@link UnsupportedOperationException}). 뷰이므로 이후의
	 * {@link #add(Object, Object)}·{@link #remove(Object)}가 반영된다. 키 순서는 처음 나온 순서다.
	 *
	 * @return	키별 값 목록의 읽기 전용 뷰.
	 */
	public Map<K,List<V>> asMap() {
		return Collections.unmodifiableMap(Maps.transformValues(m_groups, Collections::unmodifiableList));
	}

	/**
	 * 그룹 키들의 읽기 전용 뷰를 반환한다. 순서는 처음 나온 순서다.
	 *
	 * @return	그룹 키 집합.
	 */
	public Set<K> keySet() {
		return Collections.unmodifiableSet(m_groups.keySet());
	}

	/**
	 * 그룹 수(서로 다른 키의 수)를 반환한다.
	 *
	 * @return	그룹 수.
	 * @see #size()
	 */
	public int groupCount() {
		return m_groups.size();
	}

	/**
	 * 모든 그룹의 값 개수의 합(전체 값 개수)을 반환한다. 그룹 수는 {@link #groupCount()}로 얻는다.
	 *
	 * @return	전체 값 개수.
	 */
	public long size() {
		return KeyValueFStream.from(m_groups)
							    .values()
								.mapToInt(List::size)
								.sum();
	}

	/**
	 * 키의 값 목록을 읽기 전용으로 반환한다.
	 *
	 * @param key	키.
	 * @return	키의 값 목록(수정 불가). 키가 없으면 {@code null}.
	 */
	public List<V> get(final K key) {
		List<V> group = m_groups.get(key);
		return (group != null) ? Collections.unmodifiableList(group) : null;
	}

	/**
	 * 키의 값 목록을 읽기 전용으로 반환하고, 키가 없으면 {@code defaultValue}를 그대로 반환한다.
	 *
	 * @param key			키.
	 * @param defaultValue	키가 없을 때 반환할 값.
	 * @return	키의 값 목록(수정 불가), 또는 {@code defaultValue}.
	 */
	public List<V> getOrDefault(final K key, List<V> defaultValue) {
		List<V> group = m_groups.get(key);
		return (group != null) ? Collections.unmodifiableList(group) : defaultValue;
	}

	/**
	 * 키의 값 목록 끝에 값을 더한다. 키의 목록이 없으면 새로 만든다.
	 * <p>
	 * {@code null} 값도 허용한다({@link KeyValueFStream#toListMap()}과 같은 정책). 2026-11-02 이전에는
	 * {@code null} 값을 {@link IllegalArgumentException}으로 거부했다.
	 *
	 * @param key	키.
	 * @param value	더할 값. {@code null}이어도 된다.
	 * @return	이 객체.
	 */
	public KeyedGroups<K,V> add(K key, V value) {
		m_groups.computeIfAbsent(key, k -> Lists.newArrayList())
				.add(value);
		return this;
	}

	/**
	 * 키의 그룹을 제거하고 그 값 목록을 반환한다.
	 *
	 * @param key	키.
	 * @return	제거한 그룹의 값 목록. 키가 없으면 {@link FOption#empty()}.
	 */
	public FOption<List<V>> remove(K key) {
		return FOption.ofNullable(m_groups.remove(key));
	}

	/**
	 * 그룹들을 {@code (키, 값 목록)} 스트림으로 반환한다. 순서는 처음 나온 순서다.
	 * <p>
	 * <b>주의:</b> 값 목록은 복사본이나 읽기 전용 뷰가 아니라 내부 목록 그대로다. 목록을 제자리에서 정렬하는 등의
	 * 기존 사용(예: marmot {@code InMemoryKeyedRecordSetFactory})을 깨지 않기 위해서다. 목록을 수정하면 이 객체의
	 * 그룹도 바뀌므로, 목록을 비우거나 {@code null}로 바꾸지 않는다.
	 *
	 * @return	{@code (키, 값 목록)} 스트림.
	 */
	public KeyValueFStream<K,List<V>> fstream() {
		return KeyValueFStream.from(m_groups);
	}

	/**
	 * 그룹들을 다시 펼쳐 {@code (키, 값)} 스트림으로 반환한다. 순서는 그룹 순서, 그룹 안에서는 값 순서다.
	 *
	 * @return	{@code (키, 값)} 스트림.
	 */
	public KeyValueFStream<K,V> ungroup() {
		return KeyValueFStream.from(m_groups)
						.flatMap(kv -> FStream.from(kv.value())
												.map(v -> KeyValue.of(kv.key(), v)))
						.mapToKeyValue(v -> v);
	}

	@Override
	public String toString() {
		return KeyValueFStream.from(m_groups)
						.map(kv -> String.format("%s:%d", kv.key(), kv.value().size()))
						.join(", ");
	}

	/**
	 * 그룹별로 다른 처리를 하는 {@link MultipleCases}를 만든다. 그룹 맵을 복사해 사용하므로 이 객체에는 영향이 없다.
	 *
	 * @return	{@link MultipleCases} 객체.
	 */
	public MultipleCases<K,List<V>> switcher() {
		return MultipleCases.switching(asMap());
	}
}
