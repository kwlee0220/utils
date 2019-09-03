package utils.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class DriverDelegate implements Driver {
	private final Driver m_driver;
	
	DriverDelegate(Driver driver) {
		m_driver = driver;
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		return m_driver.connect(url, info);
	}

	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return m_driver.acceptsURL(url);
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return m_driver.getPropertyInfo(url, info);
	}

	@Override
	public int getMajorVersion() {
		return m_driver.getMajorVersion();
	}

	@Override
	public int getMinorVersion() {
		return m_driver.getMinorVersion();
	}

	@Override
	public boolean jdbcCompliant() {
		return m_driver.jdbcCompliant();
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return m_driver.getParentLogger();
	}

}
