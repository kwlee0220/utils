package utils.jdbc;

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import utils.CSV;
import utils.Throwables;
import utils.func.CheckedConsumerX;
import utils.func.CheckedFunctionX;
import utils.func.FOption;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;


/**
 * {@code JdbcProcessor}는 JDBC 연결을 다루는 인터페이스를 제공한다.
 * <p>
 * JDBC 연결을 위한 URL, 사용자 이름, 패스워드, 드라이버 클래스 이름을 이용하여
 * JDBC 연결을 생성하고, SQL 질의문을 실행시키는 기능을 제공한다.
 * <p>
 * JDBC 연결을 위한 URL은 다음과 같은 형식을 가진다.
 * <pre>
 * jdbc:system:host:port:dbname
 * </pre>
 * 여기서 {@code system}은 JDBC 시스템 이름이며, 현재 지원되는 시스템은 다음과 같다.
 * <ul>
 *     <li>mysql</li>
 *     <li>postgresql</li>
 *     <li>mariadb</li>
 *     <li>kairos</li>
 *     <li>h2_remote</li>
 *     <li>h2_local</li>
 * </ul>
 * <p>
 * 예를 들어, MySQL 데이터베이스에 대한 JDBC 연결을 생성하는 경우는 다음과 같이 사용한다.
 * <pre>
 * JdbcProcessor jdbc = new JdbcProcessor("jdbc:mysql:localhost:3306:mydb", "user", "passwd");
 * </pre>
 * <p>
 * JDBC 연결을 생성한 후, SQL 질의문을 실행시키는 경우는 다음과 같이 사용한다.
 * <pre>
 * ResultSet rs = jdbc.executeQuery("select * from mytable");
 * </pre>
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcProcessor implements Serializable {
	private static final long serialVersionUID = 1L;
	private final Logger s_logger = LoggerFactory.getLogger(JdbcProcessor.class);

	private final String m_jdbcUrl;
	private final String m_user;
	private final String m_passwd;
	private final String m_driverClsName;
	private @Nullable File m_jarFile;
	private @Nullable ClassLoader m_cloader;
	
	public static JdbcProcessor create(JdbcConfiguration config) {
		if ( config.getDriverClassName() != null ) {
			return new JdbcProcessor(config.getJdbcUrl(), config.getUser(), config.getPassword(),
									config.getDriverClassName());
		}
		else {
			return JdbcProcessor.create(config.getJdbcUrl(), config.getUser(), config.getPassword());
		}
	}
	
	public static JdbcProcessor fromFullJdbcUrl(String fullJdbcUrl) {
		Map<String,String> parsed = FullJdbcUrlParser.parse(fullJdbcUrl);
		return create(parsed.get("jdbcUrl"), parsed.get("user"), parsed.get("password"));
	}
	
	/**
	 * Creates a new JdbcProcessor using the specified connection details.
	 *
	 * @param system the JDBC system name (e.g., mysql, postgresql).
	 * @param host   the host name or IP address of the database server.
	 * @param port   the port number on which the database server is listening.
	 * @param user   the user name for the JDBC connection.
	 * @param passwd the password for the JDBC connection.
	 * @param dbName the name of the database to connect to.
	 * @return a new JdbcProcessor instance.
	 */
	public static JdbcProcessor create(String system, String host, int port, String user, String passwd,
			String dbName) {
		JdbcConnectInfo connInfo = getJdbcConnectInfo(system);

		Map<String, String> values = Maps.newHashMap();
		values.put("host", host);
		values.put("port", Integer.toString(port));
		values.put("dbname", dbName);
		String jdbcUrl = new StringSubstitutor(values).replace(connInfo.m_urlFormat);

		return new JdbcProcessor(jdbcUrl, user, passwd, connInfo.m_driverClassName);
	}
	
	/**
	 * Creates a new JdbcProcessor instance using the specified JDBC URL, user, and
	 * password.
	 *
	 * @param jdbcUrl the JDBC URL to connect to.
	 * @param user    the user name for the JDBC connection.
	 * @param passwd  the password for the JDBC connection.
	 * @return a new JdbcProcessor instance configured with the specified connection
	 *         details.
	 * @throws IllegalArgumentException if the provided JDBC URL is invalid.
	 */
	public static JdbcProcessor create(String jdbcUrl, String user, String passwd) {
		FOption<JdbcConnectInfo> oConnInfo = getJdbcConnectInfoByUrl(jdbcUrl);
		if ( oConnInfo.isPresent() ) {
			return new JdbcProcessor(jdbcUrl, user, passwd, oConnInfo.get().m_driverClassName);
		}
		else {
			throw new IllegalArgumentException("invalid JdbcUrl: " + jdbcUrl);
		}
	}
	
	public static String getJdbcDriverClassName(String protocol) {
		return getJdbcConnectInfo(protocol).m_driverClassName;
	}
	
	/**
	 * 주어진 정보를 이용하한 JDBC 연결기 객체를 생성한다.
	 * 
	 * @param jdbcUrl	JDBC URL
	 * @param user		JDBC user
	 * @param passwd	JDBC password
	 * @param driverClsName	JDBC driver class name
	 */
	public JdbcProcessor(String jdbcUrl, String user, String passwd, String driverClsName) {
		Preconditions.checkArgument(jdbcUrl != null, "JDBC URL is null");
		Preconditions.checkArgument(user != null, "JDBC user is null");
		Preconditions.checkArgument(passwd != null, "JDBC password is null");
		Preconditions.checkArgument(driverClsName != null, "JDBC driver class is null");
		
		m_jdbcUrl = jdbcUrl;
		m_user = user;
		m_passwd = passwd;
		m_driverClsName = driverClsName;
	}
	
	public String getJdbcUrl() {
		return m_jdbcUrl;
	}
	
	public String getSystem() {
		return CSV.parseCsv(m_jdbcUrl, ':')
					.take(2).findLast()
					.getOrThrow(() -> new IllegalArgumentException("jdbc_url=" + m_jdbcUrl));
	}
	
	public String getUser() {
		return m_user;
	}
	
	public String getPassword() {
		return m_passwd;
	}
	
	public String getDriverClassName() {
		return m_driverClsName;
	}
	
	public ClassLoader getClassLoader() {
		return m_cloader;
	}
	
	public JdbcProcessor setJdbcJarFile(File jarFile) {
		m_jarFile = jarFile;
		return this;
	}
	
	public JdbcProcessor setClassLoader(ClassLoader cloader) {
		m_cloader = cloader;
		return this;
	}
	
	/**
	 * 주어진 연결 정보를 이용하여 JDBC 연결 객체를 반환한다.
	 * 
	 * @return	JDBC 연결 객체
	 * @throws SQLException	JDBC 연결 도중 오류가 발생된 경우.
	 */
	public Connection connect() throws SQLException {
		if ( m_jarFile != null && m_cloader == null ) {
			try {
				URI uri = new URI(String.format("jar:file:%s!/", m_jarFile.getAbsolutePath()));
				URLClassLoader cloader = new URLClassLoader(new URL[]{uri.toURL()});
				setClassLoader(cloader);
			}
			catch ( MalformedURLException | URISyntaxException e ) {
				throw new SQLException("fails to create " + getClass() + ", invalid jar path=" + m_jarFile);
			}
		}
		
		try {
			if ( m_cloader != null ) {
				Driver driver = (Driver)Class.forName(m_driverClsName, true, m_cloader)
												.getDeclaredConstructor()
												.newInstance();
				DriverManager.registerDriver(new DriverDelegate(driver));
			}
			else {
				Class.forName(m_driverClsName);
			}
			
			return DriverManager.getConnection(m_jdbcUrl, m_user, m_passwd);
		}
		catch ( Exception e ) {
			throw new JdbcException("fails to load JDBC driver class: name=" + m_driverClsName
									+ ", jdbcUrl=" + m_jdbcUrl + ", cause=" + e);
		}
	}
	
	/**
	 * 주어진 이름의 테이블의 존재 여부를 반환한다.
	 * 
	 * TODO: 일단 급하게 작성한 것이어서 코드 정리가 필요함.
	 * 
	 * @param tableName		검색 대상 테이블 이름.
	 * @return	존재하면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean existsTable(String tableName) {
		try ( Connection conn = connect() ) {
			DatabaseMetaData meta = conn.getMetaData();
			ResultSet rs = meta.getTables(null, null, null, new String[]{"TABLE"});
			
			while ( rs.next() ) {
				String name = rs.getString("TABLE_NAME");
				if ( tableName.equalsIgnoreCase(name) ) {
					return true;
				}
			}
			
			return false;
		}
		catch ( SQLException e ) {
			throw new JdbcException(e);
		}
	}
	
	/**
	 * 주어진 이름의 테이블을 삭제시킨다.
	 * 
	 * @param tblName	삭제할 테이블 이름.
	 * @return	삭제가 성공한 경우는 {@code true}, 그렇지 않고, 삭제될 테이블 이름이 존재하지 않아
	 * 			삭제가 실패된 경우
	 */
	public boolean dropTable(String tblName) {
		Objects.requireNonNull(tblName, "table name is null");
		
		try ( Connection conn = connect();
				Statement stmt = conn.createStatement() ) {
			String sql = String.format("drop table %s", tblName);
			s_logger.debug("delete table '{}': {}", tblName, sql);
			
			return stmt.executeUpdate(sql) > 0;
		}
		catch ( SQLException e ) {
			s_logger.info("fails to delete table=" + tblName, e);
			return false;
		}
	}
	
	/**
	 * 주어진 SQL 질의문을 실행시켜 결과 {@link ResultSet} 객체를 반환한다.
	 * <p>
	 * 반환된 ResultSet 객체의 'close()'이 호출되는 경우는
	 * 기반 JDBC 연결을 자동으로 close시킨다.
	 * 
	 * @param sql	SQL 질의문
	 * @return	질의 결과 객체.
	 * @throws SQLException	질의 처리 중 예외가 발생된 경
	 */
	public ResultSet executeQuery(String sql, boolean autoClose) throws SQLException {
		ResultSet rs = connect().createStatement().executeQuery(sql);
		if ( autoClose ) {
			return JdbcUtils.bindToConnection(rs);
		}
		else {
			return rs;
		}
	}
	
	public ResultSet executeQuery(String sql,
									CheckedConsumerX<Statement,SQLException> stmtSetter) throws SQLException {
		Statement stmt = connect().createStatement();
		stmtSetter.accept(stmt);
		return JdbcUtils.bindToConnection(stmt.executeQuery(sql));
	}
	
	public <T> FStream<T> streamQuery(String sql, CheckedFunctionX<ResultSet, T, SQLException> deserializer) {
		return JdbcRowSource.select(deserializer)
							.from(this)
							.executeQuery(sql)
							.fstream();
	}
	
	public <T> FOption<T> getFirstQuery(String sql, CheckedFunctionX<ResultSet, T, SQLException> deserializer) {
		return JdbcRowSource.select(deserializer)
							.from(this)
							.executeQuery(sql)
							.first();
	}
	
	public int executeUpdate(String sql) throws SQLException {
		try ( Connection conn = connect() ) {
			return conn.createStatement().executeUpdate(sql);
		}
	}
	
	public void executeUpdate(String sql, JdbcConsumer<PreparedStatement> update)
		throws SQLException, ExecutionException {
		try ( Connection conn = connect() ) {
			PreparedStatement pstmt = conn.prepareStatement(sql);
			update.accept(pstmt);
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			throw new ExecutionException(Throwables.unwrapThrowable(e));
		}
	}
	
	public long rowCount(String tableName) throws SQLException {
		return streamQuery("select count(*) from " + tableName, rs -> rs.getLong(1))
				.findFirst()
				.get();
	}
	
	public static final class ColumnInfo {
		private final String m_name;
		private final int m_type;
		private final String m_typeName;
		private final boolean m_nullable;
		
		private ColumnInfo(String name, int type, String typeName, boolean nullable) {
			m_name = name;
			m_type = type;
			m_typeName = typeName;
			m_nullable = nullable;
		}
		
		public String name() {
			return m_name;
		}
		
		public int type() {
			return m_type;
		}
		
		public String typeName() {
			return m_typeName;
		}
		
		public boolean nullable() {
			return m_nullable;
		}
	}
	
	public Map<String,ColumnInfo> getColumns(String tblName) throws SQLException {
		Map<String,ColumnInfo> columns = Maps.newLinkedHashMap();

		try ( Connection conn = connect() ) {
			DatabaseMetaData meta = conn.getMetaData();
			ResultSet rs = meta.getColumns(null, null, tblName, null);
			
			while ( rs.next() ) {
				String name = rs.getString("COLUMN_NAME");
				int type = rs.getInt("DATA_TYPE");
				String typeName = rs.getString("TYPE_NAME");
				boolean nullable = rs.getInt("NULLABLE") != 0; 
				
				columns.put(name, new ColumnInfo(name, type, typeName, nullable));
			}
			
			return columns;
		}
		catch ( SQLException e ) {
			throw e;
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s?user=%s,driver=%s", m_jdbcUrl, m_user, m_driverClsName);
	}
	
	public static JdbcProcessor parseString(String str) {
		List<String> parts = CSV.parseCsv(str, ':').toList();
		
		String system = parts.get(0);
		String host = parts.get(1);
		int port = Integer.parseInt(parts.get(2));
		String user = parts.get(3);
		String passwd = parts.get(4);
		String dbName = parts.get(5);
		
		return JdbcProcessor.create(system, host, port, user, passwd, dbName);
	}
	
	@FunctionalInterface
	public static interface JdbcConsumer<T> {
		public void accept(T data) throws SQLException;
	}
	
	private static JdbcConnectInfo getJdbcConnectInfo(String system) {
		JdbcConnectInfo connInfo = JDBC_URLS.get(system);
		if ( connInfo == null ) {
			throw new IllegalArgumentException("unsupported jdbc system: " + system);
		}
		
		return connInfo;
	}
	
	private static FOption<JdbcConnectInfo> getJdbcConnectInfoByUrl(String jdbcUrl) {
		String[] parts = jdbcUrl.split(":");
		String prefix = String.format("%s:%s", parts[0], parts[1]);
		return KeyValueFStream.from(JDBC_URLS)
								.filterValue(info -> info.m_urlFormat.startsWith(prefix))
								.values()
								.findFirst();
	}
	
	private static class JdbcConnectInfo {
		private String m_urlFormat;
		private String m_driverClassName;
		
		JdbcConnectInfo(String urlFormat, String driverClassName) {
			m_urlFormat = urlFormat;
			m_driverClassName = driverClassName;
		}
		
		@Override
		public String toString() {
			return String.format("jdbc_url=%s, driver_class=%s", m_urlFormat, m_driverClassName);
		}
	}
	
	private static final Map<String,JdbcConnectInfo> JDBC_URLS = Maps.newHashMap();
	static {
		JDBC_URLS.put("mysql", new JdbcConnectInfo("jdbc:mysql://${host}:${port}/${dbname}?characterEncoding=utf8&useSSL=false&useCursorFetch=true",
													"com.mysql.jdbc.Driver"));
		JDBC_URLS.put("postgresql", new JdbcConnectInfo("jdbc:postgresql://${host}:${port}/${dbname}",
														"org.postgresql.Driver"));
		JDBC_URLS.put("mariadb", new JdbcConnectInfo("jdbc:mariadb://${host}:${port}/${dbname}",
														"org.mariadb.jdbc.Driver"));
		JDBC_URLS.put("kairos", new JdbcConnectInfo("jdbc:kairos://${host}:${port}/${dbname}",
													"kr.co.realtimetech.kairos.jdbc.kairosDriver"));
		JDBC_URLS.put("h2_remote", new JdbcConnectInfo("jdbc:h2:tcp://${host}:${port}/${dbname}", "org.h2.Driver"));
		JDBC_URLS.put("h2_local", new JdbcConnectInfo("jdbc:h2:${dbname}", "org.h2.Driver"));
	}
}
