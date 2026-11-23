package utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 길이 문자열({@code mm}, {@code cm}, {@code m}, {@code km})과 미터 단위 값 사이의 변환을 제공한다.
 * <p>
 * 문자열을 파싱하는 메소드는 {@code null}을 받지 않는다({@link IllegalArgumentException}). 값이 없을 수 있으면
 * 호출하는 쪽에서 {@link utils.func.Nullables#map(Object, java.util.function.Function)} 등으로 거른다.
 * 문자열로 바꿀 때와 단위 접미사를 비교할 때는 기본 로캘과 상관없이 {@link Locale#ROOT}를 쓴다. 따라서 소수점은
 * 항상 {@code '.'}이고 숫자는 ASCII 숫자이며, 출력한 문자열을 같은 클래스의 파싱 메소드로 다시 읽을 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Lengths {
	/** {@link #formatMeters(double)}의 단위. 작은 단위부터 차례로 쓴다. */
	private static final String[] LENGTH_UNITS = {"mm", "cm", "m", "km"};
	/** 각 단위의 1이 몇 미터인가. 단위를 고를 때 쓴다. */
	private static final double[] LENGTH_UNIT_METERS = {0.001, 0.01, 1, 1000};
	/** 미터 값을 각 단위로 바꾸려면 소수점을 오른쪽으로 몇 자리 옮기는가. */
	private static final int[] LENGTH_UNIT_SHIFTS = {3, 2, 0, -3};

	private Lengths() {
		throw new AssertionError("should not be called: " + Lengths.class);
	}

	/**
	 * 단위 접미사가 붙은 길이 문자열을 미터 단위 {@code double}로 변환한다.
	 * <p>
	 * 지원 접미사: {@code mm}, {@code cm}, {@code m}, {@code km}. 대소문자 구분 없음.
	 * 접미사가 없으면 입력 값을 미터로 해석한다.
	 *
	 * @param lengthStr	길이 문자열 (예: "1.5km", "100mm", "5").
	 * @return	미터 단위 {@code double} 값.
	 * @throws IllegalArgumentException	{@code lengthStr}이 {@code null}이거나, 숫자 부분이 파싱되지 않는 경우
	 * 									({@link NumberFormatException}).
	 */
	public static double parseMeters(String lengthStr) {
		Preconditions.checkNotNullArgument(lengthStr, "length string is null");
		String s = lengthStr.toLowerCase(Locale.ROOT);
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
	 * 크기에 따라 {@code mm}(1cm 미만), {@code cm}(1m 미만), {@code m}(1km 미만), {@code km} 중 하나로 쓰고, 숫자는
	 * 소수 첫째 자리까지 반올림({@link RoundingMode#HALF_UP})한 뒤 끝의 {@code .0}을 뺀다. 반올림한 값이 다음 단위의 1에
	 * 이르면 다음 단위로 쓴다(예: 999.96m는 {@code 1000m}가 아니라 {@code 1km}). 음수는 앞에 {@code '-'}를 붙이고,
	 * {@code 0}은 {@code 0m}이다. 결과는 {@link #parseMeters(String)}로 다시 읽을 수 있다(정밀도는 단위의 0.1).
	 *
	 * @param length	미터 단위 길이.
	 * @return	변환된 문자열 (예: "4mm", "4.5mm", "50cm", "1.5m", "100m", "1.5km").
	 * @throws IllegalArgumentException	{@code length}가 NaN이거나 무한대인 경우.
	 */
	public static String formatMeters(double length) {
		Preconditions.checkArgument(Double.isFinite(length), "length is not finite: %s", length);
		if ( length == 0 ) {
			return "0m";
		}

		// 미터 값을 10진수 그대로 다루도록 BigDecimal로 바꾸고, 단위 변환은 소수점 이동으로 한다.
		BigDecimal meters = new BigDecimal(Double.toString(length));
		double abs = Math.abs(length);
		int idx = LENGTH_UNITS.length - 1;
		while ( idx > 0 && abs < LENGTH_UNIT_METERS[idx] ) {
			--idx;
		}
		BigDecimal value = roundLength(meters, idx);
		if ( idx < LENGTH_UNITS.length - 1
			&& value.abs().compareTo(BigDecimal.ONE.movePointLeft(LENGTH_UNIT_SHIFTS[idx+1] - LENGTH_UNIT_SHIFTS[idx])) >= 0 ) {
			// 반올림으로 다음 단위의 1에 이른 경우(예: 999.96m → 1000.0m)
			value = roundLength(meters, ++idx);
		}
		return value.toPlainString() + LENGTH_UNITS[idx];
	}

	private static BigDecimal roundLength(BigDecimal meters, int unitIdx) {
		BigDecimal value = meters.movePointRight(LENGTH_UNIT_SHIFTS[unitIdx]).setScale(1, RoundingMode.HALF_UP);
		return (value.signum() == 0) ? BigDecimal.ZERO : value.stripTrailingZeros();
	}
}
