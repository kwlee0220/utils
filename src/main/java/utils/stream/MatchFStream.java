package utils.stream;

import java.util.Map;
import java.util.function.Function;

import utils.KeyValue;
import utils.func.FOption;
import utils.stream.KeyValueFStreams.AbstractKeyValueFStream;


/**
 * 원본 스트림의 각 원소를 키로 조회하여 {@code (키, 조회 값)} 쌍을 반환하는 스트림({@link FStream#lookup}).
 * <p>
 * 일치 판정은 조회 방식에 따라 다르다.
 * <ul>
 *   <li>{@link Map}으로 조회하면 <b>키의 존재</b>({@link Map#containsKey(Object)})로 판단한다. 값이 {@code null}인 키도
 *       일치하며 {@code (키, null)}을 낸다. {@link KeyValueFStream#match(Map)}와 같은 기준이다.</li>
 *   <li>조회 함수로 조회하면 키의 존재를 알 수 없으므로, 함수가 {@code null}을 반환하면 일치하지 않은 것으로 본다.</li>
 * </ul>
 * 일치하지 않은 원소는 버리고, {@code keepUnmatched}가 {@code true}이면 {@code (키, null)}로 유지한다. 그래서
 * {@code keepUnmatched}일 때 {@link Map}에서 값이 {@code null}인 키와 맵에 없는 키는 결과에서 구별되지 않는다.
 * <p>
 * 원본 스트림은 이 스트림을 닫을 때 함께 닫힌다.
 *
 * @param <K>	키(원본 스트림의 원소) 타입.
 * @param <V>	조회 값 타입.
 * @author Kang-Woo Lee (ETRI)
 */
class MatchFStream<K,V> extends AbstractKeyValueFStream<K,V> {
	private final FStream<K> m_stream;
	private final Function<? super K,FOption<V>> m_matcher;	// 일치하면 값(null 가능)을, 아니면 empty
	private final boolean m_keepUnmatched;

	/**
	 * 조회 함수로 일치를 판단하는 스트림을 생성한다.
	 *
	 * @param stream		원본 스트림. 이 스트림을 닫으면 함께 닫힌다.
	 * @param lookup		키로 값을 조회하는 함수. 일치하는 값이 없으면 {@code null}을 반환한다.
	 * @param keepUnmatched	일치하지 않은 원소를 {@code (키, null)}로 유지할지 여부.
	 */
	MatchFStream(FStream<K> stream, Function<? super K,? extends V> lookup, boolean keepUnmatched) {
		this(stream, keepUnmatched, k -> FOption.ofNullable(lookup.apply(k)));
	}

	/**
	 * 참조 맵으로 일치를 판단하는 스트림을 생성한다.
	 * <p>
	 * 키가 맵에 있으면({@link Map#containsKey(Object)}) 값이 {@code null}이어도 일치로 본다.
	 *
	 * @param stream		원본 스트림. 이 스트림을 닫으면 함께 닫힌다.
	 * @param lut			키로 찾을 참조 맵. {@code null} 값을 가진 키도 허용한다.
	 * @param keepUnmatched	일치하지 않은 원소를 {@code (키, null)}로 유지할지 여부.
	 */
	MatchFStream(FStream<K> stream, Map<? super K,? extends V> lut, boolean keepUnmatched) {
		this(stream, keepUnmatched, k -> {
			V value = lut.get(k);
			// 값이 null일 때만 키의 존재를 따로 확인한다(한 번의 조회로 끝나는 경우가 대부분).
			return (value != null || lut.containsKey(k)) ? FOption.of(value) : FOption.empty();
		});
	}

	private MatchFStream(FStream<K> stream, boolean keepUnmatched, Function<? super K,FOption<V>> matcher) {
		m_stream = stream;
		m_matcher = matcher;
		m_keepUnmatched = keepUnmatched;
	}

	// 중간 연산이므로 원본을 닫는다(패키지 닫기 규약 2번).
	@Override
	protected void closeInGuard() throws Exception {
		m_stream.close();
	}

	@Override
	protected FOption<KeyValue<K,V>> nextInGuard() {
		// 원본에서 키를 하나씩 읽어 조회한다. 일치하지 않으면 keepUnmatched가 아닌 한 버리고 다음 키를 읽는다.
		while ( true ) {
			FOption<K> onext = m_stream.next();
			if ( onext.isAbsent() ) {
				return FOption.empty();
			}

			K key = onext.getUnchecked();
			FOption<V> matched = m_matcher.apply(key);
			if ( matched.isPresent() ) {
				return FOption.of(KeyValue.of(key, matched.getUnchecked()));
			}
			if ( m_keepUnmatched ) {
				return FOption.of(KeyValue.of(key, null));
			}
		}
	}
}
