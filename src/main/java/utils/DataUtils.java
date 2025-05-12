package utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
			return ((Double)obj).intValue();
		}
		if ( obj instanceof Boolean ) {
			return ((Boolean)obj) ? 1 : 0;
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
			return ((Double)obj).longValue();
		}
		if ( obj instanceof Boolean ) {
			return ((Boolean)obj) ? 1 : 0;
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
			return ((Double)obj).shortValue();
		}
		if ( obj instanceof Boolean ) {
			return (short)(((Boolean)obj) ? 1 : 0);
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
			return (byte)((Double)obj).longValue();
		}
		if ( obj instanceof Boolean ) {
			return (byte)(((Boolean)obj) ? 1 : 0);
		}
		
		throw new IllegalArgumentException("unknown byte value=" + obj);
	}
	
	public static boolean asBoolean(Object obj, boolean defValue) {
		if ( obj == null ) {
			return defValue;
		}
		if ( obj instanceof Boolean ) {
			return ((Boolean)obj).booleanValue();
		}
		if ( obj instanceof Integer ) {
			return ((Integer)obj).intValue() != 0;
		}
		if ( obj instanceof Long ) {
			return ((Long)obj).longValue() != 0;
		}
		if ( obj instanceof String ) {
			String str = ((String)obj).trim();
			if ( str.equalsIgnoreCase("false") || str.equalsIgnoreCase("no") ) {
				return false;
			}
			return str.length() > 0;
		}
		if ( obj instanceof Double ) {
			return ((Double)obj).compareTo(0.0) != 0;
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
		
		throw new IllegalArgumentException("unknown double value='" + obj + "'");
	}
	
	public static boolean asBoolean(Object obj) {
		return asBoolean(obj, false);
	}
	
	public static Instant asInstant(Object obj) {
		if ( obj instanceof String ) {
			String str = (String)obj;
			if ( str.indexOf('Z') >= 0 ) {
				return Instant.parse(str);
			}
			else {
				LocalDateTime ldt = LocalDateTime.parse(str);
				return LocalDateTimes.toInstant(ldt);
			}
		}
		else if ( obj instanceof Instant ) {
			return (Instant)obj;
		}
		else {
			throw new IllegalArgumentException("Not Instant object: obj=" + obj);
		}
	}
	
	public static LocalDateTime asDatetime(Object obj) {
		if ( obj instanceof Instant ) {
			return LocalDateTimes.fromInstant((Instant)obj);
		}
		else if ( obj instanceof LocalDateTime ) {
			return (LocalDateTime)obj;
		}
		else if ( obj instanceof Date ) {
			return ((Date)obj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
		}
		else {
			throw new IllegalArgumentException("Not DateTime object: obj=" + obj);
		}
	}
	
	public static java.sql.Date asDate(Object obj) {
		if ( obj instanceof java.sql.Date ) {
			return (java.sql.Date)obj;
		}
		else if ( obj instanceof LocalDate ) {
			long epoch = ((LocalDate)obj).atStartOfDay(ZoneId.systemDefault()).toInstant().getEpochSecond();
			return new java.sql.Date(epoch);
		}
		else if ( obj instanceof Date ) {
			return new java.sql.Date(((Date)obj).getTime());
		}
		else {
			throw new IllegalArgumentException("Not Date object: obj=" + obj);
		}
	}
	
	public static LocalTime asTime(Object obj) {
		if ( obj instanceof LocalTime ) {
			return (LocalTime)obj;
		}
		else {
			throw new IllegalArgumentException("Not asTime: obj=" + obj);
		}
	}
}
