package utils;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
 * {@link java.time.LocalTime}과 하루 중 밀리초·문자열 사이의 변환을 제공한다. 시간대와는 무관하다.
 * <p>
 * 객체 인자가 {@code null}이면 {@link IllegalArgumentException}을 던진다.
 * <p>
 * 2026-11-01에 {@code fromString} → {@code parse}로 이름을 바꿨다(별칭 없음).
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
	 * @throws IllegalArgumentException	범위를 벗어난 경우(2026-11-01 이전에는 나노초 값을 담은
	 * 									{@link java.time.DateTimeException}이었다).
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
	public static String toString(LocalTime time) {
		Preconditions.checkNotNullArgument(time, "LocalTime is null");
		
		return time.toString();
	}
}