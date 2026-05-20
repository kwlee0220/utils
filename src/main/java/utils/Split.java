package utils;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 문자열을 구분자 기준으로 한 번 분할한 결과({@code head}, optional {@code tail})를 표현하는
 * 불변 값 객체이다.
 * <p>
 * 분할은 항상 <b>처음 등장하는 구분자</b>를 기준으로 한다. 분할 결과는 {@link #split(String, String)}
 * 정적 팩토리로 생성하며, 결과 객체에서 {@link #head()} / {@link #tail()}로 부분을 조회하거나
 * {@link #splitTail(String)}로 tail을 다시 분할할 수 있다.
 * <p>
 * Java {@code record}로 정의되어 있어 {@code equals}, {@code hashCode}, {@code toString}이
 * 자동 제공된다.
 *
 * @param head	구분자 직전까지의 부분 문자열. 구분자가 없었던 경우 원본 전체.
 * @param tail	구분자 직후부터의 부분 문자열을 감싼 {@link Optional}.
 *				구분자가 없었던 경우 {@link Optional#empty()}.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public record Split(String head, Optional<String> tail) {

	/**
	 * 주어진 문자열을 구분자 기준으로 처음 등장하는 위치에서 한 번 분할한다.
	 * <p>
	 * 동작은 다음과 같다.
	 * <ul>
	 *   <li>{@code delim}이 {@code str}에 없으면 head는 {@code str} 전체, tail은
	 *       {@link Optional#empty()}.</li>
	 *   <li>{@code delim}이 발견되면 head는 첫 등장 직전까지, tail은 첫 등장 직후부터의 부분 문자열.
	 *       구분자는 결과 어느 쪽에도 포함되지 않는다.</li>
	 *   <li>구분자가 문자열 끝에 위치하면 tail은 {@link Optional#of}({@code ""}) (빈 문자열을
	 *       감싼 Optional)이 된다 — {@code empty}와 구분된다.</li>
	 * </ul>
	 *
	 * @param str	분할 대상 문자열. {@code null}이면 안 된다.
	 * @param delim	구분자 문자열. {@code null} 또는 빈 문자열이면 안 된다.
	 * @return	분할 결과를 담은 {@code Split} 객체.
	 * @throws IllegalArgumentException	{@code str} 또는 {@code delim}이 {@code null}이거나
	 *									{@code delim}이 빈 문자열인 경우.
	 */
	public static Split split(String str, String delim) {
		Preconditions.checkNotNullArgument(str, "str must not be null");
		Preconditions.checkNotNullArgument(delim, "delim must not be null");
		Preconditions.checkArgument(!delim.isEmpty(), "delim must not be empty");

		int idx = str.indexOf(delim);
		if ( idx < 0 ) {
			return new Split(str, Optional.empty());
		}
		else {
			return new Split(str.substring(0, idx), Optional.of(str.substring(idx + delim.length())));
		}
	}
	
	/**
	 * 주어진 문자열을 구분자가 <b>마지막으로 등장하는 위치</b>를 기준으로 한 번 분할한다.
	 * <p>
	 * {@link #split(String, String)}이 첫 등장 위치 기준이라면, 본 메소드는 마지막 등장 위치 기준이다.
	 * <ul>
	 *   <li>{@code delim}이 {@code str}에 없으면 head는 {@code str} 전체, tail은 {@link Optional#empty()}.</li>
	 *   <li>{@code delim}이 발견되면 head는 마지막 등장 직전까지, tail은 마지막 등장 직후부터의
	 *       부분 문자열. 구분자는 결과 어느 쪽에도 포함되지 않는다.</li>
	 *   <li>구분자가 문자열 끝에 위치하면 tail은 {@link Optional#of}({@code ""}).</li>
	 * </ul>
	 *
	 * @param str	분할 대상 문자열. {@code null}이면 안 된다.
	 * @param delim	구분자 문자열. {@code null} 또는 빈 문자열이면 안 된다.
	 * @return	분할 결과를 담은 {@code Split} 객체.
	 * @throws IllegalArgumentException	{@code str} 또는 {@code delim}이 {@code null}이거나
	 *									{@code delim}이 빈 문자열인 경우.
	 */
	public static Split splitLast(String str, String delim) {
		Preconditions.checkNotNullArgument(str, "str must not be null");
		Preconditions.checkNotNullArgument(delim, "delim must not be null");
		Preconditions.checkArgument(!delim.isEmpty(), "delim must not be empty");

		int delimIndex = str.lastIndexOf(delim);
		if ( delimIndex >= 0 ) {
			return new Split(str.substring(0, delimIndex),
								Optional.of(str.substring(delimIndex + delim.length())));
		}
		else {
			return new Split(str, Optional.empty());
		}
	}

	/**
	 * 현재 분할의 tail을 같은 의미로 다시 분할한다.
	 * <p>
	 * 즉, {@code split(tail().get(), delim)}을 수행하는 것과 동등하다.
	 * 예를 들어 {@code "a/b/c"}를 {@code "/"}로 분할한 뒤 결과를 다시 {@code "/"}로
	 * splitTail하면 {@code head="b"}, {@code tail=Optional.of("c")} 가 된다.
	 *
	 * @param delim	tail을 분할할 구분자. {@code null} 또는 빈 문자열이면 안 된다.
	 * @return	tail을 다시 분할한 새 {@code Split} 객체.
	 * @throws NoSuchElementException	tail이 존재하지 않는 경우 (즉, 원래 분할에서 구분자가
	 *									발견되지 않았던 경우).
	 * @throws IllegalArgumentException	{@code delim}이 {@code null} 또는 빈 문자열인 경우.
	 */
	public Split splitTail(String delim) {
		Preconditions.checkNotNullArgument(delim, "delim must not be null");
		Preconditions.checkArgument(!delim.isEmpty(), "delim must not be empty");
		
		return tail.map(str -> split(str, delim))
					.orElseThrow(() -> new NoSuchElementException("tail is absent"));
	}
	
	public KeyValue<String, String> toKeyValue() {
		String value = tail.orElseThrow(() -> new IllegalArgumentException("invalid split: tail is absent"));
		return new KeyValue<>(head, value);
	}
}
