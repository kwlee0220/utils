package utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalDates {
	private LocalDates() {
		throw new AssertionError("should not be called: " + getClass());
	}
	
	public static LocalDate fromUtcMillis(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
	}
	
	public static long toUtcMillis(LocalDate date) {
		return date.atStartOfDay().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
	}
	
	public static LocalDate fromEpochMillis(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
	}
	
	public static long toEpochMillis(LocalDate ldt) {
		return ldt.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}
}