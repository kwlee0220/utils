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
	
	public static LocalDateTime fromInstant(Instant instant, ZoneId zoneId) {
        return LocalDateTime.ofInstant(instant, zoneId);
	}
	
	public static LocalDateTime fromInstant(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
	}
	
	public static Instant toInstant(LocalDateTime ldt) {
		return ldt.atZone(ZoneOffset.systemDefault()).toInstant();
	}
	
	public static Instant toInstantWithoutTimeZone(LocalDateTime ldt) {
		return ldt.toInstant(ZoneOffset.UTC);
	}
	
	public static long toEpochMillis(LocalDateTime ldt) {
		return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}
	
	public static LocalDateTime fromEpochMillis(long millis) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
	}
	
	public static LocalDateTime fromUtcMillis(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDateTime();
	}
	
	public static long toUtcMillis(LocalDateTime ldt) {
		return ldt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
	}
	
	public static LocalDateTime fromString(String str) {
		return LocalDateTime.parse(str);
	}
	
	public static String toString(LocalDateTime ldt) {
		return ldt.toString();
	}
}