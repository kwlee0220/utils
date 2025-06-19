package utils;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.Lists;

import utils.func.FOption;
import utils.stream.FStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SplitStream implements Iterator<String> {
	private final String m_str;
	private final int m_delim;
	private int m_pos;
	@Nullable private List<String> m_pusheds = Lists.newArrayList();
	
	public static SplitStream of(String str, char delim) {
		return new SplitStream(str, delim);
	}

	private SplitStream(String str, int delim) {
		m_str = str;
		m_delim = delim;
		m_pos = 0;
	}
	
	public void pushBack(String token) {
		m_pusheds.add(0, token);
	}

	@Override
	public boolean hasNext() {
		return m_pusheds.size() > 0 ||  m_pos < m_str.length();
	}

	@Override
	public String next() {
		return nextToken().getOrThrow(() -> new NoSuchElementException("No more tokens available"));
	}

	public FOption<String> nextToken() {
		if ( m_pusheds.size() > 0 ) {
			return FOption.of(m_pusheds.remove(0));
		}
		
		int index = m_str.indexOf(m_delim, m_pos);
		if ( index < 0 ) {
			if ( hasNext() ) {
				String remaining = m_str.substring(m_pos);
				m_pos = m_str.length();
				
				return FOption.of(remaining);
			}
			else {
				return FOption.empty();
			}
		}
		
		String token = m_str.substring(m_pos, index);
		m_pos = index + 1;
		
		return FOption.of(token);
	}
	
	public String remaining() {
		String remaining = m_str.substring(m_pos);
		if ( m_pusheds.size() > 0 ) {
			return FStream.from(m_pusheds).join("" + m_delim) + ":" + remaining;
		}
		else {
			return remaining;
		}
	}
	
	@Override
	public String toString() {
		int idx = m_pos;
		for ( int i =0; i < m_pusheds.size(); ++i ) {
            idx = m_str.lastIndexOf(m_delim, idx-2);
        }
		
		String remaining = remaining();
		if ( idx >= 0 ) {
			String preStr = m_str.substring(0, idx);
			return String.format("%s ^ %s", preStr, remaining);
		}
		else {
			return String.format("^ %s", remaining);
		}
	}
}
