package utils;

import utils.func.FOption;
import utils.stream.FStream;

/**
 * CSV/TSV 한 줄을 파싱/직렬화하는 경량 헬퍼.
 * <p>
 * fluent 스타일 빌더로 delimiter/escape/quote 를 설정한 뒤
 * {@link #parse(String)}로 한 줄을 토큰 stream으로 분해하거나,
 * {@link #toString(String...)} 류로 토큰을 한 줄 문자열로 직렬화한다.
 * <p>
 * <b>특수 문자 처리</b>:
 * <ul>
 *   <li>{@code delim} (필수, 기본 {@code ','}) — 토큰 구분자.</li>
 *   <li>{@code escape} (선택) — 다음 한 글자를 literal로 취급. 인용 영역 밖에서만 인식되며
 *       인용 영역 안에서는 quote-doubling만 인식된다.</li>
 *   <li>{@code quote} (선택) — quote로 감싼 영역 안의 delim/escape는 모두 literal로 취급된다.
 *       인용 영역 안의 quote 문자는 RFC 4180 방식 quote-doubling ({@code ""} → 한 개의 {@code "})으로
 *       이스케이프한다.</li>
 * </ul>
 * <p>
 * <b>thread-safety</b>: 인스턴스 필드는 fluent 빌더 호출로만 변경되며, 파싱 상태는
 * 각 {@link #parse(String)} 호출이 만드는 별개의 stream에 격리된다. 빌더 호출이 끝난 인스턴스는
 * 여러 스레드에서 동시에 {@code parse}/{@code toString}을 호출해도 안전하다.
 * <p>
 * <b>인코딩 round-trip 요구사항</b>: {@code toString} 류는 값에 {@code delim}이 포함되어 있는데
 * {@code escape}와 {@code quote}가 모두 미설정인 경우 round-trip이 불가능하므로
 * {@link IllegalArgumentException}으로 fail-fast한다.
 * <p>
 * <b>encoder 우선순위</b>: 두 옵션이 모두 설정된 경우 encoder는 <em>quote-only 경로</em>를 따른다
 * (quote-doubling + 양 끝 wrap). parser가 quote 영역 안에서 escape를 인식하지 않으므로,
 * escape 변환을 함께 적용하면 round-trip이 깨지기 때문이다.
 * <ul>
 *   <li>{@code quote != null} → quote-doubling + wrap (escape 무시).</li>
 *   <li>{@code quote == null && escape != null} → escape doubling 후 delim 이스케이프.</li>
 *   <li>둘 다 {@code null} → 값에 delim이 없으면 그대로, 있으면 IAE.</li>
 * </ul>
 * <p>
 * <b>한 줄 처리 전용</b>: 본 클래스는 한 줄 단위 파싱/직렬화만 지원한다. RFC 4180에서 허용하는
 * quote 영역 안의 embedded newline은 처리하지 않는다 — 멀티라인 CSV의 경우 호출자가 직접 줄을
 * 잘라 한 줄씩 {@link #parse(String)}에 넘겨야 한다.
 * <p>
 * <b>빈 입력 처리</b>: {@link #parse(String)}는 빈 문자열에 대해 토큰 0개를 반환한다
 * ({@code parse("")} → empty). 이는 {@code parse(",")}가 두 개의 빈 토큰을 반환하는 것과
 * 비대칭이며, {@code String.split(",", -1)}이 빈 입력에 대해 {@code [""]}를 반환하는 것과도 다르다.
 * 빈 입력을 "한 줄에 빈 토큰 한 개" 로 취급해야 한다면 호출 측에서 별도 처리가 필요하다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CSV {
	private char m_delim;
	private Character m_escape = null;
	private Character m_quote = null;

	/**
	 * delimiter가 기본값({@code ','})인 CSV 헬퍼를 생성한다.
	 *
	 * @return	새 {@code CSV} 인스턴스
	 */
	public static CSV get() {
		return new CSV();
	}

	/**
	 * delimiter가 탭({@code '\t'})인 TSV 헬퍼를 생성한다.
	 *
	 * @return	새 {@code CSV} 인스턴스 (delimiter = TAB)
	 */
	public static CSV getTsv() {
		return new CSV().withDelimiter('\t');
	}

	private CSV() {
		m_delim = ',';
	}

	/**
	 * 현재 delimiter를 반환한다.
	 *
	 * @return	delimiter 문자
	 */
	public char delimiter() {
		return m_delim;
	}

	/**
	 * delimiter를 설정한다.
	 *
	 * @param delim	delimiter 문자
	 * @return	본 인스턴스 (fluent chain)
	 * @throws IllegalArgumentException	{@code delim}이 현재 escape 또는 quote와 같은 경우
	 */
	public CSV withDelimiter(char delim) {
		if ( m_escape != null && m_escape == delim ) {
			throw new IllegalArgumentException("The delimiter character must not be the same as "
												+ "the escape character");
		}
		if ( m_quote != null && m_quote == delim ) {
			throw new IllegalArgumentException("The delimiter character must not be the same as "
												+ "the quote character");
		}
		m_delim = delim;
		return this;
	}

	/**
	 * 현재 escape 문자를 반환한다.
	 *
	 * @return	escape 문자. 미설정이면 {@code null}.
	 */
	public Character escape() {
		return m_escape;
	}

	/**
	 * escape 문자를 설정한다.
	 * <p>
	 * escape는 인용 영역 밖에서만 인식되며, escape 다음 한 글자를 literal로 취급한다 (예: {@code \,}는
	 * 토큰 종료가 아닌 literal {@code ,}). {@code null}을 전달하면 escape를 비활성화한다.
	 *
	 * @param escapeChar	escape 문자. {@code null}이면 비활성화.
	 * @return	본 인스턴스 (fluent chain)
	 * @throws IllegalArgumentException	{@code escapeChar}가 현재 delimiter 또는 quote와 같은 경우
	 */
	public CSV withEscape(Character escapeChar) {
		if ( escapeChar != null && escapeChar == m_delim ) {
			throw new IllegalArgumentException("The escape character must not be the same as "
												+ "the delimiter character");
		}
		if ( escapeChar != null && m_quote != null && escapeChar.charValue() == m_quote.charValue() ) {
			throw new IllegalArgumentException("The escape character must not be the same as "
												+ "the quote character");
		}
		m_escape = escapeChar;
		return this;
	}

	/**
	 * 현재 quote 문자를 반환한다.
	 *
	 * @return	quote 문자. 미설정이면 {@code null}.
	 */
	public Character quote() {
		return m_quote;
	}

	/**
	 * quote 문자를 설정한다.
	 * <p>
	 * quote 영역 안에서 delim과 escape는 literal로 취급된다. 인용 안의 quote 문자 자체는
	 * RFC 4180 방식 quote-doubling ({@code ""} → 한 개의 {@code "})으로 이스케이프한다.
	 * {@code null}을 전달하면 quote를 비활성화한다.
	 *
	 * @param quoteChar	quote 문자. {@code null}이면 비활성화.
	 * @return	본 인스턴스 (fluent chain)
	 * @throws IllegalArgumentException	{@code quoteChar}가 현재 delimiter 또는 escape와 같은 경우
	 */
	public CSV withQuote(Character quoteChar) {
		if ( quoteChar != null && quoteChar == m_delim ) {
			throw new IllegalArgumentException("The quote character must not be the same as "
												+ "the delimiter character");
		}
		if ( quoteChar != null && m_escape != null && quoteChar.charValue() == m_escape.charValue() ) {
			throw new IllegalArgumentException("The quote character must not be the same as "
												+ "the escape character");
		}
		m_quote = quoteChar;
		return this;
	}

	/**
	 * 한 줄을 토큰 stream으로 파싱한다.
	 * <p>
	 * 파싱은 lazy하게 수행된다 — 반환된 {@link FStream}이 소비될 때마다 다음 토큰이 추출된다.
	 * 입력에 quote가 짝이 안 맞거나 escape가 trailing이면 stream 소비 중에
	 * {@link IllegalArgumentException}이 발생한다.
	 * <p>
	 * 빈 입력에 대해서는 0개 토큰을 반환한다 ({@code parse("")} → empty). 이는 {@code parse(",")}가
	 * 두 개의 빈 토큰을 반환하는 것과 비대칭임에 유의한다.
	 *
	 * @param line	파싱할 한 줄 문자열 (non-null)
	 * @return	토큰을 lazy하게 반환하는 {@link FStream}
	 * @throws IllegalArgumentException	{@code line}이 {@code null}인 경우
	 */
	public FStream<String> parse(String line) {
		Utilities.checkNotNullArgument(line, "line is null");
		return new Parser(line);
	}

	/**
	 * 기본 설정({@code ','} delimiter, escape/quote 미사용)으로 한 줄을 파싱한다.
	 *
	 * @param str	파싱할 문자열
	 * @return	토큰 stream
	 */
	public static FStream<String> parseCsv(String str) {
		return get().parse(str);
	}

	/**
	 * 기본 설정으로 한 줄을 파싱하여 배열로 반환한다.
	 *
	 * @param str	파싱할 문자열
	 * @return	토큰 배열
	 */
	public static String[] parseCsvAsArray(String str) {
		return parseCsv(str).toArray(String.class);
	}

	/**
	 * 지정된 delimiter로 한 줄을 파싱한다.
	 *
	 * @param str	파싱할 문자열
	 * @param delim	delimiter 문자
	 * @return	토큰 stream
	 */
	public static FStream<String> parseCsv(String str, char delim) {
		return get().withDelimiter(delim).parse(str);
	}

	/**
	 * 지정된 delimiter와 escape로 한 줄을 파싱한다.
	 *
	 * @param str	파싱할 문자열
	 * @param delim	delimiter 문자
	 * @param esc	escape 문자
	 * @return	토큰 stream
	 * @throws IllegalArgumentException	{@code esc == delim}인 경우
	 */
	public static FStream<String> parseCsv(String str, char delim, char esc) {
		return get().withDelimiter(delim).withEscape(esc).parse(str);
	}

	/**
	 * 지정된 delimiter로 한 줄을 파싱하여 배열로 반환한다.
	 *
	 * @param str	파싱할 문자열
	 * @param delim	delimiter 문자
	 * @return	토큰 배열
	 */
	public static String[] parseCsvAsArray(String str, char delim) {
		return parseCsv(str, delim).toArray(String.class);
	}

	/**
	 * 지정된 delimiter와 escape로 한 줄을 파싱하여 배열로 반환한다.
	 *
	 * @param str	파싱할 문자열
	 * @param delim	delimiter 문자
	 * @param esc	escape 문자
	 * @return	토큰 배열
	 * @throws IllegalArgumentException	{@code esc == delim}인 경우
	 */
	public static String[] parseCsvAsArray(String str, char delim, char esc) {
		return parseCsv(str, delim, esc).toArray(String.class);
	}

	/**
	 * 토큰 stream을 한 줄 CSV 문자열로 직렬화한다.
	 * <p>
	 * 각 토큰의 인코딩은 quote/escape 설정에 따라 다음 우선순위로 결정된다:
	 * <ul>
	 *   <li>{@code quote != null} → quote-doubling + 양 끝 wrap (escape는 무시됨).</li>
	 *   <li>{@code quote == null && escape != null} → escape doubling 후 delim 이스케이프.</li>
	 *   <li>둘 다 {@code null} → 값에 delim이 포함되어 있으면 IAE.</li>
	 * </ul>
	 *
	 * @param values	직렬화할 토큰 stream
	 * @return	한 줄 CSV 문자열
	 * @throws IllegalArgumentException	값이 delimiter를 포함하지만 escape/quote가 모두 미설정인 경우
	 */
	public String toString(FStream<String> values) {
		return values.map(this::encode).join(String.valueOf(m_delim));
	}

	/**
	 * 토큰 컬렉션을 한 줄 CSV 문자열로 직렬화한다.
	 *
	 * @param values	직렬화할 토큰 컬렉션
	 * @return	한 줄 CSV 문자열
	 * @throws IllegalArgumentException	값이 delimiter를 포함하지만 escape/quote가 모두 미설정인 경우
	 */
	public String toString(Iterable<String> values) {
		return toString(FStream.from(values));
	}

	/**
	 * 가변 인자 토큰들을 한 줄 CSV 문자열로 직렬화한다.
	 *
	 * @param values	직렬화할 토큰들
	 * @return	한 줄 CSV 문자열
	 * @throws IllegalArgumentException	값이 delimiter를 포함하지만 escape/quote가 모두 미설정인 경우
	 */
	public String toString(String... values) {
		return toString(FStream.of(values));
	}
	
	private String encode(String value) {
		if ( m_quote != null ) {
			// quote가 설정된 경우 quote-only 경로를 탄다 — parser는 quote 영역 안에서 escape를
			// 인식하지 않으므로, escape 변환을 함께 적용하면 round-trip이 깨진다.
			// RFC 4180 quote-doubling: 값 안의 quote 문자는 두 번 반복으로 이스케이프한 뒤 양 끝을
			// quote로 감싼다.
			String quoteStr = String.valueOf(m_quote);
			value = value.replace(quoteStr, quoteStr + quoteStr);
			return m_quote + value + m_quote;
		}
		else if ( m_escape != null ) {
			// escape 자체도 round-trip을 위해 doubling으로 이스케이프해야 한다 (escape 먼저, 그 다음 delim).
			String escStr = String.valueOf(m_escape);
			value = value.replace(escStr, escStr + escStr);
			return value.replace(String.valueOf(m_delim), escStr + m_delim);
		}
		else {
			// escape도 quote도 설정되지 않은 상태에서 값이 delim을 포함하면 round-trip이 불가능하므로
			// fail-fast 한다 (조용히 잘못된 CSV를 만들지 않는다).
			if ( value.indexOf(m_delim) >= 0 ) {
				throw new IllegalArgumentException(
						"value contains delimiter('" + m_delim + "') but neither escape nor quote is configured: ["
						+ value + "]");
			}
			return value;
		}
	}
	
	private class Parser implements FStream<String> {
		private final char[] m_buf;
		private int m_start = 0;
		private final char[] m_accum;
		private int m_accumIdx = 0;
		private boolean m_inQuote = false;

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
						// RFC 4180 quote-doubling: 인용 영역 안에서 두 개의 quote는 literal quote 한 개.
						// 그 외에는 quote의 끝.
						if ( m_start + 1 < m_buf.length && m_buf[m_start + 1] == m_quote ) {
							m_accum[m_accumIdx++] = m_quote;
							++m_start;   // 두 번째 quote를 건너뛴다 (loop의 ++ 가 한 번 더 진행).
						}
						else {
							m_inQuote = !m_inQuote;
						}
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
