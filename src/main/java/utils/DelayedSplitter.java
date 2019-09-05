package utils;

import java.util.Objects;

import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class DelayedSplitter {
	private final String m_str;
	private int m_start;
	
	public static DelayedSplitter on(String str) {
		return new DelayedSplitter(str);
	}
	
	public static KeyValue<String,String> parseKeyValue(String str, char delim) {
		DelayedSplitter splitter = DelayedSplitter.on(str);
		FOption<String> opart1 = splitter.cutNext(delim);
		FOption<String> opart2 = splitter.remains();
		
		if ( opart1.isPresent() && opart2.isPresent() ) {
			return KeyValue.of(opart1.get(), opart2.get());
		}
		else {
			throw new IllegalArgumentException("invalid key-value string: " + str);
		}
	}
	
	public static String[] splitIntoTwo(String str, char delim) {
		DelayedSplitter splitter = DelayedSplitter.on(str);
		FOption<String> opart1 = splitter.cutNext(delim);
		FOption<String> opart2 = splitter.remains();
		
		if ( opart1.isPresent() && opart2.isPresent() ) {
			return new String[]{ opart1.get(), opart2.get()};
		}
		else {
			throw new IllegalArgumentException("invalid string: " + str);
		}
	}
	
	private DelayedSplitter(String str) {
		Objects.requireNonNull(str, "string is null");
		
		m_str = str;
		m_start = 0;
	}
	
	public FOption<String> cutNext(char delim) {
		int idx = m_str.indexOf(delim, m_start);
		if ( idx >= 0 ) {
			String part = m_str.substring(m_start, idx);
			m_start = idx+1;
			
			return FOption.of(part);
		}
		else {
			m_start = -1;
			return FOption.empty();
		}
	}
	
	public FOption<String> remains() {
		return ( m_start >= 0 ) ? FOption.of(m_str.substring(m_start)) : FOption.empty();
	}
}