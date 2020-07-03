package utils;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalTimes {
	private LocalTimes() {
		throw new AssertionError("should not be called: " + getClass());
	}
	
	public static LocalTime fromString(String str) {
		return LocalTime.parse(str);
	}
	
	public static long toMillisOfDay(LocalTime lt) {
		return TimeUnit.NANOSECONDS.toMillis(lt.toNanoOfDay());
	}
	
	public static LocalTime fromMillisOfDay(long millis) {
		return LocalTime.ofNanoOfDay(TimeUnit.MILLISECONDS.toNanos(millis));
	}
	
	public static String toString(LocalTime time) {
		return time.toString();
	}
}