package utils;

import java.awt.Dimension;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;


/**
 * 다양한 단위(화면 크기, 길이, 바이트 크기, duration, 날짜)의 문자열 파싱 및 포맷팅 유틸리티.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class UnitUtils {
	private UnitUtils() {
		throw new AssertionError("Should not be invoked!!: class=" + UnitUtils.class.getName());
	}

	/**
	 * "WIDTHxHEIGHT" 형식의 문자열을 {@link Dimension}으로 파싱한다.
	 * <p>
	 * 구분자는 {@code 'x'} 또는 {@code 'X'} 모두 허용한다 (예: "1024x768", "640X480").
	 *
	 * @param str	파싱할 문자열.
	 * @return	파싱된 {@link Dimension}.
	 * @throws IllegalArgumentException	{@code str}이 {@code null}이거나 형식이 올바르지 않은 경우.
	 */
	public static Dimension parseDimension(String str) {
		Preconditions.checkNotNullArgument(str, "dimension string is null");

		int index = str.toUpperCase().indexOf('X');
		if ( index < 0 ) {
			throw new IllegalArgumentException("invalid dimension format: " + str);
		}
		try {
			int width = Integer.parseInt(str.substring(0, index).trim());
			int height = Integer.parseInt(str.substring(index+1).trim());
			return new Dimension(width, height);
		}
		catch ( NumberFormatException e ) {
			throw new IllegalArgumentException("invalid dimension format: " + str, e);
		}
	}

	/**
	 * 단위 접미사가 붙은 길이 문자열을 미터 단위 {@code double}로 변환한다.
	 * <p>
	 * 지원 접미사: {@code mm}, {@code cm}, {@code m}, {@code km}. 대소문자 구분 없음.
	 * 접미사가 없으면 입력 값을 미터로 해석한다.
	 *
	 * @param lengthStr	길이 문자열 (예: "1.5km", "100mm", "5").
	 * @return	미터 단위 {@code double} 값.
	 * @throws NumberFormatException	숫자 부분이 파싱되지 않는 경우.
	 */
	public static double parseLengthInMeter(String lengthStr) {
		String s = lengthStr.toLowerCase();
		if ( s.endsWith("mm") ) {
			return Double.parseDouble(s.substring(0, s.length()-2)) / 1000.0;
		}
		if ( s.endsWith("cm") ) {
			return Double.parseDouble(s.substring(0, s.length()-2)) / 100.0;
		}
		if ( s.endsWith("km") ) {
			return Double.parseDouble(s.substring(0, s.length()-2)) * 1000.0;
		}
		if ( s.endsWith("m") ) {
			return Double.parseDouble(s.substring(0, s.length()-1));
		}
		return Double.parseDouble(s);
	}

	/**
	 * 미터 단위 길이를 사람이 읽기 좋은 문자열로 변환한다.
	 * <p>
	 * 1m 미만은 소수 한 자리, 1km 이상은 km 단위로 표기한다.
	 *
	 * @param length	길이 (미터).
	 * @return	변환된 문자열 (예: "0.5m", "100m", "5km").
	 */
	public static String toMeterString(double length) {
		if ( length < 1 ) {
			return String.format("%.1fm", length);
		}
		if ( length < 1000 ) {
			return String.format("%.0fm", length);
		}
		return String.format("%.0fkm", length / 1000);
	}

	/**
	 * 단위 접미사가 붙은 바이트 크기 문자열을 {@code long}으로 파싱한다.
	 * <p>
	 * 지원 접미사: {@code kb}/{@code k}, {@code mb}/{@code m}, {@code gb}/{@code g}.
	 * 대소문자 구분 없음. 접미사가 없으면 byte 로 해석한다.
	 * 긴 접미사({@code mb}, {@code kb}, {@code gb})를 짧은 것({@code m}, {@code k}, {@code g})보다
	 * 우선 매칭한다.
	 *
	 * @param szStr	크기 문자열 (예: "100mb", "5GB", "1024").
	 * @return	byte 단위 {@code long} 값.
	 * @throws NumberFormatException	숫자 부분이 파싱되지 않는 경우.
	 */
	public static long parseByteSize(String szStr) {
		String s = szStr.toLowerCase();

		// 긴 접미사부터 매칭한다.
		if ( s.endsWith("kb") ) {
			return Math.round(Double.parseDouble(s.substring(0, s.length()-2)) * 1024);
		}
		if ( s.endsWith("mb") ) {
			return Math.round(Double.parseDouble(s.substring(0, s.length()-2)) * 1024 * 1024);
		}
		if ( s.endsWith("gb") ) {
			return Math.round(Double.parseDouble(s.substring(0, s.length()-2)) * 1024 * 1024 * 1024);
		}
		if ( s.endsWith("k") ) {
			return Math.round(Double.parseDouble(s.substring(0, s.length()-1)) * 1024);
		}
		if ( s.endsWith("m") ) {
			return Math.round(Double.parseDouble(s.substring(0, s.length()-1)) * 1024 * 1024);
		}
		if ( s.endsWith("g") ) {
			return Math.round(Double.parseDouble(s.substring(0, s.length()-1)) * 1024 * 1024 * 1024);
		}
		return Long.parseLong(s);
	}

	/**
	 * 바이트 크기를 지정된 단위와 포맷으로 변환한다.
	 *
	 * @param sz		byte 단위 크기.
	 * @param unit		변환 단위 ({@code b}, {@code k}/{@code kb}, {@code m}/{@code mb}, {@code g}/{@code gb}).
	 * 					대소문자 구분 없음.
	 * @param format	숫자 부분의 포맷 (예: {@code "%.1f"}). 단위는 자동으로 뒤에 붙는다.
	 * @return	변환된 문자열 (예: "1.5mb").
	 * @throws IllegalArgumentException	지원하지 않는 단위인 경우.
	 */
	public static String toByteSizeString(long sz, String unit, String format) {
		String lunit = unit.toLowerCase();
		switch ( lunit ) {
			case "m":
			case "mb":
				return String.format(format + unit, sz / 1024.0 / 1024.0);
			case "k":
			case "kb":
				return String.format(format + unit, sz / 1024.0);
			case "g":
			case "gb":
				return String.format(format + unit, sz / 1024.0 / 1024.0 / 1024.0);
			case "b":
				return String.format(format + unit, sz);
			default:
				throw new IllegalArgumentException("unknown byte unit: " + unit);
		}
	}

	/**
	 * 바이트 크기를 사람이 읽기 좋은 단위 문자열로 변환한다.
	 * <p>
	 * 1024 배수로 자동 환산하며, 정수 부분이 떨어지면 정수, 소수가 있으면 한 자리 소수로 표기한다.
	 *
	 * @param sz	byte 단위 크기.
	 * @return	변환된 문자열 (예: "512", "1.5kb", "10mb").
	 */
	public static String toByteSizeString(long sz) {
		if ( sz < 1024 ) {
			return "" + sz;
		}
		double fsz = sz / 1024.0;
		if ( fsz < 1024 ) {
			return (fsz - Math.floor(fsz) > 0) ? String.format("%.1fkb", fsz)
												: String.format("%.0fkb", fsz);
		}
		fsz = fsz / 1024.0;
		if ( fsz < 1024 ) {
			return (fsz - Math.floor(fsz) > 0) ? String.format("%.1fmb", fsz)
												: String.format("%.0fmb", fsz);
		}
		fsz = fsz / 1024.0;
		return (fsz - Math.floor(fsz) > 0) ? String.format("%.1fgb", fsz)
											: String.format("%.0fgb", fsz);
	}

	/**
	 * Duration 문자열을 {@link Duration} 객체로 파싱한다.
	 * <p>
	 * 다음 순서로 해석한다:
	 * <ul>
	 *   <li>{@code "PT"}로 시작하면 ISO-8601로 보고 {@link Duration#parse(CharSequence)}에 위임한다.</li>
	 *   <li>접미사({@code ms}, {@code s}, {@code m}, {@code h}, {@code d})가 있으면 해당 단위로 해석한다.</li>
	 *   <li>접미사가 없으면 {@code defaultUnit} 단위의 정수로 해석한다. {@code defaultUnit}이
	 *       {@code null}이면 {@link IllegalArgumentException}을 던진다.</li>
	 * </ul>
	 *
	 * @param durStr		duration 문자열. {@code null}이면 {@code null} 반환.
	 * @param defaultUnit	접미사가 없을 때 적용할 기본 단위({@code ms}/{@code s}/{@code m}/{@code h}/{@code d}).
	 * 						{@code null}이면 접미사 없는 입력을 허용하지 않는다.
	 * @return	파싱된 {@link Duration} 또는 {@code null}.
	 * @throws IllegalArgumentException	접미사가 없고 {@code defaultUnit}이 {@code null}이거나 유효하지 않은 경우.
	 * @throws NumberFormatException	숫자 부분이 파싱되지 않는 경우.
	 */
	public static Duration parseDuration(String durStr, String defaultUnit) {
		if ( durStr == null ) {
			return null;
		}
		if ( durStr.startsWith("PT") ) {
			return Duration.parse(durStr);
		}
		try {
			return Duration.ofMillis(parseSuffixedDurationMillis(durStr));
		}
		catch ( IllegalArgumentException expected ) {
			if ( defaultUnit == null ) {
				throw expected;
			}
			try {
				long amount = Long.parseLong(durStr);
				return switch ( defaultUnit ) {
					case "ms" -> Duration.ofMillis(amount);
					case "s" -> Duration.ofSeconds(amount);
					case "m" -> Duration.ofMinutes(amount);
					case "h" -> Duration.ofHours(amount);
					case "d" -> Duration.ofDays(amount);
					default -> throw new IllegalArgumentException("invalid default duration unit: " + defaultUnit);
				};
			}
			catch ( NumberFormatException e ) {
				throw expected;
			}
		}
	}
	
	/**
	 * {@code parseDuration(durStr, null)}과 동일하다. 접미사 없는 입력은 허용하지 않는다
	 * (ISO-8601 {@code "PT"} 형식 제외).
	 *
	 * @param durStr	duration 문자열. {@code null}이면 {@code null} 반환.
	 * @return	파싱된 {@link Duration} 또는 {@code null}.
	 * @throws IllegalArgumentException	접미사가 없는 경우.
	 * @throws NumberFormatException	숫자 부분이 파싱되지 않는 경우.
	 */
	public static Duration parseDuration(String durStr) {
		return parseDuration(durStr, null);
	}

	/**
	 * Duration 문자열을 {@link Duration} 객체로 파싱한다.
	 * <p>
	 * {@link #parseDuration(String)}과 달리, 접미사가 없는 입력을 <b>second</b> 단위로 해석한다
	 * (소수 허용 — 예: "1.5" → 1500ms). {@code "PT"} 시작 ISO-8601과
	 * 접미사({@code ms}/{@code s}/{@code m}/{@code h}/{@code d})도 지원한다.
	 *
	 * @param durStr	duration 문자열. {@code null}이면 {@code null} 반환.
	 * @return	파싱된 {@link Duration} 또는 {@code null}.
	 * @throws IllegalArgumentException	문자열을 duration으로 해석할 수 없는 경우
	 * 									({@link NumberFormatException} 포함).
	 */
	public static Duration parseSecondDuration(String durStr) {
		if ( durStr == null ) {
			return null;
		}

		try {
			// PT/접미사(d=일 포함)를 먼저 처리한다. (Double.parseDouble은 끝의 'd'/'f'를 타입 접미사로
			//  오인하므로 "5d"가 5초로 잘못 파싱되는 것을 막기 위해 순서가 중요하다.)
			return parseDuration(durStr);
		}
		catch ( IllegalArgumentException expected ) {
			// 접미사 없는 소수 초 (예: "1.5" → 1500ms)
			return Duration.ofMillis(Math.round(Double.parseDouble(durStr) * 1000));
		}
	}

	/**
	 * 접미사 단위가 붙은 duration 문자열을 millisecond 로 변환한다.
	 * 인식 가능한 접미사({@code ms}/{@code s}/{@code m}/{@code h}/{@code d})가 없으면
	 * {@link IllegalArgumentException}을 던진다(절대 {@code null}을 반환하지 않는다).
	 */
	private static long parseSuffixedDurationMillis(String durStr) {
		if ( durStr.endsWith("ms") ) {
			return Long.parseLong(durStr.substring(0, durStr.length()-2));
		}
		if ( durStr.endsWith("s") ) {
			return TimeUnit.SECONDS.toMillis(Long.parseLong(durStr.substring(0, durStr.length()-1)));
		}
		if ( durStr.endsWith("m") ) {
			return TimeUnit.MINUTES.toMillis(Long.parseLong(durStr.substring(0, durStr.length()-1)));
		}
		if ( durStr.endsWith("h") ) {
			return TimeUnit.HOURS.toMillis(Long.parseLong(durStr.substring(0, durStr.length()-1)));
		}
		if ( durStr.endsWith("d") ) {
			return TimeUnit.DAYS.toMillis(Long.parseLong(durStr.substring(0, durStr.length()-1)));
		}
		
		throw new IllegalArgumentException("invalid duration expr: " + durStr);
	}

	/**
	 * Millisecond 값을 {@code [hh:][mm:]ss[.fff]} 형식의 문자열로 변환한다.
	 * <p>
	 * 음수 입력은 앞에 {@code '-'} 를 붙여 절댓값 형식과 동일하게 표현한다.
	 *
	 * @param millis	millisecond 값.
	 * @return	변환된 문자열 (예: "0.500", "30", "01:30", "1:00:00.250").
	 */
	public static String toMillisString(long millis) {
		if ( millis < 0 ) {
			return "-" + toMillisString(-millis);
		}

		long seconds = millis / 1000;
		if ( seconds == 0 ) {
			return String.format("0.%03d", millis);
		}
		millis = millis % 1000;
		String millisStr = (millis > 0) ? String.format(".%03d", millis) : "";

		long minutes = seconds / 60;
		if ( minutes == 0 ) {
			return String.format("%02d%s", seconds, millisStr);
		}
		seconds = seconds % 60;

		long hours = minutes / 60;
		if ( hours == 0 ) {
			return String.format("%02d:%02d%s", minutes, seconds, millisStr);
		}
		minutes = minutes % 60;

		return String.format("%d:%02d:%02d%s", hours, minutes, seconds, millisStr);
	}

	/**
	 * Millisecond 값을 {@code [hh:][mm:]ss} 단위의 사람이 읽기 좋은 문자열로 변환한다.
	 * Millisecond 부분은 반올림된다.
	 *
	 * @param millis	millisecond 값. 음수 입력은 정의되지 않음.
	 * @return	변환된 문자열 (예: "0s", "30s", "01m:30s", "1h:00m:00s").
	 */
	public static String toSecondString(long millis) {
		long seconds = Math.round(millis / 1000d);
		if ( seconds == 0 ) {
			return "0s";
		}

		long minutes = seconds / 60;
		if ( minutes == 0 ) {
			return String.format("%02ds", seconds);
		}
		seconds = seconds % 60;

		long hours = minutes / 60;
		if ( hours == 0 ) {
			return String.format("%02dm:%02ds", minutes, seconds);
		}
		minutes = minutes % 60;

		return String.format("%dh:%02dm:%02ds", hours, minutes, seconds);
	}

	/**
	 * Epoch millisecond 값을 {@code yyyyMMdd'T'HHmmss[:fff]} 형식의 문자열로 변환한다.
	 *
	 * @param date				epoch millisecond.
	 * @param toMilliSeconds	{@code true}면 millisecond 부분 포함.
	 * @return	변환된 문자열.
	 */
	public static String toDateString(long date, boolean toMilliSeconds) {
		GregorianCalendar c = new GregorianCalendar();
		c.setTime(new Date(date));

		return UnitUtils.toDateString(c, toMilliSeconds);
	}

	/**
	 * {@link Calendar}을 {@code yyyyMMdd'T'HHmmss[:fff]} 형식의 문자열로 변환한다.
	 *
	 * @param c					{@link Calendar} 객체. {@code null} 이면 {@code null} 반환.
	 * @param toMilliSeconds	{@code true}면 millisecond 부분({@code :fff}) 포함.
	 * @return	변환된 문자열 또는 {@code null}.
	 */
	public static String toDateString(Calendar c, boolean toMilliSeconds) {
		if ( c == null ) {
			return null;
		}

		StringBuilder builder = new StringBuilder();

		builder.append(String.format("%04d", c.get(Calendar.YEAR)));
		builder.append(String.format("%02d", c.get(Calendar.MONTH) + 1));
		builder.append(String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));

		builder.append('T');
		builder.append(String.format("%02d%02d%02d",
										c.get(Calendar.HOUR_OF_DAY),
										c.get(Calendar.MINUTE),
										c.get(Calendar.SECOND)));

		if ( toMilliSeconds ) {
			builder.append(String.format(":%03d", c.get(Calendar.MILLISECOND)));
		}

		return builder.toString();
	}

	/**
	 * {@code yyyyMMdd[THHmmss[:fff]]} 형식의 문자열을 {@link Calendar}로 파싱한다.
	 * <p>
	 * 시각 부분이 생략된 경우 {@code 00:00:00}으로 설정한다. 시각이 포함된 경우 인덱스 8의 구분 문자
	 * (예: {@code 'T'})는 무시되고 9~14의 6자리({@code HHmmss})가 사용된다. 길이가 19 이상이고
	 * 인덱스 15가 {@code ':'}이면 16~18의 3자리를 밀리초({@code fff})로 해석한다
	 * ({@link #toDateString(Calendar, boolean)}의 {@code toMilliSeconds=true} 출력과 round-trip된다).
	 *
	 * @param date	날짜 문자열. {@code null} 이면 {@code null} 반환.
	 * @return	파싱된 {@link Calendar} 또는 {@code null}.
	 * @throws IllegalArgumentException	형식이나 길이가 올바르지 않은 경우.
	 */
	public static Calendar parseDateString(String date) {
		if ( date == null ) {
			return null;
		}
		if ( date.length() < 8 || (date.length() > 8 && date.length() < 15) ) {
			throw new IllegalArgumentException("invalid date format=" + date);
		}

		try {
			int year = Integer.parseInt(date.substring(0, 4));
			int month = Integer.parseInt(date.substring(4, 6)) - 1;
			int day = Integer.parseInt(date.substring(6, 8));

			if ( date.length() > 8 ) {
				int hour = Integer.parseInt(date.substring(9, 11));
				int min = Integer.parseInt(date.substring(11, 13));
				int sec = Integer.parseInt(date.substring(13, 15));

				GregorianCalendar cal = new GregorianCalendar(year, month, day, hour, min, sec);
				// ':fff' 밀리초 부분(toDateString의 toMilliSeconds=true 출력)이 있으면 반영한다.
				if ( date.length() >= 19 && date.charAt(15) == ':' ) {
					cal.set(Calendar.MILLISECOND, Integer.parseInt(date.substring(16, 19)));
				}
				return cal;
			}
			else {
				return new GregorianCalendar(year, month, day, 0, 0, 0);
			}
		}
		catch ( NumberFormatException e ) {
			throw new IllegalArgumentException("invalid date format=" + date, e);
		}
	}
}
