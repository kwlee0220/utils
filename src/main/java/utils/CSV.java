package utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

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
	
	public static CSV getDefaultForRead() {
		return new CSV().withDelimiter(',')
						.withEscape('\\')
						.withQuote('"');
	}
	
	public static CSV getDefaultForWrite() {
		return new CSV().withDelimiter(',')
						.withEscape('\\');
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
	
	public static List<String> parseCSV(String str) {
		return parse(str, ',', '\\');
	}
	
	public static String[] parseCSVAsArray(String str) {
		return parseAsArray(str, ',', '\\');
	}
	
	public static List<String> parse(String str, char delim, char esc) {
		return get().withDelimiter(delim).withEscape(esc).parse(str);
	}
	
	public static String[] parseAsArray(String str, char delim, char esc) {
		return get().withDelimiter(delim).withEscape(esc).parseAsArray(str);
	}

	public List<String> parse(String line) {
		if ( line == null ) {
			throw new IllegalArgumentException("CSV string was null");
		}
		if ( line.trim().length() == 0 ) {
			return Collections.<String>emptyList();
		}

        List<String> vList = Lists.newArrayList();
        StringBuffer appender = new StringBuffer();
        char[] buf = line.toCharArray();
        for ( int start = 0; start < buf.length;  ) {
            int i;

            for ( i =start; i < buf.length; ++i ) {
                char c = buf[i];

                if ( m_quote == null || m_inQuote ) {
	                if ( c == m_delim ) {
	    	            vList.add(appender.toString());
	    	            appender = new StringBuffer();
	                    break;
	                }
	                else if ( m_escape != null && c == m_escape ) {
	                    if ( ++i >= buf.length ) {
	                        throw new IllegalArgumentException("Corrupted CSV string");
	                    }
	                	appender.append(buf[i]);
	                }
	                else if ( m_quote != null && c == m_quote ) {
	                	m_inQuote = !m_inQuote;
	                }
	                else {
	                	appender.append(c);
	                }
                }
                else if ( c == m_quote ) {
                	m_inQuote = !m_inQuote;
                }
                else if ( c == m_delim ) {
    	            vList.add(appender.toString());
    	            appender = new StringBuffer();
                	break;
                }
            }

            start = i + 1;
        }

        vList.add(appender.toString());
        return vList;
	}
	
	public String[] parseAsArray(String line) {
		return parse(line).toArray(new String[0]);
	}
	
	public String toString(Collection<String> values) {
		return values.stream().map(this::encode).collect(Collectors.joining(""+m_delim));
	}
	
	public String toString(FStream<String> values) {
		return values.map(this::encode).join("" + m_delim);
	}
	
	public String toString(String... values) {
		return toString(Arrays.asList(values));
	}
	
	public static String toString(String[] csv, char delim, char esc) {
		return toString(Arrays.asList(csv), delim, esc);
	}
	
	public static String toString(Collection<String> csv, char delim, char esc) {
		return get().withDelimiter(delim).withEscape(esc).toString(csv);
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
}
