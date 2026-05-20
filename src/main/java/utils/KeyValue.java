package utils;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * 키-값 쌍을 표현하는 불변(immutable) 컨테이너.
 * <p>
 * 키({@code key})는 non-null이며, 값({@code value})은 {@code null}을 허용한다.
 * <p>
 * 인스턴스 생성은 정적 팩토리({@link #of}, {@link #from(Map.Entry)},
 * {@link #from(Tuple)}, {@link #parse(String)})를 사용하는 것이 권장된다.
 * 생성자는 {@code protected}로 노출되어 있으나 별도 확장이 필요한 경우 외에는
 * 직접 호출하지 않는다.
 * <p>
 * 본 클래스는 불변이지만 {@code final}은 아니다. {@link #equals}는 {@code getClass()}
 * 동일성을 요구하므로 서로 다른 하위 클래스 인스턴스는 절대 같다고 판정되지 않는다
 * (Liskov-safe).
 *
 * @param <K> 키의 타입
 * @param <V> 값의 타입
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValue<K,V> implements Keyed<K> {
	@NotNull private final K m_key;
	@Nullable private final V m_value;

	/**
	 * {@link Map.Entry}로부터 {@link KeyValue} 인스턴스를 생성한다.
	 *
	 * @param <K>	키의 타입.
	 * @param <V>	값의 타입.
	 * @param entry	키-값을 추출할 entry. {@code null}이 아니어야 한다.
	 * @return	{@code entry.getKey()} / {@code entry.getValue()}로 구성된 {@code KeyValue} 인스턴스.
	 * @throws IllegalArgumentException	{@code entry}가 {@code null}이거나 {@code entry.getKey()}가 {@code null}인 경우.
	 */
	public static <K,V> KeyValue<K,V> from(Map.Entry<? extends K,? extends V> entry) {
		Preconditions.checkNotNullArgument(entry, "entry is null");

		K key = entry.getKey();
		Preconditions.checkNotNullArgument(key, "entry.getKey() is null");
		return new KeyValue<>(key, entry.getValue());
	}

	/**
	 * {@link Tuple}로부터 {@link KeyValue} 인스턴스를 생성한다.
	 *
	 * @param <K>	키의 타입.
	 * @param <V>	값의 타입.
	 * @param tupl	키-값을 추출할 튜플. {@code null}이 아니어야 한다.
	 * @return	{@code tupl._1} / {@code tupl._2}로 구성된 {@code KeyValue} 인스턴스.
	 * @throws IllegalArgumentException	{@code tupl}이 {@code null}이거나 {@code tupl._1}이 {@code null}인 경우.
	 */
	public static <K,V> KeyValue<K,V> from(Tuple<? extends K,? extends V> tupl) {
		Preconditions.checkNotNullArgument(tupl, "tupl is null");

		K key = tupl._1;
		Preconditions.checkNotNullArgument(key, "tupl._1 is null");
		return new KeyValue<>(key, tupl._2);
	}

	/**
	 * 주어진 키와 값으로 {@link KeyValue} 인스턴스를 생성한다.
	 *
	 * @param <K>	키의 타입.
	 * @param <V>	값의 타입.
	 * @param key	키. {@code null}이 아니어야 한다.
	 * @param value	값. {@code null}을 허용한다.
	 * @return	{@code KeyValue} 인스턴스.
	 * @throws IllegalArgumentException	{@code key}가 {@code null}인 경우.
	 */
	public static <K,V> KeyValue<K,V> of(K key, V value) {
		return new KeyValue<>(key, value);
	}

	/**
	 * 주어진 키와 값으로 {@link KeyValue} 인스턴스를 생성한다.
	 * 일반적으로 정적 팩토리({@link #of}, {@link #from(Map.Entry)} 등)를 사용하는 것이 권장된다.
	 *
	 * @param key	키. {@code null}이 아니어야 한다.
	 * @param value	값. {@code null}을 허용한다.
	 * @throws IllegalArgumentException	{@code key}가 {@code null}인 경우.
	 */
	protected KeyValue(K key, V value) {
		Preconditions.checkNotNullArgument(key, "key is null");

		m_key = key;
		m_value = value;
	}
	
	/**
	 * 키 값을 반환한다.
	 * 
	 * @return 키 값.
	 */
	@Override
	public @NotNull K key() {
		return m_key;
	}
	
	/**
	 * 값을 반환한다.
	 * 
	 * @return 값.
	 */
	public @Nullable V value() {
		return m_value;
	}
	
	/**
	 * 키-값 쌍을 Tuple로 변환한다.
	 * 
	 * @return Tuple 객체.
	 */
	public Tuple<K,V> toTuple() {
		return Tuple.of(m_key, m_value);
	}
	
	/**
	 * 키와 값에 mapper를 적용하여 임의 타입의 결과를 만든다.
	 *
	 * @param <T>		변환 결과 타입.
	 * @param mapper	키와 값을 함께 받는 변환 함수.
	 * @return			{@code mapper.apply(key, value)} 결과.
	 */
	public <T> T map(BiFunction<? super K,? super V,? extends T> mapper) {
		return mapper.apply(m_key, m_value);
	}

	/**
	 * 현재 값은 유지한 채 키만 변환한 새 {@link KeyValue} 인스턴스를 반환한다.
	 *
	 * @param <S>		새 키의 타입.
	 * @param mapper	기존 키를 새 키로 변환하는 함수.
	 * @return			키만 변환된 새 {@code KeyValue} 인스턴스.
	 * @throws NullPointerException	변환된 키가 {@code null}인 경우.
	 */
	public <S> KeyValue<S,V> mapKey(Function<? super K,? extends S> mapper) {
		S mappedKey = mapper.apply(m_key);
		Preconditions.checkNotNull(mappedKey, "mapped key is null");
		return new KeyValue<>(mappedKey, m_value);
	}

	/**
	 * 현재 값은 유지한 채 키만 변환한 새 {@link KeyValue} 인스턴스를 반환한다.
	 * mapper는 키와 값을 함께 입력받으므로, 값에 의존한 키 변환이 필요할 때 사용한다.
	 *
	 * @param <S>		새 키의 타입.
	 * @param mapper	(key, value) → 새 키 변환 함수.
	 * @return			키만 변환된 새 {@code KeyValue} 인스턴스.
	 * @throws NullPointerException	변환된 키가 {@code null}인 경우.
	 */
	public <S> KeyValue<S,V> mapKey(BiFunction<? super K,? super V,? extends S> mapper) {
		S mappedKey = mapper.apply(m_key, m_value);
		Preconditions.checkNotNull(mappedKey, "mapped key is null");
		return new KeyValue<>(mappedKey, m_value);
	}

	/**
	 * 현재 키는 유지한 채 값만 변환한 새 {@link KeyValue} 인스턴스를 반환한다.
	 *
	 * @param <U>		새 값의 타입.
	 * @param mapper	기존 값을 새 값으로 변환하는 함수.
	 * @return			값만 변환된 새 {@code KeyValue} 인스턴스.
	 */
	public <U> KeyValue<K,U> mapValue(Function<? super V,? extends U> mapper) {
		return new KeyValue<>(m_key, mapper.apply(m_value));
	}

	/**
	 * 현재 키는 유지한 채 값만 변환한 새 {@link KeyValue} 인스턴스를 반환한다.
	 * mapper는 키와 값을 함께 입력받으므로, 키에 의존한 값 변환이 필요할 때 사용한다.
	 *
	 * @param <U>		새 값의 타입.
	 * @param mapper	(key, value) → 새 값 변환 함수.
	 * @return			값만 변환된 새 {@code KeyValue} 인스턴스.
	 */
	public <U> KeyValue<K,U> mapValue(BiFunction<? super K,? super V,? extends U> mapper) {
		return new KeyValue<>(m_key, mapper.apply(m_key, m_value));
	}

	/**
	 * {@code "key=value"} 형식의 문자열을 파싱하여 {@link KeyValue} 인스턴스를 생성한다.
	 * <p>
	 * 입력 문자열의 첫번째 {@code '='}를 기준으로 키와 값이 나뉘므로, 값에 {@code '='}가
	 * 포함되어 있어도 그대로 보존된다 (예: {@code "a=b=c"} → key={@code "a"}, value={@code "b=c"}).
	 * 키/값 양쪽의 앞뒤 공백은 제거된다 ({@link String#strip} 기준 — Unicode whitespace 포함).
	 * 값 안쪽 공백은 보존되며, 값이 공백뿐이면 빈 문자열로 정규화된다.
	 *
	 * @param expr		파싱할 표현식. {@code null}이 아니어야 한다.
	 * @return			파싱 결과 {@code KeyValue} 인스턴스.
	 * @throws IllegalArgumentException	{@code expr}이 {@code null}이거나, {@code '='}가
	 * 			없거나, {@code '='} 앞이 비어있거나 공백뿐인 경우.
	 */
	public static KeyValue<String,String> parse(String expr) {
		Preconditions.checkNotNullArgument(expr, "expr is null");

		Split split = Split.split(expr, "=");
		if ( split.tail().isEmpty() ) {
			throw new IllegalArgumentException("invalid key-value: " + expr);
		}
		else if ( split.head().isBlank() ) {
			throw new IllegalArgumentException("invalid key-value (empty key): " + expr);
		}

		return KeyValue.of(split.head().strip(), split.tail().get().strip());
	}
	
	/**
	 * 키-값 쌍의 문자열 표현({@code "key=value"})을 반환한다.
	 * <p>
	 * <b>주의</b>: 본 메소드는 {@link #parse(String)}와의 round-trip을 <em>보장하지 않는다</em>.
	 *
	 * @return {@code "key=value"} 형식의 문자열.
	 */
	@Override
	public String toString() {
		return m_key + "=" + m_value;
	}

	@Override
	public int hashCode() {
		return 31 * m_key.hashCode() + Objects.hashCode(m_value);
	}

	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}

		KeyValue<?,?> other = (KeyValue<?,?>)obj;
		return m_key.equals(other.m_key)
				&& Objects.equals(m_value, other.m_value);
	}
}
