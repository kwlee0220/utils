package utils;

import java.awt.Dimension;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UnitUtils {
	private UnitUtils() {
		throw new AssertionError("Should not be invoked!!: class=" + UnitUtils.class.getName());
	}

	public static Dimension parseDimension(String str) {
		Dimension dim = new Dimension();
		
		int index = str.toUpperCase().indexOf('X');
		dim.width = Integer.parseInt(str.substring(0, index).trim());
		dim.height = Integer.parseInt(str.substring(index+1).trim());
		
		return dim;
	}

	public static double parseLengthInMeter(String lengthStr) {
	    if ( lengthStr.endsWith("mm") ) {
	    	double mm = Double.parseDouble(lengthStr.substring(0, lengthStr.length()-2));
	    	return mm / 1000.0;
	    }
	    else if ( lengthStr.endsWith("cm") ) {
	    	double cm = Double.parseDouble(lengthStr.substring(0, lengthStr.length()-2));
	    	return cm / 100.0;
	    }
	    else if ( lengthStr.endsWith("km") ) {
	    	double km = Double.parseDouble(lengthStr.substring(0, lengthStr.length()-2));
	    	return km * 1000.0;
	    }
	    else if ( lengthStr.endsWith("m") ) {
	    	return Double.parseDouble(lengthStr.substring(0, lengthStr.length()-1));
	    }
	    else {
	    	return Double.parseDouble(lengthStr);
	    }
	}
	
	public static String toMeterString(double length) {
		if ( length < 1 ) {
			return String.format("%.1fm", length);
		}
		if ( length < 1000 ) {
			return String.format("%.0fm", length);
		}
		
		length = length / 1000;	// km
		return String.format("%.0fkm", length);
	}

	public static long parseByteSize(String szStr) {
		szStr = szStr.toLowerCase();
		
	    if ( szStr.endsWith("m") ) {
	    	double mega = Double.parseDouble(szStr.substring(0, szStr.length()-1));
	    	return Math.round(mega * 1024 * 1024);
	    }
	    else if ( szStr.endsWith("mb") ) {
	    	double mega = Double.parseDouble(szStr.substring(0, szStr.length()-2));
	    	return Math.round(mega * 1024 * 1024);
	    }
	    else if ( szStr.endsWith("k") ) {
	    	double kilo = Double.parseDouble(szStr.substring(0, szStr.length()-1));
	    	return Math.round(kilo * 1024);
	    }
	    else if ( szStr.endsWith("kb") ) {
	    	double kilo = Double.parseDouble(szStr.substring(0, szStr.length()-2));
	    	return Math.round(kilo * 1024);
	    }
	    else if ( szStr.endsWith("g") ) {
	    	double giga = Double.parseDouble(szStr.substring(0, szStr.length()-1));
	    	return Math.round(giga * 1024*1024*1024);
	    }
	    else if ( szStr.endsWith("gb") ) {
	    	double giga = Double.parseDouble(szStr.substring(0, szStr.length()-2));
	    	return Math.round(giga * 1024*1024*1024);
	    }
	    else {
	    	return Long.parseLong(szStr);
	    }
	}
	
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
	
	public static String toByteSizeString(long sz) {
		if ( sz < 1024 ) {
			return "" + sz;
		}
		double fsz = sz / 1024.0;
		if ( fsz < 1024 ) {
			if ( fsz - Math.floor(fsz) > 0 ) {
				return String.format("%.1fkb", fsz);
			}
			else {
				return String.format("%.0fkb", fsz);
			}
		}
		fsz = fsz / 1024.0;
		if ( fsz < 1024 ) {
			if ( fsz - Math.floor(fsz) > 0 ) {
				return String.format("%.1fmb", fsz);
			}
			else {
				return String.format("%.0fmb", fsz);
			}
		}
		fsz = fsz / 1024.0;
		if ( fsz - Math.floor(fsz) > 0 ) {
			return String.format("%.1fgb", fsz);
		}
		else {
			return String.format("%.0fgb", fsz);
		}
	}
	
	public static long parseDurationMillis(String durStr) {
	    if ( durStr.endsWith("ms") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-2));
	    	return value;
	    }
	    else if ( durStr.endsWith("s") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	return TimeUnit.SECONDS.toMillis(value);
	    }
	    else if ( durStr.endsWith("m") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	return TimeUnit.MINUTES.toMillis(value);
	    }
	    else if ( durStr.endsWith("h") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	return TimeUnit.HOURS.toMillis(value);
	    }
	    else {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()));
	    	return value;
	    }
	}
	
	public static Duration parseDuration(String durStr) {
		long millis = -1;
		
		if ( durStr.startsWith("PT") ) {
			return Duration.parse(durStr);
		}
	    if ( durStr.endsWith("ms") ) {
	    	millis = Long.parseLong(durStr.substring(0, durStr.length()-2));
	    }
	    else if ( durStr.endsWith("s") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	millis = TimeUnit.SECONDS.toMillis(value);
	    }
	    else if ( durStr.endsWith("m") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	millis = TimeUnit.MINUTES.toMillis(value);
	    }
	    else if ( durStr.endsWith("h") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	millis = TimeUnit.HOURS.toMillis(value);
	    }
	    else if ( durStr.endsWith("d") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	millis = TimeUnit.DAYS.toMillis(value);
	    }
	    else {
	    	millis = Long.parseLong(durStr.substring(0, durStr.length()));
	    }
	    
    	return Duration.ofMillis(millis);
	}

	public static String toMillisString(long millis) {
		long seconds = millis / 1000;
		if ( seconds == 0 ) {
			return String.format("0.%d", millis);
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

	public static String toDateString(long date, boolean toMilliSeconds) {
		GregorianCalendar c = new GregorianCalendar();
		c.setTime(new Date(date));
	
		return UnitUtils.toDateString(c, toMilliSeconds);
	}

	public static String toDateString(Calendar c, boolean toMilliSeconds) {
		if ( c == null ) {
			return null;
		}
	
		StringBuilder builder = new StringBuilder();
	
		builder.append(String.format("%04d", c.get(Calendar.YEAR)));
		builder.append(String.format("%02d", c.get(Calendar.MONTH) + 1));
		builder.append(String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
	
		int day = c.get(Calendar.HOUR_OF_DAY);
		int min = c.get(Calendar.MINUTE);
		int sec = c.get(Calendar.SECOND);
	
		builder.append('T');
		builder.append(String.format("%02d%02d%02d", day, min, sec));
	
		if ( toMilliSeconds ) {
			builder.append(':').append(c.get(Calendar.MILLISECOND));
		}
	
		return builder.toString();
	}

	public static Calendar parseDateString(String date) {
		if ( date == null ) {
			return null;
		}
	
		try {
			int year = Integer.parseInt(date.substring(0, 4));
			int month = Integer.parseInt(date.substring(4, 6)) - 1;
			int day = Integer.parseInt(date.substring(6, 8));
	
			if ( date.length() > 8 ) {
				int hour = Integer.parseInt(date.substring(9, 11));
				int min = Integer.parseInt(date.substring(11, 13));
				int sec = Integer.parseInt(date.substring(13, 15));
	
				return new GregorianCalendar(year, month, day, hour, min, sec);
			}
			else {
				return new GregorianCalendar(year, month, day, 0, 0, 0);
			}
		}
		catch ( NumberFormatException e ) {
			throw new IllegalArgumentException("invalid date format=" + date);
		}
	}

}
