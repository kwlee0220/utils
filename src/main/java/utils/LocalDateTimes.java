package utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
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
	 * @param instant 변환할 시간.
	 * @param zoneId  변환할 시간대.
	 * @return 변환된 {@link LocalDateTime} 객체.
	 */
	public static LocalDateTime fromInstant(Instant instant, ZoneId zoneId) {
        return LocalDateTime.ofInstant(instant, zoneId);
	}
	
	/**
	 * 주어진 시각을 현재 시스템의 시간대로 변환된 {@link LocalDateTime} 객체를 생성한다.
	 * 
	 * @param instant 변환할 시간.
	 * @return 변환된 {@link LocalDateTime} 객체.
	 */
	public static LocalDateTime fromInstant(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
	}
	
	/**
	 * 주어진 {@code LocalDateTime} 객체를 시스템 기본 시간대로 변환된 {@link Instant} 객체를 생성한다.
	 * 
	 * @param ldt 변환할 시간.
	 * @return 변환된 {@link Instant} 객체.
	 */
	public static Instant toInstant(LocalDateTime ldt) {
		return ldt.atZone(ZoneOffset.systemDefault()).toInstant();
	}
	
	/**
	 * 주어진 {@code LocalDateTime} 객체를 UTC 시간대로 변환된 {@link Instant} 객체를 생성한다.
	 * 
	 * @param ldt 변환할 시간.
	 * @return 변환된 {@link Instant} 객체.
	 */
	public static Instant toInstantWithoutTimeZone(LocalDateTime ldt) {
		return ldt.toInstant(ZoneOffset.UTC);
	}
	
	/**
	 * 주어진 {@code LocalDateTime} 객체를 epoch milliseconds로 변환한다.
	 * 
	 * @param ldt 변환할 시간.
	 * @return epoch milliseconds 값.
	 */
	public static long toEpochMillis(LocalDateTime ldt) {
		return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}
	
	/**
	 * 주어진 epoch milliseconds를 시스템 기본 시간 대의 {@code LocalDateTime} 객체로 변환한다.
	 * 
	 * @param millis 변환할 epoch milliseconds 값.
	 * @return 변환된 {@code LocalDateTime} 객체.
	 */
	public static LocalDateTime fromEpochMillis(long millis) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
	}
	
	/**
	 * 주어진 epoch milliseconds를 UTC 시간대의 {@code LocalDateTime} 객체로 변환한다.
	 * 
	 * @param millis 변환할 epoch milliseconds 값.
	 * @return 변환된 {@code LocalDateTime} 객체.
	 */
	public static LocalDateTime fromUtcMillis(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDateTime();
	}
	
	/**
	 * 주어진 {@code LocalDateTime} 객체를 UTC 시간대의 epoch milliseconds로 변환한다.
	 * 
	 * @param ldt 변환할 시간.
	 * @return epoch milliseconds 값.
	 */
	public static long toUtcMillis(LocalDateTime ldt) {
		return ldt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
	}
	
	/**
	 * 주어진 시간 문자열을 파싱하여 {@code LocalDateTime} 객체로 변환한다.
	 * <p>
	 * 시간 문자열은 {@code yyyy-MM-ddTHH:mm:ss} 형식을 따라야 한다.
	 * 
	 * @param str 변환할 문자열.
	 * @return 변환된 {@code LocalDateTime} 객체.
	 */
	public static LocalDateTime fromString(String str) {
		return LocalDateTime.parse(str);
	}
	
	/**
     * 주어진 {@code LocalDateTime} 객체를 문자열로 변환한다.
     * <p>
     * 반환된 시각 문자열은 {@code yyyy-MM-ddTHH:mm:ss} 형식을 따른다.
     * 
     * @param ldt 변환할 시간.
     * @return 변환된 문자열.
     */
	public static String toString(LocalDateTime ldt) {
		return ldt.toString();
	}
}