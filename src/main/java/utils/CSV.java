package utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

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
	
	public char delimeter() {
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
		Stream<String> strm = values.stream();
		if ( m_escape != null ) {
			strm = strm.map(str -> str.replace(""+m_delim, ""+m_escape+m_delim));
		}
		if ( m_quote != null ) {
			strm = strm.map(str -> "\"" + str + "\"");
		}
		return strm.collect(Collectors.joining(""+m_delim));
	}
	
	public static String toString(String[] csv, char delim, char esc) {
		return toString(Arrays.asList(csv), delim, esc);
	}
	
	public static String toString(Collection<String> csv, char delim, char esc) {
		return get().withDelimiter(delim).withEscape(esc).toString(csv);
	}
}
