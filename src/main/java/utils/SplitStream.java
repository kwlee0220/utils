package utils;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.google.common.collect.Lists;

import org.jetbrains.annotations.Nullable;

import utils.func.FOption;
import utils.stream.FStream;

/**
 * 문자열을 한 문자 구분자로 앞에서부터 차례로 잘라 토큰을 내는 {@link Iterator}.
 * <p>
 * 연속된 구분자 사이와 맨 앞 구분자 앞에서는 빈 토큰이 나오지만, 맨 끝 구분자 뒤의 빈 토큰은 나오지 않는다
 * (예: {@code ",a,,b,"} → {@code "", "a", "", "b"}). {@link #pushBack(String)}으로 토큰을 되돌릴 수 있다.
 * 동기화하지 않는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SplitStream implements Iterator<String> {
	private final String m_str;
	private final int m_delim;
	private int m_pos;
	private @Nullable List<String> m_pusheds = Lists.newArrayList();
	
	/**
	 * 주어진 문자열을 분할하는 스트림을 생성한다.
	 *
	 * @param str	분할할 문자열. {@code null}은 검사하지 않으며 이후 호출에서 {@link NullPointerException}이 발생한다.
	 * @param delim	구분자 문자.
	 * @return	생성된 {@code SplitStream}.
	 */
	public static SplitStream of(String str, char delim) {
		return new SplitStream(str, delim);
	}

	private SplitStream(String str, int delim) {
		m_str = str;
		m_delim = delim;
		m_pos = 0;
	}
	
	/**
	 * 토큰을 되돌려 다음 {@link #next()}/{@link #nextToken()}이 이를 먼저 반환하게 한다.
	 * 여러 번 되돌리면 마지막에 되돌린 토큰부터 반환된다.
	 *
	 * @param token	되돌릴 토큰. 원래 문자열에서 나온 토큰이 아니어도 된다.
	 */
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

	/**
	 * 다음 토큰을 반환한다. 되돌린 토큰이 있으면 그것을 먼저 반환한다.
	 *
	 * @return	다음 토큰. 더 이상 토큰이 없으면 {@link FOption#empty()}.
	 */
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
	
	/**
	 * 아직 읽지 않은 나머지 문자열을 반환한다.
	 * <p>
	 * 되돌린 토큰이 있으면 그 토큰들을 이어 붙이고 {@code ':'} 뒤에 나머지 문자열을 붙인다.
	 * <b>주의:</b> 이때 토큰 사이 구분자로 구분자 문자 대신 그 문자의 코드 값(예: {@code ','}이면 {@code "44"})이
	 * 들어간다.
	 *
	 * @return	나머지 문자열.
	 */
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
