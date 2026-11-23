package utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * duration 문자열({@code 30s}, {@code 1.5m}, {@code PT1H} 등)과 {@link Duration} 사이의 변환을 제공한다.
 * <ul>
 *   <li>파싱: {@link #parse(String)}, {@link #parse(String, ChronoUnit)}. {@code null}은 받지 않는다
 *       ({@link IllegalArgumentException}). 값이 없을 수 있으면 호출하는 쪽에서
 *       {@link utils.func.Nullables#map(Object, java.util.function.Function)} 등으로 거른다.</li>
 *   <li>출력: 시계 형식 {@link #formatClock(Duration)}({@code 1:00:00.250})과 짧은 단위 표기
 *       {@link #formatShort(Duration)}({@code 1h 0m 5s}). 사람이 읽기 위한 형식이므로 {@link #parse(String)}로
 *       다시 읽을 수 없다. 기본 로캘과 상관없이 ASCII 숫자를 쓴다.</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Durations {
	/** ISO-8601 duration 표기의 시작({@code P}, {@code +P}, {@code -P}). 대소문자를 구분하지 않는다. */
	private static final Pattern ISO_DURATION_PREFIX = Pattern.compile("[+-]?P", Pattern.CASE_INSENSITIVE);
	/** 숫자(부호, 정수 또는 소수)와 선택적인 단위 접미사. 대소문자를 구분하지 않는다. */
	private static final Pattern SUFFIXED_DURATION = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)\\s*(ms|s|m|h|d)?",
																	Pattern.CASE_INSENSITIVE);
	private static final BigDecimal NANOS_PER_SECOND = BigDecimal.valueOf(1_000_000_000L);

	private Durations() {
		throw new AssertionError("should not be called: " + Durations.class);
	}

	/**
	 * duration 문자열을 {@link Duration}으로 파싱한다. 접미사가 없는 숫자는 받지 않는다.
	 * <p>
	 * {@code parse(durStr, null)}과 같다. 받는 표기는 {@link #parse(String, ChronoUnit)}을 참고한다.
	 *
	 * @param durStr	duration 문자열.
	 * @return	파싱된 {@link Duration}.
	 * @throws IllegalArgumentException	{@code durStr}이 {@code null}이거나, 지원하지 않는 표기이거나 접미사가 없는 숫자인
	 * 									경우, 또는 값이 {@link Duration}의 범위를 넘는 경우.
	 */
	public static Duration parse(String durStr) {
		return parse(durStr, null);
	}

	/**
	 * duration 문자열을 {@link Duration}으로 파싱한다.
	 * <p>
	 * 앞뒤 공백은 무시하고 다음 두 표기를 받는다. 영문자는 대소문자를 구분하지 않는다.
	 * <ul>
	 *   <li>ISO-8601 duration: {@code P}(앞에 부호가 올 수 있다)로 시작하면 {@link Duration#parse(CharSequence)}로
	 *       해석한다(예: {@code PT2H30M}, {@code P1D}, {@code pt1.5s}).</li>
	 *   <li>숫자와 단위 접미사: 숫자는 부호가 붙을 수 있는 정수 또는 소수이고, 숫자와 접미사 사이에 공백이 올 수 있다.
	 *       접미사는 {@code ms}(밀리초), {@code s}(초), {@code m}(분), {@code h}(시간), {@code d}(일, 24시간)이다
	 *       (예: {@code 500ms}, {@code 1.5s}, {@code 5 m}, {@code 1.5d}는 36시간). 접미사가 없으면
	 *       {@code defaultUnit} 단위로 해석한다(예: {@code defaultUnit}이 {@link ChronoUnit#SECONDS}이면 {@code 1.5}는
	 *       1.5초).</li>
	 * </ul>
	 * 지수 표기({@code 1e3}), {@code NaN}·{@code Infinity}, 그 밖의 접미사는 받지 않는다.
	 * 나노초보다 작은 소수 부분은 반올림한다({@link RoundingMode#HALF_UP}).
	 *
	 * @param durStr		duration 문자열.
	 * @param defaultUnit	접미사가 없는 숫자에 적용할 단위. {@link ChronoUnit#NANOS}부터 {@link ChronoUnit#DAYS}까지의
	 * 						단위를 쓸 수 있다. {@code null}이면 접미사가 없는 숫자를 받지 않는다.
	 * @return	파싱된 {@link Duration}.
	 * @throws IllegalArgumentException	{@code durStr}이 {@code null}인 경우, 지원하지 않는 표기인 경우, 접미사가 없는데 {@code defaultUnit}이 {@code null}인 경우,
	 * 									{@code defaultUnit}이 지원하지 않는 단위인 경우, 값이 {@link Duration}의 범위를
	 * 									넘는 경우. {@code defaultUnit}은 {@code durStr}과 상관없이 항상 검사한다.
	 */
	public static Duration parse(String durStr, ChronoUnit defaultUnit) {
		Preconditions.checkArgument(defaultUnit == null || defaultUnit.compareTo(ChronoUnit.DAYS) <= 0,
									"unsupported default duration unit: %s (NANOS..DAYS expected)", defaultUnit);
		Preconditions.checkNotNullArgument(durStr, "duration string is null");

		String str = durStr.strip();
		if ( ISO_DURATION_PREFIX.matcher(str).lookingAt() ) {
			try {
				return Duration.parse(str);
			}
			catch ( DateTimeParseException e ) {
				throw new IllegalArgumentException(invalidDurationMessage(durStr), e);
			}
		}

		Matcher matcher = SUFFIXED_DURATION.matcher(str);
		if ( !matcher.matches() ) {
			throw new IllegalArgumentException(invalidDurationMessage(durStr));
		}
		ChronoUnit unit = (matcher.group(2) != null) ? toDurationUnit(matcher.group(2)) : defaultUnit;
		if ( unit == null ) {
			throw new IllegalArgumentException("duration unit is missing: '" + durStr
												+ "' (suffix ms, s, m, h or d expected)");
		}

		// 소수 값도 손실 없이 계산하도록 나노초 단위의 BigDecimal로 바꾼 뒤 초와 나노초로 나눈다.
		BigDecimal nanos = new BigDecimal(matcher.group(1))
								.multiply(BigDecimal.valueOf(unit.getDuration().toNanos()))
								.setScale(0, RoundingMode.HALF_UP);
		BigDecimal[] secondsAndNanos = nanos.divideAndRemainder(NANOS_PER_SECOND);
		try {
			return Duration.ofSeconds(secondsAndNanos[0].longValueExact(), secondsAndNanos[1].longValue());
		}
		catch ( ArithmeticException e ) {
			throw new IllegalArgumentException("duration out of range: '" + durStr + "'", e);
		}
	}

	private static ChronoUnit toDurationUnit(String suffix) {
		return switch ( suffix.toLowerCase(Locale.ROOT) ) {
			case "ms" -> ChronoUnit.MILLIS;
			case "s" -> ChronoUnit.SECONDS;
			case "m" -> ChronoUnit.MINUTES;
			case "h" -> ChronoUnit.HOURS;
			case "d" -> ChronoUnit.DAYS;
			default -> throw new AssertionError("unexpected duration suffix: " + suffix);
		};
	}

	private static String invalidDurationMessage(String durStr) {
		return "invalid duration: '" + durStr
				+ "' (ISO-8601 such as PT30S, or a number with an optional suffix ms, s, m, h or d)";
	}

	/**
	 * duration을 시계 형식({@code [h:]m:ss.fff})의 문자열로 변환한다.
	 * <p>
	 * 1시간 미만이면 {@code m:ss.fff}, 그 이상이면 {@code h:mm:ss.fff}로 쓴다. 분과 시는 앞에 0을 채우지 않고,
	 * 밀리초는 항상 세 자리로 쓴다. 밀리초보다 작은 부분은 버린다. 음수는 앞에 {@code '-'}를 한 번만 붙인다.
	 *
	 * @param duration	변환할 duration.
	 * @return	변환된 문자열 (예: "0:00.500", "0:30.000", "1:30.250", "1:00:00.250", "-0:05.000").
	 * @throws IllegalArgumentException	{@code duration}이 {@code null}인 경우.
	 */
	public static String formatClock(Duration duration) {
		Preconditions.checkNotNullArgument(duration, "duration is null");

		BigInteger[] parts = splitAbs(totalSeconds(duration).abs().movePointRight(3).setScale(0, RoundingMode.DOWN)
																.toBigIntegerExact(), 1000);
		BigInteger[] secs = splitAbs(parts[0], 60);
		BigInteger[] mins = splitAbs(secs[0], 60);
		String sign = (duration.isNegative() && !isZeroMillis(parts)) ? "-" : "";
		if ( mins[0].signum() > 0 ) {
			return String.format(Locale.ROOT, "%s%d:%02d:%02d.%03d", sign, mins[0], mins[1], secs[1], parts[1]);
		}
		else {
			return String.format(Locale.ROOT, "%s%d:%02d.%03d", sign, mins[1], secs[1], parts[1]);
		}
	}

	/**
	 * 밀리초 값을 시계 형식의 문자열로 변환한다. {@code formatClock(Duration.ofMillis(millis))}와 같다.
	 *
	 * @param millis	밀리초 값.
	 * @return	변환된 문자열.
	 * @see #formatClock(Duration)
	 */
	public static String formatClock(long millis) {
		return formatClock(Duration.ofMillis(millis));
	}

	/**
	 * duration을 짧은 단위 표기({@code 1h 0m 5s})의 문자열로 변환한다.
	 * <p>
	 * 초 단위로 반올림({@link RoundingMode#HALF_UP})한 뒤, 1분 미만이면 {@code Ns}, 1시간 미만이면 {@code Nm Ns},
	 * 그 이상이면 {@code Nh Nm Ns}로 쓴다. 시는 24를 넘을 수 있다. 음수는 앞에 {@code '-'}를 한 번만 붙이고,
	 * 반올림해서 0초가 되면 {@code 0s}이다.
	 *
	 * @param duration	변환할 duration.
	 * @return	변환된 문자열 (예: "0s", "30s", "1m 30s", "1h 0m 0s", "-1m 30s").
	 * @throws IllegalArgumentException	{@code duration}이 {@code null}인 경우.
	 */
	public static String formatShort(Duration duration) {
		Preconditions.checkNotNullArgument(duration, "duration is null");

		BigInteger total = totalSeconds(duration).abs().setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
		if ( total.signum() == 0 ) {
			return "0s";
		}
		BigInteger[] secs = splitAbs(total, 60);
		BigInteger[] mins = splitAbs(secs[0], 60);
		String sign = duration.isNegative() ? "-" : "";
		if ( mins[0].signum() > 0 ) {
			return String.format(Locale.ROOT, "%s%dh %dm %ds", sign, mins[0], mins[1], secs[1]);
		}
		else if ( mins[1].signum() > 0 ) {
			return String.format(Locale.ROOT, "%s%dm %ds", sign, mins[1], secs[1]);
		}
		else {
			return String.format(Locale.ROOT, "%s%ds", sign, secs[1]);
		}
	}

	/**
	 * 밀리초 값을 짧은 단위 표기의 문자열로 변환한다. {@code formatShort(Duration.ofMillis(millis))}와 같다.
	 *
	 * @param millis	밀리초 값.
	 * @return	변환된 문자열.
	 * @see #formatShort(Duration)
	 */
	public static String formatShort(long millis) {
		return formatShort(Duration.ofMillis(millis));
	}

	/** duration 전체를 초 단위의 정확한 10진수로 나타낸다({@link Duration#toMillis()}처럼 넘치지 않는다). */
	private static BigDecimal totalSeconds(Duration duration) {
		return BigDecimal.valueOf(duration.getSeconds()).add(BigDecimal.valueOf(duration.getNano(), 9));
	}

	/** 음이 아닌 값을 {@code divisor}로 나눈 몫과 나머지. */
	private static BigInteger[] splitAbs(BigInteger value, long divisor) {
		return value.divideAndRemainder(BigInteger.valueOf(divisor));
	}

	private static boolean isZeroMillis(BigInteger[] secondsAndMillis) {
		return secondsAndMillis[0].signum() == 0 && secondsAndMillis[1].signum() == 0;
	}
}
