package utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZoneId;
import java.util.Locale;

/**
 * 타입이 정해지지 않은 값({@link Object})을 기본 타입과 문자열로 변환하는 정적 유틸리티.
 * <p>
 * 숫자·{@code boolean} 변환 규칙:
 * <ul>
 *   <li><b>{@code null}</b>: 인자 하나짜리 메소드({@link #asInt(Object)} 등)는 {@link IllegalArgumentException}을
 *       던진다. {@code null}일 때 쓸 값이 있으면 기본값을 받는 메소드({@link #asInt(Object, int)} 등)를 쓴다.</li>
 *   <li><b>{@link Boolean}</b>: 숫자 변환에서는 {@code true}가 1, {@code false}가 0이다.</li>
 *   <li><b>문자열</b>: 앞뒤 공백을 제거한 뒤 해석한다.</li>
 *   <li><b>정수 변환</b>({@code int}, {@code long}, {@code short}, {@code byte}): 숫자와 숫자 문자열을 같은 규칙으로
 *       바꾼다. 소수 부분이 있거나(예: {@code 12.5}, {@code "12.5"}), NaN·무한대이거나, 대상 타입의 범위를 넘으면
 *       {@link IllegalArgumentException}을 던진다. 소수 부분이 0이면 받는다(예: {@code 12.0}, {@code "12.0"}).</li>
 *   <li><b>실수 변환</b>({@code float}, {@code double}): 정밀도 손실과 범위를 검사하지 않는다.</li>
 * </ul>
 * {@link #asString(Object)}는 {@code null}을 그대로 돌려준다.
 * <p>
 * 날짜·시각 값의 변환은 각 시간 클래스의 {@code from} 메소드({@link Instants#from(Object, java.time.ZoneId)},
 * {@link LocalDateTimes#from(Object, java.time.ZoneId)}, {@link LocalDates#from(Object, java.time.ZoneId)},
 * {@link LocalTimes#from(Object, java.time.ZoneId)})를 쓴다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DataUtils {
	private DataUtils() {
		throw new AssertionError("Should not be called: " + DataUtils.class);
	}
	
	/**
	 * 주어진 객체의 {@link Object#toString()} 결과를 반환한다.
	 *
	 * @param obj	변환할 객체.
	 * @return	문자열. {@code obj}가 {@code null}이면 {@code null}.
	 */
	public static String asString(Object obj) {
		return obj != null ? obj.toString() : null;
	}
	
	/**
	 * 주어진 객체를 {@code int} 값으로 변환한다.
	 * <p>
	 * {@link Number}, {@link String}, {@link Boolean}을 받는다. 규칙은 클래스 설명의 정수 변환과 같다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@code obj}가 {@code null}이거나, 변환할 수 없는 타입이거나, 정수로 해석할 수
	 * 									없거나(소수 부분, NaN·무한대, 형식 오류), {@code int} 범위를 넘는 경우.
	 */
	public static int asInt(Object obj) {
		return (int)toIntegral(obj, "int", Integer.MIN_VALUE, Integer.MAX_VALUE);
	}
	
	/**
	 * 주어진 객체를 {@code int} 값으로 변환한다. {@code obj}가 {@code null}이면 {@code defValue}를 반환한다.
	 * 변환 규칙은 {@link #asInt(Object)}와 같다.
	 *
	 * @param obj		변환할 객체.
	 * @param defValue	{@code obj}가 {@code null}일 때 반환할 값.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@link #asInt(Object)}와 같다({@code null}인 경우 제외).
	 */
	public static int asInt(Object obj, int defValue) {
		return (obj != null) ? asInt(obj) : defValue;
	}
	
	/**
	 * 주어진 객체를 {@code long} 값으로 변환한다.
	 * <p>
	 * {@link Number}, {@link String}, {@link Boolean}을 받는다. 규칙은 클래스 설명의 정수 변환과 같다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@code obj}가 {@code null}이거나, 변환할 수 없는 타입이거나, 정수로 해석할 수
	 * 									없거나(소수 부분, NaN·무한대, 형식 오류), {@code long} 범위를 넘는 경우.
	 */
	public static long asLong(Object obj) {
		return toIntegral(obj, "long", Long.MIN_VALUE, Long.MAX_VALUE);
	}
	
	/**
	 * 주어진 객체를 {@code long} 값으로 변환한다. {@code obj}가 {@code null}이면 {@code defValue}를 반환한다.
	 * 변환 규칙은 {@link #asLong(Object)}와 같다.
	 *
	 * @param obj		변환할 객체.
	 * @param defValue	{@code obj}가 {@code null}일 때 반환할 값.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@link #asLong(Object)}와 같다({@code null}인 경우 제외).
	 */
	public static long asLong(Object obj, long defValue) {
		return (obj != null) ? asLong(obj) : defValue;
	}
	
	/**
	 * 주어진 객체를 {@code short} 값으로 변환한다.
	 * <p>
	 * {@link Number}, {@link String}, {@link Boolean}을 받는다. 규칙은 클래스 설명의 정수 변환과 같다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@code obj}가 {@code null}이거나, 변환할 수 없는 타입이거나, 정수로 해석할 수
	 * 									없거나(소수 부분, NaN·무한대, 형식 오류), {@code short} 범위를 넘는 경우.
	 */
	public static short asShort(Object obj) {
		return (short)toIntegral(obj, "short", Short.MIN_VALUE, Short.MAX_VALUE);
	}
	
	/**
	 * 주어진 객체를 {@code short} 값으로 변환한다. {@code obj}가 {@code null}이면 {@code defValue}를 반환한다.
	 * 변환 규칙은 {@link #asShort(Object)}와 같다.
	 *
	 * @param obj		변환할 객체.
	 * @param defValue	{@code obj}가 {@code null}일 때 반환할 값.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@link #asShort(Object)}와 같다({@code null}인 경우 제외).
	 */
	public static short asShort(Object obj, short defValue) {
		return (obj != null) ? asShort(obj) : defValue;
	}
	
	/**
	 * 주어진 객체를 {@code byte} 값으로 변환한다.
	 * <p>
	 * {@link Number}, {@link String}, {@link Boolean}을 받는다. 규칙은 클래스 설명의 정수 변환과 같다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@code obj}가 {@code null}이거나, 변환할 수 없는 타입이거나, 정수로 해석할 수
	 * 									없거나(소수 부분, NaN·무한대, 형식 오류), {@code byte} 범위를 넘는 경우.
	 */
	public static byte asByte(Object obj) {
		return (byte)toIntegral(obj, "byte", Byte.MIN_VALUE, Byte.MAX_VALUE);
	}
	
	/**
	 * 주어진 객체를 {@code byte} 값으로 변환한다. {@code obj}가 {@code null}이면 {@code defValue}를 반환한다.
	 * 변환 규칙은 {@link #asByte(Object)}와 같다.
	 *
	 * @param obj		변환할 객체.
	 * @param defValue	{@code obj}가 {@code null}일 때 반환할 값.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@link #asByte(Object)}와 같다({@code null}인 경우 제외).
	 */
	public static byte asByte(Object obj, byte defValue) {
		return (obj != null) ? asByte(obj) : defValue;
	}
	
	/**
	 * 주어진 객체를 {@code boolean} 값으로 변환한다. {@code obj}가 {@code null}이면 {@code defValue}를 반환한다.
	 * <ul>
	 *   <li>{@link Boolean}: 그대로.</li>
	 *   <li>{@link Number}: 0이 아니면 {@code true}(NaN도 {@code true}).</li>
	 *   <li>{@link String}: 앞뒤 공백을 제거하고 대소문자를 무시해서, {@code true}·{@code yes}·{@code on}·{@code y}·
	 *       {@code 1}이면 {@code true}, {@code false}·{@code no}·{@code off}·{@code n}·{@code 0}·빈 문자열이면
	 *       {@code false}이다. 그 밖의 문자열(예: {@code "flase"}, {@code "abc"}, {@code "00"})은 예외를 던진다.</li>
	 * </ul>
	 *
	 * @param obj		변환할 객체.
	 * @param defValue	{@code obj}가 {@code null}일 때 반환할 값.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@link Boolean}, {@link Number}, {@link String}이 아니거나, 위 목록에 없는
	 * 									문자열인 경우.
	 */
	public static boolean asBoolean(Object obj, boolean defValue) {
		if ( obj == null ) {
			return defValue;
		}
		if ( obj instanceof Boolean bool ) {
			return bool;
		}
		if ( obj instanceof String str ) {
			return switch ( str.strip().toLowerCase(Locale.ROOT) ) {
				case "true", "yes", "on", "y", "1" -> true;
				case "false", "no", "off", "n", "0", "" -> false;
				default -> throw new IllegalArgumentException("invalid boolean string: '" + str
										+ "' (true/yes/on/y/1 or false/no/off/n/0 expected)");
			};
		}
		if ( obj instanceof BigDecimal bd ) {
			return bd.signum() != 0;
		}
		if ( obj instanceof BigInteger bi ) {
			return bi.signum() != 0;
		}
		if ( obj instanceof Number n ) {
			// 그 밖의 숫자는 double로 비교한다. NaN은 0이 아니므로 true이다.
			return n.doubleValue() != 0;
		}
		
		throw new IllegalArgumentException("cannot convert to boolean: " + describe(obj));
	}
	
	/**
	 * 주어진 객체를 {@code boolean} 값으로 변환한다. 변환 규칙은 {@link #asBoolean(Object, boolean)}과 같다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@code obj}가 {@code null}이거나, {@link #asBoolean(Object, boolean)}이
	 * 									예외를 던지는 경우.
	 */
	public static boolean asBoolean(Object obj) {
		Preconditions.checkNotNullArgument(obj, "cannot convert null to boolean");
		return asBoolean(obj, false);
	}
	
	/**
	 * 주어진 객체를 {@code float} 값으로 변환한다.
	 * <p>
	 * {@link Number}, {@link String}, {@link Boolean}을 받는다. {@link Boolean}은 1/0이고, 문자열은 앞뒤 공백을 제거한 뒤
	 * {@link Float#parseFloat(String)}로 해석한다. {@code double}·{@code long} 등은 정밀도 손실을 검사하지 않고
	 * 변환한다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@code obj}가 {@code null}이거나, 변환할 수 없는 타입이거나, 문자열을 파싱할 수
	 * 									없는 경우({@link NumberFormatException}).
	 */
	public static float asFloat(Object obj) {
		Preconditions.checkNotNullArgument(obj, "cannot convert null to float");
		if ( obj instanceof Number n ) {
			return n.floatValue();
		}
		if ( obj instanceof String str ) {
			return Float.parseFloat(str.trim());
		}
		if ( obj instanceof Boolean bool ) {
			return bool ? 1 : 0;
		}
		
		throw new IllegalArgumentException("cannot convert to float: " + describe(obj));
	}
	
	/**
	 * 주어진 객체를 {@code float} 값으로 변환한다. {@code obj}가 {@code null}이면 {@code defValue}를 반환한다.
	 * 변환 규칙은 {@link #asFloat(Object)}와 같다.
	 *
	 * @param obj		변환할 객체.
	 * @param defValue	{@code obj}가 {@code null}일 때 반환할 값.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@link #asFloat(Object)}와 같다({@code null}인 경우 제외).
	 */
	public static float asFloat(Object obj, float defValue) {
		return (obj != null) ? asFloat(obj) : defValue;
	}
	
	/**
	 * 주어진 객체를 {@code double} 값으로 변환한다.
	 * <p>
	 * {@link Number}, {@link String}, {@link Boolean}을 받는다. {@link Boolean}은 1/0이고, 문자열은 앞뒤 공백을 제거한 뒤
	 * {@link Double#parseDouble(String)}로 해석한다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@code obj}가 {@code null}이거나, 변환할 수 없는 타입이거나, 문자열을 파싱할 수
	 * 									없는 경우({@link NumberFormatException}).
	 */
	public static double asDouble(Object obj) {
		Preconditions.checkNotNullArgument(obj, "cannot convert null to double");
		if ( obj instanceof Number n ) {
			return n.doubleValue();
		}
		if ( obj instanceof String str ) {
			return Double.parseDouble(str.trim());
		}
		if ( obj instanceof Boolean bool ) {
			return bool ? 1 : 0;
		}
		
		throw new IllegalArgumentException("cannot convert to double: " + describe(obj));
	}
	
	/**
	 * 주어진 객체를 {@code double} 값으로 변환한다. {@code obj}가 {@code null}이면 {@code defValue}를 반환한다.
	 * 변환 규칙은 {@link #asDouble(Object)}와 같다.
	 *
	 * @param obj		변환할 객체.
	 * @param defValue	{@code obj}가 {@code null}일 때 반환할 값.
	 * @return	변환된 값.
	 * @throws IllegalArgumentException	{@link #asDouble(Object)}와 같다({@code null}인 경우 제외).
	 */
	public static double asDouble(Object obj, double defValue) {
		return (obj != null) ? asDouble(obj) : defValue;
	}
	
	/**
	 * 정수 변환의 공통 구현. 값을 {@link BigDecimal}로 바꿔 소수 부분과 범위를 검사한 뒤 {@code long}으로 반환한다.
	 *
	 * @param obj		변환할 객체.
	 * @param typeName	대상 타입의 이름(오류 메시지용).
	 * @param min		대상 타입의 최솟값.
	 * @param max		대상 타입의 최댓값.
	 * @return	{@code [min, max]} 범위의 값.
	 * @throws IllegalArgumentException	변환할 수 없는 경우.
	 */
	private static long toIntegral(Object obj, String typeName, long min, long max) {
		Preconditions.checkNotNullArgument(obj, "cannot convert null to %s", typeName);
		
		BigDecimal value;
		if ( obj instanceof Boolean bool ) {
			return bool ? 1 : 0;
		}
		else if ( obj instanceof String str ) {
			try {
				value = new BigDecimal(str.trim());
			}
			catch ( NumberFormatException e ) {
				throw new IllegalArgumentException("cannot convert to " + typeName + ": '" + str + "'", e);
			}
			checkNoFraction(value, typeName, obj);
		}
		else if ( obj instanceof Number n ) {
			value = toIntegralDecimal(n, typeName);
		}
		else {
			throw new IllegalArgumentException("cannot convert to " + typeName + ": " + describe(obj));
		}
		
		if ( value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0 ) {
			throw new IllegalArgumentException(String.format("value out of %s range [%d, %d]: %s",
															typeName, min, max, obj));
		}
		return value.longValue();
	}
	
	/**
	 * 숫자를 소수 부분이 없는 {@link BigDecimal}로 바꾼다.
	 * <p>
	 * 소수 부분을 버리면 값이 조용히 바뀌므로 오류로 처리한다. {@code 12.0}처럼 소수 부분이 0이면 통과한다.
	 * NaN과 무한대도 정수로 나타낼 수 없으므로 오류로 처리한다.
	 */
	private static BigDecimal toIntegralDecimal(Number n, String typeName) {
		BigDecimal value;
		if ( n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte ) {
			return BigDecimal.valueOf(n.longValue());
		}
		else if ( n instanceof BigDecimal bd ) {
			value = bd;
		}
		else if ( n instanceof BigInteger bi ) {
			return new BigDecimal(bi);
		}
		else if ( n instanceof Double || n instanceof Float ) {
			double d = n.doubleValue();
			if ( Double.isNaN(d) || Double.isInfinite(d) ) {
				throw new IllegalArgumentException("cannot convert " + d + " to " + typeName);
			}
			value = new BigDecimal(d);
		}
		else {
			// AtomicLong, LongAdder 등 그 밖의 Number. toString()이 10진수 표기이면 그대로 쓴다.
			try {
				value = new BigDecimal(n.toString());
			}
			catch ( NumberFormatException e ) {
				value = new BigDecimal(n.doubleValue());
			}
		}
		checkNoFraction(value, typeName, n);
		return value;
	}
	
	private static void checkNoFraction(BigDecimal value, String typeName, Object original) {
		if ( value.signum() != 0 && value.stripTrailingZeros().scale() > 0 ) {
			throw new IllegalArgumentException(String.format("cannot convert a number with a fractional part to %s: %s",
															typeName, original));
		}
	}
	
	private static String describe(Object obj) {
		return obj + " (" + obj.getClass().getName() + ")";
	}
}
