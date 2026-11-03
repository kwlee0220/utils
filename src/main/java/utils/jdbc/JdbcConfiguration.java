package utils.jdbc;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;

/**
 * JDBC 접속 설정(드라이버 클래스, JDBC URL, 사용자, 비밀번호, 최대 풀 크기)을 담는 설정 객체.
 * <p>
 * JDBC URL만 필수이며 {@link #setJdbcUrl(String)}은 {@code null}을 거부한다. 드라이버 클래스 이름·사용자·비밀번호는
 * 선택 항목이라 {@code null}을 받는다({@link JdbcProcessor#builder(JdbcConfiguration)}는 드라이버가 없으면 URL로
 * 추정하고, 사용자·비밀번호가 없으면 드라이버에 넘기지 않는다).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcConfiguration {
	private @Nullable String m_driverClassName;
	private String m_jdbcUrl;
	private @Nullable String m_user;
	private @Nullable String m_password;
	private int m_maxPoolSize = 0;
	
	/**
	 * 모든 항목이 비어 있는(최대 풀 크기는 0인) {@code JdbcConfiguration} 객체를 생성한다.
	 */
	public JdbcConfiguration() { }
	
	/**
	 * JDBC 드라이버 클래스 이름을 얻는다.
	 *
	 * @return JDBC 드라이버 클래스 이름. 지정하지 않았으면 {@code null}.
	 */
	public @Nullable String getDriverClassName() {
		return m_driverClassName;
	}

	/**
	 * JDBC 드라이버 클래스 이름을 설정한다.
	 *
	 * @param name  JDBC 드라이버 클래스 이름. {@code null}이면 지정하지 않은 것으로 한다.
	 */
	public void setDriverClassName(@Nullable String name) {
		m_driverClassName = name;
	}
	
	/**
	 * JDBC URL을 반환한다.
	 *
	 * @return	JDBC URL.
	 */
	public String getJdbcUrl() {
		return m_jdbcUrl;
	}

	/**
	 * JDBC URL을 설정한다.
	 *
	 * @param jdbcUrl JDBC URL. {@code null}이면 안 된다.
	 * @throws IllegalArgumentException	{@code jdbcUrl}이 {@code null}인 경우.
	 */
	public void setJdbcUrl(String jdbcUrl) {
		Preconditions.checkNotNullArgument(jdbcUrl, "jdbcUrl is null");
		m_jdbcUrl = jdbcUrl;
	}
	
	/**
	 * JDBC connection을 위한 사용자 이름을 반환한다.
	 *
	 * @return 사용자 이름. 지정하지 않았으면 {@code null}.
	 */
	public @Nullable String getUser() {
		return m_user;
	}

	/**
	 * JDBC connection을 위한 사용자 이름을 설정한다.
	 *
	 * @param user 사용자 이름. {@code null}이면 지정하지 않은 것으로 한다.
	 */
	public void setUser(@Nullable String user) {
		m_user = user;
	}

	/**
	 * JDBC connection을 위한 password를 반환한다.
	 *
	 * @return 비밀번호. 지정하지 않았으면 {@code null}.
	 */
	public @Nullable String getPassword() {
		return m_password;
	}

	/**
	 * JDBC connection을 위한 password를 설정한다.
	 *
	 * @param password 비밀번호. {@code null}이면 지정하지 않은 것으로 한다.
	 */
	public void setPassword(@Nullable String password) {
		m_password = password;
	}
	
	/**
	 * JDBC connection을 위한 최대 풀 크기를 반환한다.
	 * <p>
	 * 설정 값이다. {@link JdbcProcessor#builder(JdbcConfiguration)}로 만든 processor가 쓰는 풀은 JVM 안에서 같은 연결
	 * 정보끼리 공유하므로, 같은 연결 정보의 풀이 이미 있으면 실제 풀 크기는 그 풀을 처음 만들 때의 값을 따른다.
	 *
	 * @return 최대	풀 크기
	 */
	public int getMaxPoolSize() {
		return m_maxPoolSize;
	}

	/**
	 * JDBC connection을 위한 최대 풀 크기를 설정한다.
	 *
	 * @param maxPoolSize 최대 풀 크기
	 */
	public void setMaxPoolSize(int maxPoolSize) {
		m_maxPoolSize = maxPoolSize;
	}
	
	/**
	 * JDBC URL과 사용자 이름을 {@code JdbcConfiguration[url=<jdbcUrl>, user=<user>]} 형식으로 반환한다.
	 * <p>
	 * 로그나 예외 메시지에 비밀번호가 노출되지 않도록 비밀번호는 출력하지 않는다.
	 * 따라서 반환 값을 {@link #parse(String)}에 넘겨 접속 설정을 복원할 수는 없다.
	 *
	 * @return	비밀번호를 제외한 접속 설정 문자열.
	 */
	@Override
	public String toString() {
		return String.format("JdbcConfiguration[url=%s, user=%s]",
							(m_jdbcUrl != null) ? JdbcUtils.maskPassword(m_jdbcUrl) : null, m_user);
	}

	/**
	 * 질의 파라미터가 붙은 JDBC URL({@code <jdbcUrl>?user=..&password=..&maxPoolSize=..})을
	 * 파싱하여 {@code JdbcConfiguration} 객체를 생성한다.
	 * <p>
	 * 파라미터 중 {@code user}, {@code password}, {@code maxPoolSize}만 떼어 내 설정에 반영하고, 그 밖의 파라미터
	 * (예: {@code useSSL=false}, {@code sslmode=require})는 원래 모습 그대로 JDBC URL에 남겨 드라이버에 전달한다.
	 * 떼어 낸 세 값은 {@code %XX} escape만 UTF-8로 디코딩하며 {@code '+'}는 그대로 둔다(비밀번호의 {@code '+'}가
	 * 공백으로 바뀌지 않도록). {@code '?'}가 없으면 문자열 전체가 JDBC URL이다. 드라이버 클래스 이름은 설정하지 않는다
	 * (빌더가 URL로 추정한다).
	 * <p>
	 * 이 형식의 문자열로 {@link JdbcProcessor}를 만들려면 {@code JdbcProcessor.builder(JdbcConfiguration.parse(s))}를
	 * 쓴다.
	 *
	 * @param fullJdbcUrl	파싱할 JDBC URL 문자열.
	 * @return	파싱된 {@code JdbcConfiguration} 객체.
	 * @throws IllegalArgumentException	{@code fullJdbcUrl}이 {@code null}이거나, 파라미터의 {@code %} escape가
	 * 									잘못됐거나, {@code maxPoolSize} 값이 정수가 아닌 경우.
	 */
	public static JdbcConfiguration parse(String fullJdbcUrl) {
		Preconditions.checkNotNullArgument(fullJdbcUrl, "fullJdbcUrl is null");

		JdbcConfiguration config = new JdbcConfiguration();

		int index = fullJdbcUrl.indexOf('?');
		if ( index == -1 ) {
			config.setJdbcUrl(fullJdbcUrl);
			return config;
		}

		List<String> kept = new ArrayList<>();
		for ( String pair: fullJdbcUrl.substring(index + 1).split("&") ) {
			if ( pair.isEmpty() ) {
				continue;
			}
			String[] kv = pair.split("=", 2);
			String key = (kv.length == 2) ? decodeParameter(kv[0]) : null;
			if ( "user".equals(key) ) {
				config.setUser(decodeParameter(kv[1]));
			}
			else if ( "password".equals(key) ) {
				config.setPassword(decodeParameter(kv[1]));
			}
			else if ( "maxPoolSize".equals(key) ) {
				String value = decodeParameter(kv[1]);
				try {
					config.setMaxPoolSize(Integer.parseInt(value));
				}
				catch ( NumberFormatException e ) {
					throw new IllegalArgumentException("invalid maxPoolSize: " + value, e);
				}
			}
			else {
				// 드라이버 옵션은 인코딩된 원래 모습 그대로 URL에 남긴다.
				kept.add(pair);
			}
		}

		String jdbcUrl = fullJdbcUrl.substring(0, index);
		config.setJdbcUrl(kept.isEmpty() ? jdbcUrl : jdbcUrl + "?" + String.join("&", kept));
		return config;
	}

	// %XX escape만 UTF-8로 디코딩한다. URLDecoder는 '+'를 공백으로 바꾸므로 먼저 "%2B"로 바꿔 둔다.
	private static String decodeParameter(String encoded) {
		return URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8);
	}
}
