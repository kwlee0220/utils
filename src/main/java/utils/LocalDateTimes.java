package utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * {@link java.time.LocalDateTime}과 {@link java.time.Instant}·epoch 밀리초·문자열 사이의 변환을 제공한다.
 * 시간대가 필요한 메소드는 시간대를 인자로 받는다. 시스템 기본 시간대를 쓰려면 {@link ZoneId#systemDefault()}를 넘긴다.
 * 서머타임 전환으로 없는 시각은 전환 폭만큼 뒤로 밀리고, 두 번 있는 시각은 앞쪽 오프셋으로 해석한다.
 * <p>
 * 객체 인자가 {@code null}이면 {@link IllegalArgumentException}을 던진다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalDateTimes {
	/** 날짜와 시각을 공백 하나로 구분한 표기({@code yyyy-MM-dd HH:mm...}). */
	private static final Pattern SPACE_SEPARATED_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d.*");

	private LocalDateTimes() {
		throw new AssertionError("should not be called: " + getClass());
	}

	/**
	 * 주어진 시각을 지정한 시간대의 {@link LocalDateTime}으로 변환한다.
	 * <p>
	 * {@link LocalDateTime#ofInstant(Instant, ZoneId)}와 같고, 인자가 {@code null}이면 {@link NullPointerException}
	 * 대신 {@link IllegalArgumentException}을 던진다.
	 *
	 * @param instant	변환할 시간.
	 * @param zone		변환할 시간대.
	 * @return 변환된 {@link LocalDateTime} 객체.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static LocalDateTime ofInstant(Instant instant, ZoneId zone) {
		Preconditions.checkNotNullArgument(instant, "instant is null");
		Preconditions.checkNotNullArgument(zone, "zone is null");

		return LocalDateTime.ofInstant(instant, zone);
	}

	/**
	 * 주어진 {@code LocalDateTime}을 지정한 시간대의 시각으로 보고 {@link Instant}로 변환한다.
	 * <p>
	 * 서머타임 전환으로 그 시간대에 없는 시각(gap)은 전환 폭만큼 뒤로 밀리고, 두 번 있는 시각(overlap)은
	 * 앞쪽 오프셋으로 해석한다({@link java.time.ZonedDateTime#of(LocalDateTime, ZoneId)}의 규칙).
	 *
	 * @param ldt	변환할 시간.
	 * @param zone	{@code ldt}를 해석할 시간대.
	 * @return 변환된 {@link Instant} 객체.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static Instant toInstant(LocalDateTime ldt, ZoneId zone) {
		Preconditions.checkNotNullArgument(ldt, "LocalDateTime is null");
		Preconditions.checkNotNullArgument(zone, "zone is null");

		return ldt.atZone(zone).toInstant();
	}

	/**
	 * 주어진 {@code LocalDateTime}을 지정한 시간대의 시각으로 보고 epoch milliseconds로 변환한다.
	 * <p>
	 * 서머타임 전환 시각의 처리는 {@link #toInstant(LocalDateTime, ZoneId)}와 같다.
	 * 밀리초 미만(마이크로초·나노초)은 버리므로, {@link #fromEpochMilli(long, ZoneId)}로 되돌려도 원래 값과 다를 수
	 * 있다. 나노초까지 유지하려면 {@link #toInstant(LocalDateTime, ZoneId)}를 쓴다.
	 *
	 * @param ldt	변환할 시간.
	 * @param zone	{@code ldt}를 해석할 시간대.
	 * @return epoch milliseconds 값.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 * @throws ArithmeticException	epoch milliseconds({@code long})로 나타낼 수 없는 먼 시각인 경우(약 ±2억 9천만 년 밖).
	 */
	public static long toEpochMilli(LocalDateTime ldt, ZoneId zone) {
		return toInstant(ldt, zone).toEpochMilli();
	}

	/**
	 * 주어진 epoch milliseconds를 지정한 시간대의 {@code LocalDateTime}으로 변환한다.
	 *
	 * @param epochMilli	변환할 epoch milliseconds 값.
	 * @param zone			변환할 시간대.
	 * @return 변환된 {@code LocalDateTime} 객체.
	 * @throws IllegalArgumentException	{@code zone}이 {@code null}인 경우.
	 */
	public static LocalDateTime fromEpochMilli(long epochMilli, ZoneId zone) {
		return ofInstant(Instant.ofEpochMilli(epochMilli), zone);
	}

	/**
	 * 주어진 ISO-8601 시간 문자열을 파싱하여 {@code LocalDateTime} 객체로 변환한다.
	 * <p>
	 * 형식은 {@code yyyy-MM-ddTHH:mm[:ss[.fraction]]}이며({@link LocalDateTime#parse(CharSequence)}), 초와 소수점
	 * 이하(최대 9자리)는 생략할 수 있다. {@link Instants#parse(String, ZoneId)}처럼 {@code T} 대신 공백 하나로 날짜와 시각을
	 * 구분할 수 있고, 앞뒤 공백은 무시한다. 시간대나 오프셋은 받지 않는다.
	 * (예: {@code 2024-07-10T14:40}, {@code 2024-07-10 14:40:37}, {@code 2024-07-10T14:40:37.123456})
	 *
	 * @param str 변환할 문자열.
	 * @return 변환된 {@code LocalDateTime} 객체.
	 * @throws IllegalArgumentException	{@code str}이 {@code null}인 경우.
	 * @throws java.time.format.DateTimeParseException	형식이 맞지 않는 경우.
	 */
	public static LocalDateTime parse(String str) {
		Preconditions.checkNotNullArgument(str, "string is null");

		str = str.strip();
		if ( SPACE_SEPARATED_PATTERN.matcher(str).matches() ) {
			str = str.substring(0, 10) + 'T' + str.substring(11);
		}
		return LocalDateTime.parse(str);
	}

	/**
	 * 주어진 {@code LocalDateTime} 객체를 ISO-8601 문자열로 변환한다.
	 * <p>
	 * {@link LocalDateTime#toString()}과 같다. 형식은 정밀도에 따라 달라진다: 초와 소수점 이하가 모두 0이면
	 * {@code yyyy-MM-ddTHH:mm}, 소수점 이하만 0이면 {@code yyyy-MM-ddTHH:mm:ss}, 그 밖에는 소수점 이하를
	 * 3·6·9자리 중 값을 담을 수 있는 가장 짧은 길이로 붙인다.
	 * (예: {@code 2024-07-10T14:40}, {@code 2024-07-10T14:40:37}, {@code 2024-07-10T14:40:37.120})
	 *
	 * @param ldt 변환할 시간.
	 * @return 변환된 문자열.
	 * @throws IllegalArgumentException	{@code ldt}가 {@code null}인 경우.
	 */
	public static String format(LocalDateTime ldt) {
		Preconditions.checkNotNullArgument(ldt, "LocalDateTime is null");

		return ldt.toString();
	}

	/**
	 * 시각을 나타내는 객체를 지정한 시간대의 {@link LocalDateTime}으로 변환한다.
	 * <p>
	 * {@link LocalDateTime}은 그대로, {@link java.time.LocalDate}는 그날 자정으로 반환한다. 그 밖에는
	 * {@link Instants#from(Object, ZoneId)}가 받는 값을 같은 규칙으로 받아 {@code zone}의 지역 시각으로 바꾼다.
	 * 이 클래스의 다른 메소드와 달리 {@code obj}가 {@code null}이거나 빈 문자열이면 {@code null}을 돌려준다.
	 *
	 * @param obj	변환할 객체.
	 * @param zone	시각을 지역 시각으로 바꿀 시간대.
	 * @return	변환된 지역 시각. {@code obj}가 {@code null}이거나 빈 문자열이면 {@code null}.
	 * @throws IllegalArgumentException	{@code zone}이 {@code null}이거나, 받지 않는 타입이거나, 문자열 형식이 맞지 않는
	 * 									경우.
	 */
	public static LocalDateTime from(Object obj, ZoneId zone) {
		Preconditions.checkNotNullArgument(zone, "zone is null");

		if ( obj instanceof LocalDateTime ldt ) {
			return ldt;
		}
		else if ( obj instanceof java.time.LocalDate ld ) {
			return ld.atStartOfDay();
		}
		else {
			Instant instant = Instants.from(obj, zone);
			return (instant != null) ? LocalDateTime.ofInstant(instant, zone) : null;
		}
	}
}
