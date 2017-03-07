package utils;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Duration {
	private final long m_value;
	private final TimeUnit m_unit;
	
	public static Duration milliseconds(long value) {
		return new Duration(value, TimeUnit.MILLISECONDS);
	}
	
	public static Duration seconds(long value) {
		return new Duration(value, TimeUnit.SECONDS);
	}
	
	public static Duration minutes(long value) {
		return new Duration(value, TimeUnit.MINUTES);
	}
	
	public static Duration hours(long value) {
		return new Duration(value, TimeUnit.HOURS);
	}
	
	public Duration(long value, TimeUnit unit) {
		Preconditions.checkArgument(unit == TimeUnit.MILLISECONDS || unit == TimeUnit.SECONDS
									|| unit == TimeUnit.MINUTES || unit == TimeUnit.HOURS,
									"Unsupported Timeunit: unit=" + unit);
		
		m_value = value;
		m_unit = unit;
	}
	
	public long asMillis() {
		return m_unit.toMillis(m_value);
	}
	
	public static Duration parseDuration(String durStr) {
	    if ( durStr.endsWith("ms") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-2));
	    	return new Duration(value, TimeUnit.MILLISECONDS);
	    }
	    else if ( durStr.endsWith("s") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	return new Duration(value, TimeUnit.SECONDS);
	    }
	    else if ( durStr.endsWith("m") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	return new Duration(value, TimeUnit.MINUTES);
	    }
	    else if ( durStr.endsWith("h") ) {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()-1));
	    	return new Duration(value, TimeUnit.HOURS);
	    }
	    else {
	    	long value = Long.parseLong(durStr.substring(0, durStr.length()));
	    	return new Duration(value, TimeUnit.MILLISECONDS);
	    }
	}
	
	@Override
	public String toString() {
		switch ( m_unit ) {
			case MILLISECONDS:
				return m_value + "ms";
			case SECONDS:
				return m_value + "s";
			case MINUTES:
				return m_value + "m";
			case HOURS:
				return m_value + "h";
			default:
				throw new AssertionError();
		}
	}
}
