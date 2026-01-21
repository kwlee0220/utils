package utils.jdbc;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import com.google.common.base.Preconditions;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcConfiguration {
	private String m_driverClassName;
	private String m_jdbcUrl;
	private String m_user;
	private String m_password;
	private int m_maxPoolSize = 0;
	
	/**
	 * JDBC 드라이버 클래스 이름을 얻는다.
	 *
	 * @return JDBC 드라이버 클래스 이름.
	 */
	public String getDriverClassName() {
		return m_driverClassName;
	}

	/**
	 * JDBC 드라이버 클래스 이름을 설정한다.
	 *
	 * @param name  JDBC 드라이버 클래스 이름
	 */
	public void setDriverClassName(String name) {
		Preconditions.checkArgument(name != null, "null driver class name");
		
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
	 * @param jdbcUrl the JDBC URL.
	 */
	public void setJdbcUrl(String jdbcUrl) {
		m_jdbcUrl = jdbcUrl;
	}
	
	/**
	 * JDBC connection을 위한 사용자 이름을 반환한다.
	 *
	 * @return 사용자 이름.
	 */
	public String getUser() {
		return m_user;
	}

	/**
	 * JDBC connection을 위한 사용자 이름을 설정한다.
	 *
	 * @param user the user name to set.
	 */
	public void setUser(String user) {
		m_user = user;
	}

	/**
	 * JDBC connection을 위한 password를 반환한다.
	 *
	 * @return the password as a String.
	 */
	public String getPassword() {
		return m_password;
	}

	/**
	 * JDBC connection을 위한 password를 설정한다.
	 *
	 * @param password the password to set.
	 */
	public void setPassword(String password) {
		m_password = password;
	}
	
	/**
	 * JDBC connection을 위한 최대 풀 크기를 반환한다.
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
	
	@Override
	public String toString() {
		return String.format("%s?user=%s&password=%s", m_jdbcUrl, m_user, m_password);
	}

    public static JdbcConfiguration parseString(String fullJdbcUrl) {
    	JdbcConfiguration config = new JdbcConfiguration();

        int index = fullJdbcUrl.indexOf('?');
        if ( index == -1 ) {
        	config.setJdbcUrl(fullJdbcUrl);
        	return config;
        }
        
        String jdbcUrl = fullJdbcUrl.substring(0, index);
        config.setJdbcUrl(jdbcUrl);

        String queryPart = fullJdbcUrl.substring(index + 1);
        String[] pairs = queryPart.split("&");
        for ( String pair : pairs ) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
					String key = URLDecoder.decode(kv[0], "UTF-8");
					String value = URLDecoder.decode(kv[1], "UTF-8");
					if ( key.equals("user") ) {
                        config.setUser(value);
                    }
                    else if ( key.equals("password") ) {
                        config.setPassword(value);
                    }
                    else if ( key.equals("maxPoolSize") ) {
                        config.setMaxPoolSize(Integer.parseInt(value));
                    }
				}
				catch ( UnsupportedEncodingException e ) {
					throw new IllegalArgumentException("Failed to decode URL parameters", e);
				}
			}
		}

		return config;
    }
}
