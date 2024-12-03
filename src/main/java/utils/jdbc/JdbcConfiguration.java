package utils.jdbc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonInclude(Include.NON_NULL)
public class JdbcConfiguration {
	@JsonIgnore private String m_driverClassName;
	@JsonIgnore private String m_jdbcUrl;
	@JsonIgnore private String m_user;
	@JsonIgnore private String m_password;
	@JsonIgnore private int m_maxPoolSize = 0;
	
	public String getDriverClassName() {
		return m_driverClassName;
	}
	public void setDriverClassName(String name) {
		m_driverClassName = name;
	}
	
	public String getJdbcUrl() {
		return m_jdbcUrl;
	}
	public void setJdbcUrl(String jdbcUrl) {
		m_jdbcUrl = jdbcUrl;
	}
	
	public String getUser() {
		return m_user;
	}
	public void setUser(String user) {
		m_user = user;
	}
	
	public String getPassword() {
		return m_password;
	}
	public void setPassword(String password) {
		m_password = password;
	}
	
	public int getMaxPoolSize() {
		return m_maxPoolSize;
	}
	public void setMaxPoolSize(int maxPoolSize) {
		m_maxPoolSize = maxPoolSize;
	}
	
	@Override
	public String toString() {
		return String.format("%s?user=%s&password=%s", m_jdbcUrl, m_user, m_password);
	}
}
