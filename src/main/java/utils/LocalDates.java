package utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * {@link java.time.LocalDate}와 {@link java.time.Instant}·epoch 밀리초 사이의 변환을 제공한다.
 * 모든 메소드는 시간대를 인자로 받는다. 시스템 기본 시간대를 쓰려면 {@link ZoneId#systemDefault()}를 넘긴다.
 * 그날의 시작 시각은 보통 자정이지만, 서머타임 전환으로 자정이 없는 날에는 자정 이후 첫 유효 시각이다.
 * 날짜변경선 이동처럼 날짜 하나가 통째로 없는 경우에는 그 시각이 다음 날짜에 속하므로, 날짜로 되돌리면 원래 날짜와
 * 다르다(예: {@code Pacific/Apia}의 {@code 2011-12-30}은 되돌리면 {@code 2011-12-31}).
 * <p>
 * 객체 인자가 {@code null}이면 {@link IllegalArgumentException}을 던진다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalDates {
	/** 날짜만 있는 표기({@code yyyy-MM-dd}). */
	private static final Pattern DATE_ONLY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private LocalDates() {
		throw new AssertionError("should not be called: " + getClass());
	}

	/**
	 * 주어진 날짜가 지정한 시간대에서 시작되는 시각을 구한다.
	 * <p>
	 * 그날의 시작은 보통 자정이지만, 서머타임 전환으로 자정이 없는 날에는 자정 이후 첫 유효 시각이다
	 * ({@link LocalDate#atStartOfDay(ZoneId)}의 규칙). 그 시간대에 날짜 자체가 없으면(날짜변경선 이동 등) 그 시각은
	 * 다음 날짜에 속하므로 {@link #ofInstant(Instant, ZoneId)}로 되돌리면 다른 날짜가 된다
	 * (예: {@code Pacific/Apia}의 {@code 2011-12-30} → {@code 2011-12-31}).
	 *
	 * @param date	변환할 날짜.
	 * @param zone	날짜를 해석할 시간대.
	 * @return	그날이 시작되는 시각.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static Instant startOfDay(LocalDate date, ZoneId zone) {
		Preconditions.checkNotNullArgument(date, "LocalDate is null");
		Preconditions.checkNotNullArgument(zone, "zone is null");

		return date.atStartOfDay(zone).toInstant();
	}

	/**
	 * 주어진 시각이 지정한 시간대에서 속하는 날짜를 구한다.
	 * <p>
	 * {@link LocalDate#ofInstant(Instant, ZoneId)}와 같고, 인자가 {@code null}이면 {@link NullPointerException} 대신
	 * {@link IllegalArgumentException}을 던진다.
	 *
	 * @param instant	변환할 시각.
	 * @param zone		날짜를 구할 시간대.
	 * @return	날짜.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static LocalDate ofInstant(Instant instant, ZoneId zone) {
		Preconditions.checkNotNullArgument(instant, "instant is null");
		Preconditions.checkNotNullArgument(zone, "zone is null");

		return LocalDate.ofInstant(instant, zone);
	}

	/**
	 * 주어진 날짜가 지정한 시간대에서 시작되는 시각을 epoch milliseconds로 변환한다.
	 * <p>
	 * 그날의 시작은 {@link #startOfDay(LocalDate, ZoneId)}와 같다.
	 *
	 * @param date	변환할 날짜.
	 * @param zone	날짜를 해석할 시간대.
	 * @return	epoch milliseconds 값.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 * @throws ArithmeticException	epoch milliseconds({@code long})로 나타낼 수 없는 먼 날짜인 경우(약 ±2억 9천만 년 밖).
	 */
	public static long startOfDayEpochMilli(LocalDate date, ZoneId zone) {
		return startOfDay(date, zone).toEpochMilli();
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
		return ofInstant(Instant.ofEpochMilli(epochMilli), zone);
	}


	/**
	 * 날짜를 나타내는 객체를 {@link LocalDate}로 변환한다.
	 * <p>
	 * {@link LocalDate}는 그대로, {@link java.sql.Date}는 그 날짜로, {@link java.time.LocalDateTime}은 날짜 부분으로
	 * 바꾼다. 날짜만 있는 문자열({@code yyyy-MM-dd})은 그 날짜다. 그 밖의 값(시각 문자열 포함)은
	 * {@link LocalDateTimes#from(Object, ZoneId)}처럼 해석한 지역 시각의 날짜다. 날짜가 없는 {@link java.sql.Time}과
	 * {@link java.time.LocalTime}은 받지 않는다. JDBC에 넘길 {@link java.sql.Date}가 필요하면
	 * {@link java.sql.Date#valueOf(LocalDate)}를 쓴다.
	 * <p>
	 * 이 클래스의 다른 메소드와 달리 {@code obj}가 {@code null}이거나 빈 문자열이면 {@code null}을 돌려준다.
	 *
	 * @param obj	변환할 객체.
	 * @param zone	시각 값의 날짜를 구할 시간대.
	 * @return	변환된 날짜. {@code obj}가 {@code null}이거나 빈 문자열이면 {@code null}.
	 * @throws IllegalArgumentException	{@code zone}이 {@code null}이거나, 받지 않는 타입이거나, 문자열 형식이 맞지 않는
	 * 									경우.
	 */
	public static LocalDate from(Object obj, ZoneId zone) {
		Preconditions.checkNotNullArgument(zone, "zone is null");

		if ( obj instanceof LocalDate ld ) {
			return ld;
		}
		else if ( obj instanceof java.sql.Date sd ) {
			return sd.toLocalDate();
		}
		else if ( obj instanceof java.sql.Time || obj instanceof java.time.LocalTime ) {
			throw new IllegalArgumentException("cannot convert a time-of-day to LocalDate: " + obj);
		}
		else if ( obj instanceof String str && DATE_ONLY.matcher(str.strip()).matches() ) {
			try {
				return LocalDate.parse(str.strip());
			}
			catch ( java.time.format.DateTimeParseException e ) {
				throw new IllegalArgumentException("Invalid date string: " + str, e);
			}
		}
		else {
			java.time.LocalDateTime ldt = LocalDateTimes.from(obj, zone);
			return (ldt != null) ? ldt.toLocalDate() : null;
		}
	}
}
