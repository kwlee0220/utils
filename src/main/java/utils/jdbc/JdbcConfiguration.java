package utils.jdbc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.Preconditions;

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
	
	/**
	 * Gets the JDBC driver class name.
	 *
	 * @return the driver class name as a String.
	 */
	public String getDriverClassName() {
		return m_driverClassName;
	}

	/**
	 * Sets the JDBC driver class name.
	 *
	 * @param name the driver class name to set.
	 */
	public void setDriverClassName(String name) {
		Preconditions.checkArgument(name != null, "null driver class name");
		
		m_driverClassName = name;
	}
	
	/**
	 * Gets the JDBC URL.
	 *
	 * @return the JDBC URL.
	 */
	public String getJdbcUrl() {
		return m_jdbcUrl;
	}

	/**
	 * Sets the JDBC URL.
	 *
	 * @param jdbcUrl the JDBC URL.
	 */
	public void setJdbcUrl(String jdbcUrl) {
		m_jdbcUrl = jdbcUrl;
	}
	
	/**
	 * Gets the user name for the JDBC connection.
	 *
	 * @return the user name as a String.
	 */
	public String getUser() {
		return m_user;
	}

	/**
	 * Sets the user name for the JDBC connection.
	 *
	 * @param user the user name to set.
	 */
	public void setUser(String user) {
		m_user = user;
	}

	/**
	 * Gets the password for the JDBC connection.
	 *
	 * @return the password as a String.
	 */
	public String getPassword() {
		return m_password;
	}

	/**
	 * Sets the password for the JDBC connection.
	 *
	 * @param password the password to set.
	 */
	public void setPassword(String password) {
		m_password = password;
	}
	
	/**
	 * Gets the maximum pool size for the JDBC connection.
	 *
	 * @return the maximum pool size as an integer.
	 */
	public int getMaxPoolSize() {
		return m_maxPoolSize;
	}

	/**
	 * Sets the maximum pool size for the JDBC connection.
	 *
	 * @param maxPoolSize the maximum pool size to set.
	 */
	public void setMaxPoolSize(int maxPoolSize) {
		m_maxPoolSize = maxPoolSize;
	}
	
	@Override
	public String toString() {
		return String.format("%s?user=%s&password=%s", m_jdbcUrl, m_user, m_password);
	}
}
