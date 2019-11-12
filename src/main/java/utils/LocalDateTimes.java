package utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalDateTimes {
	private LocalDateTimes() {
		throw new AssertionError("should not be called: " + getClass());
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