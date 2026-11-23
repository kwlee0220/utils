package utils;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
 * {@link java.time.LocalTime}과 하루 중 밀리초·문자열 사이의 변환을 제공한다. 시간대와는 무관하다.
 * <p>
 * 객체 인자가 {@code null}이면 {@link IllegalArgumentException}을 던진다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalTimes {
	private static final long MILLIS_PER_DAY = 86_400_000L;
	
	private LocalTimes() {
		throw new AssertionError("should not be called: " + getClass());
	}
	
	/**
	 * ISO-8601 시각 문자열을 {@link LocalTime}으로 변환한다. {@link LocalTime#parse(CharSequence)}와 같다.
	 * 형식은 {@code HH:mm[:ss[.fraction]]}이다(예: {@code 09:00}, {@code 09:00:30.5}).
	 *
	 * @param str	변환할 문자열.
	 * @return	변환된 시각.
	 * @throws IllegalArgumentException	{@code str}이 {@code null}인 경우.
	 * @throws java.time.format.DateTimeParseException	형식이 맞지 않는 경우.
	 */
	public static LocalTime parse(String str) {
		Preconditions.checkNotNullArgument(str, "string is null");
		
		return LocalTime.parse(str);
	}
	
	/**
	 * 주어진 시각을 자정부터의 밀리초로 변환한다. 밀리초 미만은 버린다.
	 *
	 * @param lt	변환할 시각.
	 * @return	자정부터의 밀리초({@code 0} ~ {@code 86_399_999}).
	 * @throws IllegalArgumentException	{@code lt}가 {@code null}인 경우.
	 */
	public static long toMillisOfDay(LocalTime lt) {
		Preconditions.checkNotNullArgument(lt, "LocalTime is null");
		
		return TimeUnit.NANOSECONDS.toMillis(lt.toNanoOfDay());
	}
	
	/**
	 * 자정부터의 밀리초를 {@link LocalTime}으로 변환한다.
	 *
	 * @param millis	자정부터의 밀리초. {@code 0} 이상 {@code 86_400_000} 미만이어야 한다.
	 * @return	변환된 시각.
	 * @throws IllegalArgumentException	범위를 벗어난 경우.
	 */
	public static LocalTime fromMillisOfDay(long millis) {
		Preconditions.checkArgument(millis >= 0 && millis < MILLIS_PER_DAY,
									"millis-of-day out of range [0, %s): %s", MILLIS_PER_DAY, millis);
		
		return LocalTime.ofNanoOfDay(TimeUnit.MILLISECONDS.toNanos(millis));
	}
	
	/**
	 * 주어진 시각을 ISO-8601 문자열로 변환한다. {@link LocalTime#toString()}과 같아서 정밀도에 따라 형식이 달라진다
	 * (예: {@code 09:00}, {@code 09:00:30}, {@code 09:00:30.500}).
	 *
	 * @param time	변환할 시각.
	 * @return	변환된 문자열.
	 * @throws IllegalArgumentException	{@code time}이 {@code null}인 경우.
	 */
	public static String format(LocalTime time) {
		Preconditions.checkNotNullArgument(time, "LocalTime is null");
		
		return time.toString();
	}

	/**
	 * 하루 중의 시각을 나타내는 객체를 {@link LocalTime}으로 변환한다.
	 * <p>
	 * {@link LocalTime}은 그대로, {@link java.sql.Time}은 {@link java.sql.Time#toLocalTime()}으로 바꾼다. 문자열은
	 * 시각만 있는 {@code HH:mm[:ss[.fraction]]} 표기를 받는다. 그 밖의 값은 {@link LocalDateTimes#from(Object, ZoneId)}가
	 * 받는 값을 같은 규칙으로 받아 지역 시각의 시각 부분으로 바꾼다(예: {@link java.sql.Timestamp},
	 * {@link java.time.LocalDateTime}, {@link java.time.Instant}). 시각이 없는 {@link java.sql.Date}와
	 * {@link java.time.LocalDate}는 받지 않는다.
	 * <p>
	 * 이 클래스의 다른 메소드와 달리 {@code obj}가 {@code null}이거나 빈 문자열이면 {@code null}을 돌려준다.
	 *
	 * @param obj	변환할 객체.
	 * @param zone	시각 값을 지역 시각으로 바꿀 시간대.
	 * @return	변환된 시각. {@code obj}가 {@code null}이거나 빈 문자열이면 {@code null}.
	 * @throws IllegalArgumentException	{@code zone}이 {@code null}이거나, 받지 않는 타입이거나, 문자열 형식이 맞지 않는
	 * 									경우.
	 */
	public static LocalTime from(Object obj, java.time.ZoneId zone) {
		Preconditions.checkNotNullArgument(zone, "zone is null");

		if ( obj == null ) {
			return null;
		}
		else if ( obj instanceof LocalTime lt ) {
			return lt;
		}
		else if ( obj instanceof java.sql.Time time ) {
			return time.toLocalTime();
		}
		else if ( obj instanceof String str ) {
			if ( str.isBlank() ) {
				return null;
			}
			try {
				return LocalTime.parse(str.strip());
			}
			catch ( java.time.format.DateTimeParseException e ) {
				throw new IllegalArgumentException("Invalid time string: " + str, e);
			}
		}
		else if ( obj instanceof java.sql.Date || obj instanceof java.time.LocalDate ) {
			throw new IllegalArgumentException("cannot convert a date without time-of-day to LocalTime: " + obj);
		}
		else {
			return LocalDateTimes.from(obj, zone).toLocalTime();
		}
	}
}