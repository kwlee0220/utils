package utils.script;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import utils.LocalDateTimes;
import utils.script.MVELFunction;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DateTimeFunctions {
	@MVELFunction(name="DateTimeNow")
	public static LocalDateTime now() {
		return LocalDateTime.now();
	}

	@MVELFunction(name="DateTimeFromMillis")
	public static LocalDateTime DateTimeFromMillis(long millis) {
		return LocalDateTimes.fromUtcMillis(millis);
	}
	
	@MVELFunction(name="DateTimeToMillis")
	public static long DateTimeToMillis(Object obj) {
		return LocalDateTimes.toUtcMillis(asLocalDateTime(obj));
	}
	
	@MVELFunction(name="DateTimeFromString")
	public static LocalDateTime DateTimeFromString(String str) {
		return LocalDateTime.parse(str);
	}
	
	@MVELFunction(name="DateTimeToString")
	public static String DateTimeToString(Object obj) {
		return asLocalDateTime(obj).toString();
	}

	@MVELFunction(name="DateTimeGetYear")
	public static int DateTimeGetYear(Object obj) {
		return asLocalDateTime(obj).getYear();
	}

	@MVELFunction(name="DateTimeGetMonth")
	public static int DateTimeGetMonth(Object obj) {
		return asLocalDateTime(obj).getMonthValue();
	}

	@MVELFunction(name="DateTimeGetDayOfMonth")
	public static int DateTimeGetDayOfMonth(Object obj) {
		return asLocalDateTime(obj).getDayOfMonth();
	}

	@MVELFunction(name="DateTimeWeekDay")
	public static int DateTimeWeekDay(Object obj) {
		return asLocalDateTime(obj).getDayOfWeek().getValue();
	}

	@MVELFunction(name="DateTimeGetHour")
	public static int DateTimeGetHour(Object obj) {
		return asLocalDateTime(obj).getHour();
	}

	@MVELFunction(name="DateTimeGetMinute")
	public static int DateTimeGetMinute(Object obj) {
		return asLocalDateTime(obj).getMinute();
	}

	@MVELFunction(name="DateTimeGetSecond")
	public static int DateTimeGetSecond(Object obj) {
		return asLocalDateTime(obj).getSecond();
	}

	@MVELFunction(name="DateTimeParse")
	public static LocalDateTime DateTimeParse(String dtStr, String pattern) {
		try {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
			return LocalDateTime.parse(dtStr, formatter);
		}
		catch ( Exception e ) {
			return null;
		}
	}

	@MVELFunction(name="DateTimeParseLE")
	public static LocalDateTime DateTimeParseLE(String dtStr, DateTimeFormatter formatter) {
		try {
			return LocalDateTime.parse(dtStr, formatter);
		}
		catch ( Exception e ) {
			return null;
		}
	}

	@MVELFunction(name="DateTimePattern")
	public static DateTimeFormatter DateTimePattern(String patternStr) {
		return DateTimeFormatter.ofPattern(patternStr);
	}

	@MVELFunction(name="DateTimeFormat")
	public static String DateTimeFormat(Object obj, String pattern) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
		return asLocalDateTime(obj).format(formatter);
	}

	@MVELFunction(name="DateTimeFormatLE")
	public static String DateTimeFormatLE(Object obj, DateTimeFormatter formatter) {
		return asLocalDateTime(obj).format(formatter);
	}

	@MVELFunction(name="DateTimeIsEqual")
	public static boolean DateTimeIsEqual(LocalDateTime left, LocalDateTime right) {
		return left.isEqual(right);
	}

	@MVELFunction(name="DateTimeIsAfter")
	public static boolean DateTimeIsAfter(Object left, Object right) {
		return asLocalDateTime(left).isAfter(asLocalDateTime(right));
	}

	@MVELFunction(name="DateTimeIsBefore")
	public static boolean DateTimeIsBefore(LocalDateTime left, LocalDateTime right) {
		return left.isBefore(right);
	}

	@MVELFunction(name="DateTimeIsBetween")
	public static boolean DateTimeIsBetween(Object obj, Object begin, Object end) {
		long ldtMillis = DateTimeToMillis(obj);
		long beginMillis = DateTimeToMillis(begin);
		long endMillis = DateTimeToMillis(end);
		
		return ldtMillis >= beginMillis && ldtMillis <= endMillis;
	}
	
	public static LocalDateTime asLocalDateTime(Object obj) {
		if ( obj == null ) {
			return null;
		}
		if ( obj instanceof LocalDateTime ) {
			return (LocalDateTime)obj;
		}
		else if ( obj instanceof Date ) {
			return ((Date)obj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
		}
		else {
			throw new IllegalArgumentException("Not DateTime object: obj=" + obj);
		}
	}
}
