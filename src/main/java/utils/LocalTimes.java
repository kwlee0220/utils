package utils;

import java.time.LocalTime;

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
	
	public static String toString(LocalTime time) {
		return time.toString();
	}
}