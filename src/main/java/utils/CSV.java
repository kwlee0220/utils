package utils;

import utils.func.FOption;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CSV {
	private char m_delim;
	private Character m_escape = null;
	private Character m_quote = null;
	private boolean m_inQuote = false;
	
	public static CSV get() {
		return new CSV();
	}
	
	public static CSV getTsv() {
		return new CSV().withDelimiter('\t');
	}
	
	private CSV() {
		m_delim = ',';
	}
	
	public char delimiter() {
		return m_delim;
	}
	
	public CSV withDelimiter(char delim) {
		m_delim = delim;
		return this;
	}
	
	public Character escape() {
		return m_escape;
	}
	
	public CSV withEscape(Character escapeChar) {
		if ( escapeChar != null && escapeChar == m_delim ) {
			throw new IllegalArgumentException("The delimiter character should not "
												+ "same to the escape character");
		}
		m_escape = escapeChar;
		return this;
	}
	
	public Character quote() {
		return m_quote;
	}
	
	public CSV withQuote(Character quoteChar) {
		m_quote = quoteChar;
		return this;
	}
	
	public FStream<String> parse(String line) {
		return new Parser(line);
	}
	
	public static FStream<String> parseCsv(String str) {
		return get().parse(str);
	}
	public static String[] parseCsvAsArray(String str) {
		return parseCsv(str).toArray(String.class);
	}
	
	public static FStream<String> parseCsv(String str, char delim) {
		return get().withDelimiter(delim).parse(str);
	}
	
	public static FStream<String> parseCsv(String str, char delim, char esc) {
		return get().withDelimiter(delim).withEscape(esc).parse(str);
	}
	public static String[] parseCsvAsArray(String str, char delim, char esc) {
		return parseCsv(str, delim, esc).toArray(String.class);
	}
	
	public String toString(FStream<String> values) {
		return values.map(this::encode).join("" + m_delim);
	}
	
	public String toString(Iterable<String> values) {
		return toString(FStream.of(values));
	}
	
	public String toString(String... values) {
		return toString(FStream.of(values));
	}
	
	private String encode(String value) {
		if ( m_escape != null ) {
			value = value.replace(""+m_delim, ""+m_escape+m_delim);
		}
		if ( m_quote != null ) {
			value = m_quote + value + m_quote;
		}
		
		return value;
	}
	
	private class Parser implements FStream<String> {
		private final char[] m_buf;
		private int m_start = 0;
		private final char[] m_accum;
		private int m_accumIdx = 0;
		
		Parser(String str) {
			m_buf = str.toCharArray();
			m_accum = new char[m_buf.length];
		}

		@Override
		public void close() throws Exception {
			m_start = m_buf.length;
		}

		@Override
		public FOption<String> next() {
			if ( m_start > m_buf.length || m_buf.length == 0 ) {
				return FOption.empty();
			}

			m_accumIdx = 0;
	        for (; m_start < m_buf.length; ++m_start ) {
	            char c = m_buf[m_start];

	            if ( m_inQuote ) {
	            	if ( c == m_quote ) {
	                	m_inQuote = !m_inQuote;
	            	}
	            	else {
	                    m_accum[m_accumIdx++] = c;
	            	}
	            }
	            else if ( c == m_delim ) {
                	++m_start;
	            	return FOption.of(new String(m_accum, 0, m_accumIdx));
	            }
                else if ( m_escape != null && c == m_escape ) {
                    if ( ++m_start >= m_buf.length ) {
                        throw new IllegalArgumentException("Corrupted CSV string");
                    }
                    
                    m_accum[m_accumIdx++] = m_buf[m_start];
                }
	            else if ( m_quote != null && c == m_quote ) {
	            	m_inQuote = !m_inQuote;
	            }
	            else {
                    m_accum[m_accumIdx++] = m_buf[m_start];
	            }
	        }
	        
	        if ( m_inQuote ) {
	        	throw new IllegalArgumentException("quote('" + m_quote
	        										+ "') does not match");
	        }
	        
	        ++m_start;
	    	return FOption.of(new String(m_accum, 0, m_accumIdx));
		}
		
		@Override
		public String toString() {
			String prefix = new String(m_buf, 0, m_start);
			String suffix = new String(m_buf, m_start, m_buf.length-m_start);
			
			return String.format("'%s^%s'", prefix, suffix);
		}
	}
}
