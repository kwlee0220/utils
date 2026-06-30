package utils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import utils.func.FOption;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Instants {
	private static final DateTimeFormatter DEFAULT_FORMATTER
										= DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
															.withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter UTC_FORMATTER
										= DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
															.withZone(ZoneId.of("UTC"));
	
	private Instants() {
		throw new AssertionError("should not be called: " + getClass());
	}

	public static Instant fromTimestamp(Timestamp ts) {
		return FOption.map(ts, Timestamp::toInstant);
	}
	public static String toZonedString(Instant instant) {
		return FOption.map(instant, i -> DEFAULT_FORMATTER.format(i));
	}
	public static String toUTCString(Instant instant) {
		return FOption.map(instant, i -> UTC_FORMATTER.format(i));
	}
	
	public static Instant fromString(String str) {
		if ( str.indexOf('Z') >= 0 ) {
			return Instant.parse(str);
		}
	    else if (str.indexOf('T') >= 0) {
			LocalDateTime ldt = LocalDateTime.parse(str);
			return LocalDateTimes.toInstant(ldt);
		}
	    else {
	        // Handle the format "2024-07-10 14:40:37"
	        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	        LocalDateTime ldt = LocalDateTime.parse(str, formatter);
	        return LocalDateTimes.toInstant(ldt);
	    }
	}
}
