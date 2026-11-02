package utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * {@link java.time.LocalDate}와 {@link java.time.Instant}·epoch milliseconds 사이의 변환을 제공한다.
 * 시간대를 인자로 받는 메소드가 기본형이고, 시간대가 없는 메소드는 호출할 때마다 읽은 시스템 기본 시간대를 쓴다.
 * 그날의 시작 시각은 보통 자정이지만, 서머타임 전환으로 자정이 없는 날에는 자정 이후 첫 유효 시각이다.
 * 날짜변경선 이동처럼 날짜 하나가 통째로 없는 경우에는 그 시각이 다음 날짜에 속하므로, 날짜로 되돌리면 원래 날짜와
 * 다르다(예: {@code Pacific/Apia}의 {@code 2011-12-30}은 되돌리면 {@code 2011-12-31}).
 * <p>
 * 객체 인자가 {@code null}이면 {@link IllegalArgumentException}을 던진다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalDates {
	private LocalDates() {
		throw new AssertionError("should not be called: " + getClass());
	}

	/**
	 * 주어진 날짜가 지정한 시간대에서 시작되는 시각을 구한다.
	 * <p>
	 * 그날의 시작은 보통 자정이지만, 서머타임 전환으로 자정이 없는 날에는 자정 이후 첫 유효 시각이다
	 * ({@link LocalDate#atStartOfDay(ZoneId)}의 규칙). 그 시간대에 날짜 자체가 없으면(날짜변경선 이동 등) 그 시각은
	 * 다음 날짜에 속하므로 {@link #fromInstant(Instant, ZoneId)}로 되돌리면 다른 날짜가 된다
	 * (예: {@code Pacific/Apia}의 {@code 2011-12-30} → {@code 2011-12-31}).
	 *
	 * @param date	변환할 날짜.
	 * @param zone	날짜를 해석할 시간대.
	 * @return	그날이 시작되는 시각.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static Instant toInstant(LocalDate date, ZoneId zone) {
		Preconditions.checkNotNullArgument(date, "LocalDate is null");
		Preconditions.checkNotNullArgument(zone, "zone is null");

		return date.atStartOfDay(zone).toInstant();
	}

	/**
	 * 주어진 시각이 지정한 시간대에서 속하는 날짜를 구한다.
	 *
	 * @param instant	변환할 시각.
	 * @param zone		날짜를 구할 시간대.
	 * @return	날짜.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static LocalDate fromInstant(Instant instant, ZoneId zone) {
		Preconditions.checkNotNullArgument(instant, "instant is null");
		Preconditions.checkNotNullArgument(zone, "zone is null");

		return LocalDate.ofInstant(instant, zone);
	}

	/**
	 * 주어진 날짜가 지정한 시간대에서 시작되는 시각을 epoch milliseconds로 변환한다.
	 * <p>
	 * 그날의 시작은 {@link #toInstant(LocalDate, ZoneId)}와 같다.
	 *
	 * @param date	변환할 날짜.
	 * @param zone	날짜를 해석할 시간대.
	 * @return	epoch milliseconds 값.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 * @throws ArithmeticException	epoch milliseconds({@code long})로 나타낼 수 없는 먼 날짜인 경우(약 ±2억 9천만 년 밖).
	 */
	public static long toEpochMilli(LocalDate date, ZoneId zone) {
		return toInstant(date, zone).toEpochMilli();
	}

	/**
	 * 주어진 epoch milliseconds가 지정한 시간대에서 속하는 날짜를 구한다.
	 *
	 * @param epochMilli	변환할 epoch milliseconds 값.
	 * @param zone			날짜를 구할 시간대.
	 * @return	날짜.
	 * @throws IllegalArgumentException	{@code zone}이 {@code null}인 경우.
	 */
	public static LocalDate fromEpochMilli(long epochMilli, ZoneId zone) {
		return fromInstant(Instant.ofEpochMilli(epochMilli), zone);
	}

	/**
	 * 주어진 epoch milliseconds가 시스템 기본 시간대에서 속하는 날짜를 구한다.
	 * {@code fromEpochMilli(millis, ZoneId.systemDefault())}와 같다.
	 *
	 * @param millis	변환할 epoch milliseconds 값.
	 * @return	날짜.
	 */
	public static LocalDate fromEpochMilli(long millis) {
		return fromEpochMilli(millis, ZoneId.systemDefault());
	}

	/**
	 * 주어진 날짜가 시스템 기본 시간대에서 시작되는 시각을 epoch milliseconds로 변환한다.
	 * {@code toEpochMilli(date, ZoneId.systemDefault())}와 같다.
	 * <p>
	 * 그날의 시작은 {@link #toInstant(LocalDate, ZoneId)}와 같다(서머타임 전환과 날짜가 통째로 없는 경우의 처리 포함).
	 *
	 * @param date	변환할 날짜.
	 * @return	epoch milliseconds 값.
	 * @throws IllegalArgumentException	{@code date}가 {@code null}인 경우.
	 * @throws ArithmeticException	epoch milliseconds({@code long})로 나타낼 수 없는 먼 날짜인 경우(약 ±2억 9천만 년 밖).
	 */
	public static long toEpochMilli(LocalDate date) {
		return toEpochMilli(date, ZoneId.systemDefault());
	}
}
