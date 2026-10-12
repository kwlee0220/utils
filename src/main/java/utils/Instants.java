package utils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

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
	/** 문자열 끝에 타임존({@code Z} 또는 {@code ±hh:mm}/{@code ±hhmm} 오프셋)이 명시되었는지 판별하는 패턴. */
	private static final Pattern TZ_PATTERN = Pattern.compile(".*([Zz]|[+-]\\d{2}:?\\d{2})$");

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
	
	/**
	 * 시각 문자열을 {@link Instant}로 변환한다.
	 * <p>
	 * 다음 세 가지 표기를 지원한다.
	 * <ol>
	 *   <li>타임존이 명시된 ISO-8601 일시: {@code Z} 또는 {@code ±hh:mm}/{@code ±hhmm} 오프셋을 갖는
	 *       표기. 초와 소수점 이하는 생략 가능하다.
	 *       (예: {@code 2024-07-10T14:40:37Z}, {@code 2024-07-10T14:40+09:00})</li>
	 *   <li>타임존이 없는 ISO-8601 일시: 시스템 기본 시간대의 시각으로 해석한다.
	 *       (예: {@code 2024-07-10T14:40:37})</li>
	 *   <li>공백으로 구분된 일시: 시스템 기본 시간대의 시각으로 해석한다.
	 *       (예: {@code 2024-07-10 14:40:37})</li>
	 * </ol>
	 *
	 * @param str	변환할 시각 문자열.
	 * @return	변환된 {@link Instant}.
	 * @throws java.time.format.DateTimeParseException	지원하지 않는 표기인 경우.
	 */
	public static Instant fromString(String str) {
		if ( TZ_PATTERN.matcher(str).matches() ) {
			// 'Z' 또는 '±hh:mm' 오프셋이 명시된 경우.
			return OffsetDateTime.parse(str).toInstant();
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
