package utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 바이트 크기 문자열({@code 512B}, {@code 1.5KB}, {@code 10mb} 등, 1024 배수)과 바이트 수 사이의 변환을 제공한다.
 * <p>
 * 문자열을 파싱하는 메소드는 {@code null}을 받지 않는다({@link IllegalArgumentException}). 값이 없을 수 있으면
 * 호출하는 쪽에서 {@link utils.func.Nullables#map(Object, java.util.function.Function)} 등으로 거른다.
 * 문자열로 바꿀 때와 단위 접미사를 비교할 때는 기본 로캘과 상관없이 {@link Locale#ROOT}를 쓴다. 따라서 소수점은
 * 항상 {@code '.'}이고 숫자는 ASCII 숫자이며, 출력한 문자열을 같은 클래스의 파싱 메소드로 다시 읽을 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ByteSizes {
	/** 바이트 크기의 출력 단위. 인덱스가 1024의 지수이다({@code KB}는 1024, {@code MB}는 1024²). */
	private static final String[] BYTE_UNITS = {"B", "KB", "MB", "GB", "TB"};
	/** 바이트 크기 단위 접미사({@code b}, {@code k}/{@code kb}, …, {@code t}/{@code tb}). 대소문자를 구분하지 않는다. */
	private static final Pattern BYTE_UNIT = Pattern.compile("b|[kmgt]b?", Pattern.CASE_INSENSITIVE);
	/** 숫자(부호, 정수 또는 소수)와 선택적인 바이트 단위 접미사. 대소문자를 구분하지 않는다. */
	private static final Pattern BYTE_SIZE = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)\\s*(b|[kmgt]b?)?",
															Pattern.CASE_INSENSITIVE);
	private static final BigDecimal BYTES_PER_KB = BigDecimal.valueOf(1024);


	private ByteSizes() {
		throw new AssertionError("should not be called: " + ByteSizes.class);
	}

	/**
	 * 바이트 크기 문자열을 {@code long}으로 파싱한다.
	 * <p>
	 * 앞뒤 공백은 무시하고, 숫자(부호가 붙을 수 있는 정수 또는 소수) 뒤에 단위 접미사가 올 수 있다. 숫자와 접미사 사이에는
	 * 공백이 올 수 있고, 접미사는 대소문자를 구분하지 않는다.
	 * <ul>
	 *   <li>{@code b} 또는 접미사 없음: 바이트. 소수 부분이 있으면(예: {@code 1.5}, {@code 1.5b}) 예외를 던진다.</li>
	 *   <li>{@code kb}/{@code k}, {@code mb}/{@code m}, {@code gb}/{@code g}, {@code tb}/{@code t}: 1024 배수. 결과가
	 *       정수 바이트가 아니면 가장 가까운 정수로 반올림한다({@link RoundingMode#HALF_UP}, 예: {@code 0.1kb}는 102).</li>
	 * </ul>
	 * {@link #format(long)}의 출력을 다시 읽을 수 있다.
	 *
	 * @param szStr	크기 문자열 (예: "100mb", "5GB", "1.5 TB", "512B", "1024").
	 * @return	바이트 단위 {@code long} 값.
	 * @throws IllegalArgumentException	{@code szStr}이 {@code null}이거나, 지원하지 않는 표기이거나, 접미사가 없거나
	 * 									{@code b}인 값에 소수 부분이 있거나, 값이 {@code long}의 범위를 넘는 경우.
	 */
	public static long parse(String szStr) {
		Preconditions.checkNotNullArgument(szStr, "byte size string is null");

		Matcher matcher = BYTE_SIZE.matcher(szStr.strip());
		if ( !matcher.matches() ) {
			throw new IllegalArgumentException("invalid byte size: '" + szStr
												+ "' (a number with an optional suffix b, kb, mb, gb or tb expected)");
		}
		int exp = byteUnitExponent(matcher.group(2));
		BigDecimal bytes = new BigDecimal(matcher.group(1)).multiply(BYTES_PER_KB.pow(exp));
		if ( exp == 0 && bytes.signum() != 0 && bytes.stripTrailingZeros().scale() > 0 ) {
			throw new IllegalArgumentException("byte count has a fractional part: '" + szStr + "'");
		}
		try {
			return bytes.setScale(0, RoundingMode.HALF_UP).longValueExact();
		}
		catch ( ArithmeticException e ) {
			throw new IllegalArgumentException("byte size out of range: '" + szStr + "'", e);
		}
	}

	/**
	 * 바이트 크기를 지정된 단위와 포맷으로 변환한다.
	 * <p>
	 * 숫자 부분을 {@code pattern}으로 형식화한 뒤 단위를 붙인다. 단위는 입력의 대소문자와 상관없이 {@code B},
	 * {@code KB}, {@code MB}, {@code GB}, {@code TB} 중 하나로 쓴다.
	 *
	 * @param sz		바이트 단위 크기.
	 * @param unit		변환 단위 ({@code b}, {@code k}/{@code kb}, {@code m}/{@code mb}, {@code g}/{@code gb},
	 * 					{@code t}/{@code tb}). 대소문자 구분 없음.
	 * @param pattern	숫자 부분의 {@link String#format(String, Object...)} 형식 (예: {@code "%.1f"}, {@code "%.0f"}).
	 * 					단위와 상관없이 값은 {@code double}로 넘기므로 {@code %d} 같은 정수 형식은 쓸 수 없다.
	 * @return	변환된 문자열 (예: "1.5MB", "100B").
	 * @throws IllegalArgumentException	인자가 {@code null}이거나 지원하지 않는 단위인 경우.
	 */
	public static String format(long sz, String unit, String pattern) {
		Preconditions.checkNotNullArgument(unit, "unit is null");
		Preconditions.checkNotNullArgument(pattern, "pattern is null");
		Preconditions.checkArgument(BYTE_UNIT.matcher(unit).matches(), "unknown byte unit: %s", unit);

		int exp = byteUnitExponent(unit);
		return String.format(Locale.ROOT, pattern, sz / Math.pow(1024, exp)) + BYTE_UNITS[exp];
	}

	/**
	 * 바이트 크기를 사람이 읽기 좋은 단위 문자열로 변환한다.
	 * <p>
	 * 크기가 1024 미만이면 {@code B} 단위로 쓴다(예: {@code 512B}). 그 이상이면 1024 배수 단위({@code KB}, {@code MB},
	 * {@code GB}, {@code TB}) 중 값이 1024 미만이 되는 단위로 바꾸고, 소수 첫째 자리까지 반올림({@link RoundingMode#HALF_UP})한
	 * 뒤 끝의 {@code .0}을 뺀다. 반올림한 값이 1024에 이르면 다음 단위로 쓴다(예: {@code 1023.99KB}는 {@code 1024KB}가
	 * 아니라 {@code 1MB}). {@code TB}보다 큰 단위는 없다. 음수는 절댓값을 같은 규칙으로 쓰고 앞에 {@code '-'}를 붙인다.
	 * 결과는 {@link #parse(String)}로 다시 읽을 수 있다(정밀도는 단위의 0.1).
	 *
	 * @param sz	바이트 단위 크기.
	 * @return	변환된 문자열 (예: "512B", "1KB", "1.5KB", "10MB", "5TB").
	 */
	public static String format(long sz) {
		// 1024로 나누는 것은 10진수에서도 나누어떨어지므로(1024 = 2^10) BigDecimal로 정확하게 계산한다.
		BigDecimal value = BigDecimal.valueOf(sz);
		if ( value.abs().compareTo(BYTES_PER_KB) < 0 ) {
			return sz + BYTE_UNITS[0];
		}

		int idx = 0;
		while ( idx < BYTE_UNITS.length - 1 && value.abs().compareTo(BYTES_PER_KB) >= 0 ) {
			value = value.divide(BYTES_PER_KB);
			++idx;
		}
		BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP);
		if ( idx < BYTE_UNITS.length - 1 && rounded.abs().compareTo(BYTES_PER_KB) >= 0 ) {
			// 반올림으로 1024에 이른 경우(예: 1023.99kb → 1024.0kb)
			rounded = value.divide(BYTES_PER_KB).setScale(1, RoundingMode.HALF_UP);
			++idx;
		}
		return rounded.stripTrailingZeros().toPlainString() + BYTE_UNITS[idx];
	}

	/** 바이트 단위 접미사의 1024 지수. {@code null}(접미사 없음)과 {@code b}는 0이다. */
	private static int byteUnitExponent(String suffix) {
		if ( suffix == null ) {
			return 0;
		}
		return switch ( Character.toLowerCase(suffix.charAt(0)) ) {
			case 'b' -> 0;
			case 'k' -> 1;
			case 'm' -> 2;
			case 'g' -> 3;
			case 't' -> 4;
			default -> throw new AssertionError("unexpected byte unit: " + suffix);
		};
	}
}
