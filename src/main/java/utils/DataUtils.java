package utils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DataUtils {
	private DataUtils() {
		throw new AssertionError("Should not be called: " + DataUtils.class);
	}
	
	public static String asString(Object obj) {
		return obj != null ? obj.toString() : null;
	}
	
	public static int asInt(Object obj) {
		if ( obj == null ) {
			return 0;
		}
		if ( obj instanceof Integer ) {
			return ((Integer)obj).intValue();
		}
		if ( obj instanceof Long ) {
			return (int)((Long)obj).intValue();
		}
		if ( obj instanceof String ) {
			return Integer.parseInt(((String)obj).trim());
		}
		if ( obj instanceof Double ) {
			checkIntegral((Double)obj, "int");
			return ((Double)obj).intValue();
		}
		if ( obj instanceof Boolean ) {
			return ((Boolean)obj) ? 1 : 0;
		}
		if ( obj instanceof Number n ) {
			// 위에서 다루지 않은 숫자 타입(BigDecimal, BigInteger 등).
			checkIntegral(n, "int");
			return n.intValue();
		}
		
		throw new IllegalArgumentException("unknown int value=" + obj);
	}
	
	public static long asLong(Object obj) {
		if ( obj == null ) {
			return 0;
		}
		if ( obj instanceof Integer ) {
			return ((Integer)obj).intValue();
		}
		if ( obj instanceof Long ) {
			return ((Long)obj).longValue();
		}
		if ( obj instanceof Short ) {
			return ((Short)obj).shortValue();
		}
		if ( obj instanceof Byte ) {
			return ((Byte)obj).byteValue();
		}
		if ( obj instanceof String ) {
			return Long.parseLong(((String)obj).trim());
		}
		if ( obj instanceof Double ) {
			checkIntegral((Double)obj, "long");
			return ((Double)obj).longValue();
		}
		if ( obj instanceof Boolean ) {
			return ((Boolean)obj) ? 1 : 0;
		}
		if ( obj instanceof Number n ) {
			// 위에서 다루지 않은 숫자 타입(BigDecimal, BigInteger 등).
			checkIntegral(n, "long");
			return n.longValue();
		}
		
		throw new IllegalArgumentException("unknown long value=" + obj);
	}
	
	public static short asShort(Object obj) {
		if ( obj == null ) {
			return 0;
		}
		if ( obj instanceof Short ) {
			return ((Short)obj).shortValue();
		}
		if ( obj instanceof Integer ) {
			return ((Integer)obj).shortValue();
		}
		if ( obj instanceof Long ) {
			return (short)((Long)obj).shortValue();
		}
		if ( obj instanceof String ) {
			return Short.parseShort(((String)obj).trim());
		}
		if ( obj instanceof Double ) {
			checkIntegral((Double)obj, "short");
			return ((Double)obj).shortValue();
		}
		if ( obj instanceof Boolean ) {
			return (short)(((Boolean)obj) ? 1 : 0);
		}
		if ( obj instanceof Number n ) {
			// 위에서 다루지 않은 숫자 타입(BigDecimal, BigInteger 등).
			checkIntegral(n, "short");
			return n.shortValue();
		}
		
		throw new IllegalArgumentException("unknown short value=" + obj);
	}
	
	public static byte asByte(Object obj) {
		if ( obj == null ) {
			return 0;
		}
		if ( obj instanceof Byte ) {
			return ((Byte)obj).byteValue();
		}
		if ( obj instanceof Integer ) {
			return (byte)((Integer)obj).intValue();
		}
		if ( obj instanceof Long ) {
			return (byte)((Long)obj).longValue();
		}
		if ( obj instanceof String ) {
			return Byte.parseByte(((String)obj).trim());
		}
		if ( obj instanceof Double ) {
			checkIntegral((Double)obj, "byte");
			return (byte)((Double)obj).longValue();
		}
		if ( obj instanceof Boolean ) {
			return (byte)(((Boolean)obj) ? 1 : 0);
		}
		if ( obj instanceof Number n ) {
			// 위에서 다루지 않은 숫자 타입(BigDecimal, BigInteger 등).
			checkIntegral(n, "byte");
			return n.byteValue();
		}
		
		throw new IllegalArgumentException("unknown byte value=" + obj);
	}
	
	public static boolean asBoolean(Object obj, boolean defValue) {
		if ( obj == null ) {
			return defValue;
		}
		if ( obj instanceof Boolean bool1 ) {
			return bool1.booleanValue();
		}
		if ( obj instanceof Integer int1 ) {
			return int1.intValue() != 0;
		}
		if ( obj instanceof Long long1 ) {
			return long1.longValue() != 0;
		}
		if ( obj instanceof String str1 ) {
			String str = str1.trim();
			if ( str.equalsIgnoreCase("false") || str.equalsIgnoreCase("no") || str.equals("0") ) {
				return false;
			}
			return str.length() > 0;
		}
		if ( obj instanceof Double ) {
			return ((Double)obj).compareTo(0.0) != 0;
		}
		if ( obj instanceof Number n ) {
			// 위에서 다루지 않은 숫자 타입(BigDecimal, BigInteger 등).
			return n.doubleValue() != 0;
		}
		
		throw new IllegalArgumentException("unknown boolean value=" + obj);
	}
	
	public static float asFloat(Object obj) {
		if ( obj == null ) {
			return 0;
		}
		if ( obj instanceof Float ) {
			return ((Float)obj).floatValue();
		}
		if ( obj instanceof Integer ) {
			return ((Integer)obj).floatValue();
		}
		if ( obj instanceof Long ) {
			return ((Long)obj).floatValue();
		}
		if ( obj instanceof String ) {
			return Float.parseFloat(((String)obj).trim());
		}
		if ( obj instanceof Double ) {
			return ((Double)obj).floatValue();
		}
		if ( obj instanceof Boolean ) {
			return ((Boolean)obj) ? 1 : 0;
		}
		if ( obj instanceof Number n ) {
			// 위에서 다루지 않은 숫자 타입(BigDecimal, BigInteger 등).
			return n.floatValue();
		}
		
		throw new IllegalArgumentException("unknown float value='" + obj + "'");
	}
	
	public static double asDouble(Object obj) {
		if ( obj == null ) {
			return 0;
		}
		if ( obj instanceof Double ) {
			return ((Double)obj).doubleValue();
		}
		if ( obj instanceof Float ) {
			return ((Float)obj).doubleValue();
		}
		if ( obj instanceof Integer ) {
			return ((Integer)obj).doubleValue();
		}
		if ( obj instanceof Long ) {
			return ((Long)obj).doubleValue();
		}
		if ( obj instanceof String ) {
			return Double.parseDouble(((String)obj).trim());
		}
		if ( obj instanceof Boolean ) {
			return ((Boolean)obj) ? 1 : 0;
		}
		if ( obj instanceof Number n ) {
			// 위에서 다루지 않은 숫자 타입(BigDecimal, BigInteger 등).
			return n.doubleValue();
		}
		
		throw new IllegalArgumentException("unknown double value='" + obj + "'");
	}
	
	public static boolean asBoolean(Object obj) {
		return asBoolean(obj, false);
	}
	
	/**
	 * 정수 타입으로 변환할 숫자에 소수 부분이 있는지 검사한다.
	 * <p>
	 * 소수 부분을 버리면 값이 조용히 바뀌므로 오류로 처리한다. {@code 12.0}처럼 소수 부분이 0이면 통과한다.
	 * NaN과 무한대도 정수로 나타낼 수 없으므로 오류로 처리한다.
	 *
	 * @param n			검사할 숫자.
	 * @param typeName	변환할 정수 타입의 이름(오류 메시지용).
	 * @throws IllegalArgumentException	소수 부분이 있는 경우.
	 */
	private static void checkIntegral(Number n, String typeName) {
		boolean integral;
		if ( n instanceof BigDecimal bd ) {
			integral = bd.signum() == 0 || bd.stripTrailingZeros().scale() <= 0;
		}
		else if ( n instanceof Double || n instanceof Float ) {
			double d = n.doubleValue();
			integral = !Double.isNaN(d) && !Double.isInfinite(d) && d == Math.rint(d);
		}
		else {
			// 정수형 숫자(Integer, Long, Short, Byte, BigInteger 등)
			integral = true;
		}
		if ( !integral ) {
			throw new IllegalArgumentException(String.format("cannot convert a number with a fractional part to %s: %s",
																typeName, n));
		}
	}
	
	/**
	 * 시각을 나타내는 객체를 {@link Instant}로 변환한다.
	 * <p>
	 * {@link Instant}, 시각 문자열({@link Instants#fromString(String)}), {@link Timestamp}, {@link Date}
	 * ({@link java.sql.Date} 포함), {@link OffsetDateTime}, {@link ZonedDateTime}, {@link LocalDateTime}을 받는다.
	 * {@link LocalDateTime}처럼 시간대가 없는 값은 시스템 기본 시간대의 시각으로 해석한다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 시각. {@code obj}가 {@code null}이면 {@code null}.
	 * @throws IllegalArgumentException	시각을 나타내는 객체가 아닌 경우.
	 */
	public static Instant asInstant(Object obj) {
		if ( obj == null ) {
			return null;
		}
		else if ( obj instanceof Instant inst ) {
			return inst;
		}
		else if ( obj instanceof String str ) {
			return Instants.fromString(str);
		}
		else if ( obj instanceof Timestamp ts ) {
			// 나노초를 유지하도록 Date보다 먼저 처리한다.
			return ts.toInstant();
		}
		else if ( obj instanceof Date dt ) {
			// java.sql.Date.toInstant()는 UnsupportedOperationException을 던지므로 getTime()을 쓴다.
			return Instant.ofEpochMilli(dt.getTime());
		}
		else if ( obj instanceof OffsetDateTime odt ) {
			return odt.toInstant();
		}
		else if ( obj instanceof ZonedDateTime zdt ) {
			return zdt.toInstant();
		}
		else if ( obj instanceof LocalDateTime ldt ) {
			return ldt.atZone(ZoneId.systemDefault()).toInstant();
		}
		else {
			throw new IllegalArgumentException("Not Instant object: obj=" + obj);
		}
	}
	
	/**
	 * 시각을 나타내는 객체를 시스템 기본 시간대의 {@link LocalDateTime}으로 변환한다.
	 * <p>
	 * {@link LocalDateTime}은 그대로 반환하고, 그 밖에는 {@link #asInstant(Object)}가 받는 객체를 받는다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 지역 시각. {@code obj}가 {@code null}이면 {@code null}.
	 * @throws IllegalArgumentException	시각을 나타내는 객체가 아닌 경우.
	 */
	public static LocalDateTime asDatetime(Object obj) {
		if ( obj == null ) {
			return null;
		}
		else if ( obj instanceof LocalDateTime ldt ) {
			return ldt;
		}
		else {
			return LocalDateTimes.fromInstant(asInstant(obj));
		}
	}
	
	/**
	 * 날짜를 나타내는 객체를 {@link java.sql.Date}로 변환한다.
	 * <p>
	 * {@link java.sql.Date}, {@link LocalDate}, {@link Date}({@link Timestamp} 포함)를 받는다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 날짜. {@code obj}가 {@code null}이면 {@code null}.
	 * @throws IllegalArgumentException	날짜를 나타내는 객체가 아닌 경우.
	 */
	public static java.sql.Date asDate(Object obj) {
		if ( obj == null ) {
			return null;
		}
		else if ( obj instanceof java.sql.Date dt ) {
			return dt;
		}
		else if ( obj instanceof LocalDate ldt ) {
			return java.sql.Date.valueOf(ldt);
		}
		else if ( obj instanceof Date dt ) {
			return new java.sql.Date(dt.getTime());
		}
		else {
			throw new IllegalArgumentException("Not Date object: obj=" + obj);
		}
	}
	
	/**
	 * 하루 중의 시각을 나타내는 객체를 {@link LocalTime}으로 변환한다.
	 * <p>
	 * {@link LocalTime}, {@link java.sql.Time}, {@code HH:mm[:ss[.SSS]]} 형식의 문자열을 받는다.
	 *
	 * @param obj	변환할 객체.
	 * @return	변환된 시각. {@code obj}가 {@code null}이거나 빈 문자열이면 {@code null}.
	 * @throws IllegalArgumentException	시각을 나타내는 객체가 아니거나 문자열 형식이 맞지 않는 경우.
	 */
	public static LocalTime asTime(Object obj) {
		if ( obj == null ) {
			return null;
		}
		else if ( obj instanceof LocalTime lt ) {
			return lt;
		}
		else if ( obj instanceof java.sql.Time time ) {
			return time.toLocalTime();
		}
		else if ( obj instanceof String str ) {
			if ( str.isBlank() ) {
				return null;
			}
			try {
				return LocalTime.parse(str.trim());
			}
			catch ( DateTimeParseException e ) {
				throw new IllegalArgumentException("Invalid time string: " + str, e);
			}
		}
		else {
			throw new IllegalArgumentException("Not LocalTime object: obj=" + obj);
		}
	}
}
