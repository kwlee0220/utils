package utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * {@link java.time.LocalDateTime}과 {@link java.time.Instant}·epoch milliseconds·문자열 사이의 변환을 제공한다.
 * 시간대를 인자로 받는 메소드가 기본형이고, 시간대가 없는 메소드는 호출할 때마다 읽은 시스템 기본 시간대를 쓴다.
 * 서머타임 전환으로 없는 시각은 전환 폭만큼 뒤로 밀리고, 두 번 있는 시각은 앞쪽 오프셋으로 해석한다.
 * <p>
 * 객체 인자가 {@code null}이면 {@link IllegalArgumentException}을 던진다.
 * <p>
 * 2026-11-01에 {@code fromString} → {@code parse}, {@code toEpochMillis}/{@code fromEpochMillis} →
 * {@code toEpochMilli}/{@code fromEpochMilli}로 이름을 바꿨다(별칭 없음). {@code toInstantWithoutTimeZone(ldt)}는
 * 이름과 달리 UTC로 해석했으므로 지웠다({@code toInstant(ldt, ZoneOffset.UTC)}를 쓴다). {@code toUtcMillis}/
 * {@code fromUtcMillis}도 지웠다({@code toEpochMilli(ldt, ZoneOffset.UTC)}/{@code fromEpochMilli(ms, ZoneOffset.UTC)}를
 * 쓴다).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalDateTimes {
	private LocalDateTimes() {
		throw new AssertionError("should not be called: " + getClass());
	}

	/**
	 * 주어진 시각을 지정된 시간대로 변환된 {@link LocalDateTime} 객체를 생성한다.
	 *
	 * @param instant	변환할 시간.
	 * @param zone		변환할 시간대.
	 * @return 변환된 {@link LocalDateTime} 객체.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static LocalDateTime fromInstant(Instant instant, ZoneId zone) {
		Preconditions.checkNotNullArgument(instant, "instant is null");
		Preconditions.checkNotNullArgument(zone, "zone is null");

		return LocalDateTime.ofInstant(instant, zone);
	}

	/**
	 * 주어진 시각을 현재 시스템의 시간대로 변환된 {@link LocalDateTime} 객체를 생성한다.
	 * {@code fromInstant(instant, ZoneId.systemDefault())}와 같다.
	 *
	 * @param instant 변환할 시간.
	 * @return 변환된 {@link LocalDateTime} 객체.
	 * @throws IllegalArgumentException	{@code instant}가 {@code null}인 경우.
	 */
	public static LocalDateTime fromInstant(Instant instant) {
		return fromInstant(instant, ZoneId.systemDefault());
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
		return fromInstant(Instant.ofEpochMilli(epochMilli), zone);
	}

	/**
	 * 주어진 {@code LocalDateTime} 객체를 시스템 기본 시간대로 변환된 {@link Instant} 객체를 생성한다.
	 * {@code toInstant(ldt, ZoneId.systemDefault())}와 같다(서머타임 전환 시각의 처리도 같다).
	 *
	 * @param ldt 변환할 시간.
	 * @return 변환된 {@link Instant} 객체.
	 * @throws IllegalArgumentException	{@code ldt}가 {@code null}인 경우.
	 */
	public static Instant toInstant(LocalDateTime ldt) {
		return toInstant(ldt, ZoneId.systemDefault());
	}

	/**
	 * 주어진 {@code LocalDateTime} 객체를 시스템 기본 시간대의 시각으로 보고 epoch milliseconds로 변환한다.
	 * {@code toEpochMilli(ldt, ZoneId.systemDefault())}와 같다(서머타임 전환 시각의 처리도 같다).
	 * 밀리초 미만(마이크로초·나노초)은 버린다.
	 *
	 * @param ldt 변환할 시간.
	 * @return epoch milliseconds 값.
	 * @throws IllegalArgumentException	{@code ldt}가 {@code null}인 경우.
	 * @throws ArithmeticException	epoch milliseconds({@code long})로 나타낼 수 없는 먼 시각인 경우(약 ±2억 9천만 년 밖).
	 */
	public static long toEpochMilli(LocalDateTime ldt) {
		return toEpochMilli(ldt, ZoneId.systemDefault());
	}

	/**
	 * 주어진 epoch milliseconds를 시스템 기본 시간대의 {@code LocalDateTime} 객체로 변환한다.
	 * {@code fromEpochMilli(millis, ZoneId.systemDefault())}와 같다.
	 *
	 * @param millis 변환할 epoch milliseconds 값.
	 * @return 변환된 {@code LocalDateTime} 객체.
	 */
	public static LocalDateTime fromEpochMilli(long millis) {
		return fromEpochMilli(millis, ZoneId.systemDefault());
	}

	/**
	 * 주어진 ISO-8601 시간 문자열을 파싱하여 {@code LocalDateTime} 객체로 변환한다.
	 * <p>
	 * {@link LocalDateTime#parse(CharSequence)}와 같다. 형식은 {@code yyyy-MM-ddTHH:mm[:ss[.fraction]]}이며,
	 * 초와 소수점 이하(최대 9자리)는 생략할 수 있다. 시간대나 오프셋은 받지 않는다.
	 * (예: {@code 2024-07-10T14:40}, {@code 2024-07-10T14:40:37}, {@code 2024-07-10T14:40:37.123456})
	 *
	 * @param str 변환할 문자열.
	 * @return 변환된 {@code LocalDateTime} 객체.
	 * @throws IllegalArgumentException	{@code str}이 {@code null}인 경우.
	 * @throws java.time.format.DateTimeParseException	형식이 맞지 않는 경우.
	 */
	public static LocalDateTime parse(String str) {
		Preconditions.checkNotNullArgument(str, "string is null");

		return LocalDateTime.parse(str);
	}

	/**
	 * 주어진 {@code LocalDateTime} 객체를 ISO-8601 문자열로 변환한다.
	 * <p>
	 * {@link LocalDateTime#toString()}과 같다. 형식은 정밀도에 따라 달라진다: 초와 소수점 이하가 모두 0이면
	 * {@code yyyy-MM-ddTHH:mm}, 소수점 이하만 0이면 {@code yyyy-MM-ddTHH:mm:ss}, 그 밖에는 소수점 이하를
	 * 3·6·9자리 중 값을 담을 수 있는 가장 짧은 길이로 붙인다.
	 * (예: {@code 2024-07-10T14:40}, {@code 2024-07-10T14:40:37}, {@code 2024-07-10T14:40:37.120})
	 * 2026-11-01 이전의 문서는 항상 {@code yyyy-MM-ddTHH:mm:ss} 형식이라고 잘못 적고 있었다.
	 *
	 * @param ldt 변환할 시간.
	 * @return 변환된 문자열.
	 * @throws IllegalArgumentException	{@code ldt}가 {@code null}인 경우.
	 */
	public static String toString(LocalDateTime ldt) {
		Preconditions.checkNotNullArgument(ldt, "LocalDateTime is null");

		return ldt.toString();
	}
}
