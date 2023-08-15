package utils.jdbc;

import static utils.Utilities.checkArgument;
import static utils.Utilities.checkNotNullArgument;

import java.io.File;
import java.util.List;

import utils.CSV;
import utils.func.FOption;

import picocli.CommandLine.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcParameters {
	private String m_system;
	private String m_host;
	private int m_port;
	private String m_user;
	private String m_passwd;
	private String m_dbName;
	private FOption<String> m_jdbcJarPath = FOption.empty();
	
	public JdbcParameters() { }
	
	public JdbcParameters(String system, String host, int port, String user, String passwd, String dbName) {
		m_system = system;
		m_host = host;
		m_port = port;
		m_user = user;
		m_passwd = passwd;
		m_dbName = dbName;
	}
	
	public JdbcProcessor createJdbcProcessor() {
		JdbcProcessor jdbc = JdbcProcessor.create(m_system, m_host, m_port, m_user, m_passwd, m_dbName);
		m_jdbcJarPath.map(File::new).ifPresent(jdbc::setJdbcJarFile);
		
		return jdbc;
	}
	
	public String system() {
		return m_system;
	}

	@Option(names={"--jdbc-url"},
			paramLabel="<system>:<jdbc_host>:<jdbc_port>:<user_id>:<passwd>:<db_name>",
			required=true,
			description={"JDBC locator, (eg. 'mysql:localhost:3306:sbdata:xxxyy:bigdata')"})
	public JdbcParameters jdbcLoc(String loc) {
		checkNotNullArgument(loc, "JDBC locator is null");
		
		List<String> parts = CSV.parseCsv(loc, ':').toList();
		checkArgument(parts.size() == 6, "invalid JDBC locator: " + loc);
		
		m_system = parts.get(0);
		m_host = parts.get(1);
		m_port = Integer.parseInt(parts.get(2));
		m_user = parts.get(3);
		m_passwd = parts.get(4);
		m_dbName = parts.get(5);
		
		return this;
	}
	
	public String host() {
		return m_host;
	}
	
	public int port() {
		return m_port;
	}
	
	public String user() {
		return m_user;
	}
	
	public String password() {
		return m_passwd;
	}
	
	public String database() {
		return m_dbName;
	}
	
	public FOption<String> jdbcJarPath() {
		return m_jdbcJarPath;
	}

	@Option(names={"--jdbc_jar"}, paramLabel="jdbc_jar_path", required=false,
			description={"the path to JDBC driver jar"})
	public void jdbcJarPath(String jarPath) {
		m_jdbcJarPath = FOption.ofNullable(jarPath);
	}
	
	public JdbcParameters duplicate() {
		JdbcParameters dupl = new JdbcParameters(m_system, m_host, m_port, m_user, m_passwd, m_dbName);
		dupl.m_jdbcJarPath = m_jdbcJarPath;
		
		return dupl;
	}
	
	@Override
	public String toString() {
		return String.format("%s[%s:%d, user=%s, db=%s]", m_system, m_host, m_port, m_user, m_dbName);
	}
}
