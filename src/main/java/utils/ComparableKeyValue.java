package utils;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 키가 {@link Comparable}인 {@link KeyValue}.
 * <p>
 * {@link #compareTo(ComparableKeyValue)}는 키만 비교하므로, 키가 같고 값이 다른 두 객체는
 * {@code compareTo}로는 같지만 {@code equals}로는 다르다.
 *
 * @param <K>	키의 타입.
 * @param <V>	값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public final class ComparableKeyValue<K extends Comparable<K>,V>
														extends KeyValue<K,V>
														implements Comparable<ComparableKeyValue<K,V>> {
	/**
	 * 키와 값으로 {@link ComparableKeyValue}를 만든다.
	 *
	 * @param <K>	키의 타입.
	 * @param <V>	값의 타입.
	 * @param key	키. {@code null}이 아니어야 한다.
	 * @param value	값. {@code null}을 허용한다.
	 * @return	새 {@link ComparableKeyValue} 객체.
	 * @throws IllegalArgumentException	{@code key}가 {@code null}인 경우.
	 */
	public static <K extends Comparable<K>,V> ComparableKeyValue<K,V> of(K key, V value) {
		return new ComparableKeyValue<>(key, value);
	}

	/**
	 * {@link Map.Entry}의 키와 값으로 {@link ComparableKeyValue}를 만든다.
	 *
	 * @param <K>	키의 타입.
	 * @param <V>	값의 타입.
	 * @param entry	맵 엔트리.
	 * @return	새 {@link ComparableKeyValue} 객체.
	 * @throws NullPointerException	{@code entry}가 {@code null}인 경우.
	 * @throws IllegalArgumentException	엔트리의 키가 {@code null}인 경우.
	 */
	public static <K extends Comparable<K>,V>
	ComparableKeyValue<K,V> fromEntry(Map.Entry<? extends K,? extends V> entry) {
		return new ComparableKeyValue<>(entry.getKey(), entry.getValue());
	}

	/**
	 * {@link Tuple}의 첫 번째 원소를 키로, 두 번째 원소를 값으로 하는 {@link ComparableKeyValue}를 만든다.
	 *
	 * @param <K>	키의 타입.
	 * @param <V>	값의 타입.
	 * @param tupl	튜플.
	 * @return	새 {@link ComparableKeyValue} 객체.
	 * @throws NullPointerException	{@code tupl}이 {@code null}인 경우.
	 * @throws IllegalArgumentException	튜플의 첫 번째 원소가 {@code null}인 경우.
	 */
	public static <K extends Comparable<K>,V>
	ComparableKeyValue<K,V> fromTuple(Tuple<? extends K,? extends V> tupl) {
		return new ComparableKeyValue<>(tupl._1, tupl._2);
	}

	private ComparableKeyValue(K key, V value) {
		super(key, value);
	}

	@Override
	public <U> ComparableKeyValue<K,U> mapValue(Function<? super V,? extends U> mapper) {
		return new ComparableKeyValue<>(key(), mapper.apply(value()));
	}

	@Override
	public <U> ComparableKeyValue<K,U> mapValue(BiFunction<? super K,? super V,? extends U> mapper) {
		return new ComparableKeyValue<>(key(), mapper.apply(key(), value()));
	}

	/**
	 * {@code "key=value"} 형식의 문자열을 {@code '='}로 나누고, 앞뒤 공백을 제거한 키와 값으로
	 * {@link ComparableKeyValue}를 만든다.
	 * <p>
	 * <b>주의:</b> {@code quote}는 이름과 달리 CSV 파서의 escape 문자로 전달된다. 즉 감싼 영역을 만드는
	 * 인용 부호가 아니라, 바로 다음 한 글자를 literal로 취급하게 하는 문자로 동작한다.
	 *
	 * @param expr	파싱할 문자열.
	 * @param quote	CSV 파서에 escape 문자로 전달되는 문자.
	 * @return	파싱된 {@link ComparableKeyValue} 객체.
	 * @throws IllegalArgumentException	{@code expr}이 {@code null}이거나, {@code '='}로 나눈 토큰이 정확히 2개가 아니거나,
	 * 			{@code quote}가 {@code '='}이거나, 문자열이 escape 문자로 끝나는 경우.
	 */
	public static ComparableKeyValue<String,String> parse(String expr, char quote) {
		List<String> parts = CSV.parseCsv(expr, '=', quote)
								.map(String::trim)
								.toList();
		if ( parts.size() != 2 ) {
			throw new IllegalArgumentException("invalid key-value: " + expr);
		}

		return ComparableKeyValue.of(parts.get(0), parts.get(1));
	}

	/**
	 * {@code "key=value"} 형식의 문자열을 {@code '='}로 나누고, 앞뒤 공백을 제거한 키와 값으로
	 * {@link ComparableKeyValue}를 만든다. escape나 quote는 처리하지 않는다.
	 *
	 * @param expr	파싱할 문자열.
	 * @return	파싱된 {@link ComparableKeyValue} 객체.
	 * @throws IllegalArgumentException	{@code expr}이 {@code null}이거나, {@code '='}로 나눈 토큰이 정확히 2개가 아닌 경우
	 * 			(값에 {@code '='}가 들어 있는 경우 포함).
	 */
	public static ComparableKeyValue<String,String> parse(String expr) {
		List<String> parts = CSV.parseCsv(expr, '=')
								.map(String::trim)
								.toList();
		if ( parts.size() != 2 ) {
			throw new IllegalArgumentException("invalid key-value: " + expr);
		}

		return ComparableKeyValue.of(parts.get(0), parts.get(1));
	}

	@Override
	public int compareTo(ComparableKeyValue<K, V> o) {
		return key().compareTo(o.key());
	}
}
