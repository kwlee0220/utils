package utils.jdbc;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.text.StringSubstitutor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import utils.CSV;
import utils.Preconditions;
import utils.func.CheckedConsumerX;
import utils.func.CheckedFunctionX;
import utils.func.FOption;
import utils.io.IOUtils;
import utils.jdbc.JdbcConnectionPools.PoolKey;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;


/**
 * {@code JdbcProcessor}는 JDBC 연결과 SQL 질의 실행을 다루는 헬퍼 클래스이다.
 * <p>
 * JDBC URL·사용자·패스워드·드라이버 클래스 이름을 이용하여 {@link Connection}을 생성하고,
 * SQL 질의 실행, 테이블 존재 확인/삭제, 컬럼 메타데이터 조회 등의 기능을 제공한다.
 * <p>
 * 인스턴스 생성은 {@link #builder()} 또는 입력 형식별 빌더 진입점({@link #builder(JdbcConfiguration)},
 * {@link #builderFromLocator(String)})을 통해 이루어진다. 질의 파라미터에 자격 증명을 담은 JDBC URL은
 * {@code builder(JdbcConfiguration.parse(url))}로 만든다.
 * 시스템 단축 이름을 사용하면 드라이버 종류별 표준 URL이 자동으로 생성된다. 현재 지원되는 시스템 단축
 * 이름은 다음과 같다.
 * <ul>
 *     <li>mysql</li>
 *     <li>postgresql</li>
 *     <li>mariadb</li>
 *     <li>kairos</li>
 *     <li>h2_remote</li>
 *     <li>h2_local</li>
 * </ul>
 * <p>
 * MySQL 데이터베이스에 연결하는 예:
 * <pre>
 * JdbcProcessor jdbc = JdbcProcessor.builder()
 *                                  .system("mysql")
 *                                  .host("localhost")
 *                                  .port(3306)
 *                                  .dbName("mydb")
 *                                  .user("user")
 *                                  .password("passwd")
 *                                  .build();
 * try ( ResultSet rs = jdbc.executeQuery("select * from mytable") ) {
 *     // ...
 * }
 * </pre>
 * <p>
 * 빌더에서 {@link Builder#pooled()}를 지정하면 {@link #connect()}는 연결을 새로 만들지 않고
 * {@link JdbcConnectionPools}가 관리하는 연결 풀에서 얻는다. 연결 정보가 같은 pooled processor들은
 * 하나의 풀을 공유하므로, 질의마다 {@code JdbcProcessor}를 새로 만드는 코드도 연결을 재사용한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcProcessor {
	private static final Logger s_logger = LoggerFactory.getLogger(JdbcProcessor.class);

	private final String m_jdbcUrl;
	private final @Nullable String m_user;		// null이면 드라이버에 넘기지 않는다(URL 자격 증명, 인증 없는 DB)
	private final @Nullable String m_passwd;
	private final String m_driverClsName;
	private final @Nullable File m_jarFile;
	/** {@link Builder#classLoader(ClassLoader)}로 지정한 드라이버 적재용 ClassLoader. */
	private final @Nullable ClassLoader m_cloader;
	/** 별도 ClassLoader로 적재한 드라이버. 한 번 만들어 재사용한다({@link #loadDriver()}가 동기화). */
	private @Nullable Driver m_driver;
	/** 연결 풀의 키. 풀을 사용하지 않으면 {@code null}. */
	private final @Nullable PoolKey m_poolKey;
	private final int m_maxPoolSize;
	
	/**
	 * jar 파일별로 공유하는 드라이버 적재용 ClassLoader. 같은 jar를 쓰는 processor를 여러 번 만들어도 ClassLoader는
	 * 하나만 만든다. 드라이버가 계속 클래스를 적재할 수 있도록 닫지 않으며, 개수는 사용하는 jar 파일 수로 제한된다.
	 */
	private static final Map<String,URLClassLoader> s_jarLoaders = new java.util.concurrent.ConcurrentHashMap<>();
	private volatile @Nullable HikariDataSource m_pool;
	
	/**
	 * 주어진 시스템 단축 이름에 해당하는 JDBC 드라이버 클래스명을 반환한다.
	 *
	 * @param system	시스템 단축 이름 (예: {@code "mysql"}, {@code "postgresql"}).
	 * @return			드라이버 클래스명.
	 * @throws IllegalArgumentException	지원하지 않는 시스템 이름인 경우.
	 */
	public static String getJdbcDriverClassName(String system) {
		return getJdbcConnectInfo(system).m_driverClassName;
	}

	/**
	 * 주어진 정보를 이용하여 JDBC 연결기 객체를 생성한다.
	 *
	 * @param jdbcUrl		JDBC URL. {@code null}이면 안 된다.
	 * @param user			JDBC 사용자. {@code null}이면 드라이버에 넘기지 않는다.
	 * @param passwd		JDBC 패스워드. {@code null}이면 드라이버에 넘기지 않는다.
	 * @param driverClsName	JDBC 드라이버 클래스명. {@code null}이면 안 된다.
	 * @param jarFile		드라이버 jar 파일. {@code classLoader}가 있으면 쓰지 않는다.
	 * @param classLoader	드라이버 적재용 ClassLoader.
	 * @param poolKey		연결 풀의 키. 풀을 쓰지 않으면 {@code null}.
	 * @param maxPoolSize	연결 풀의 최대 크기. 풀을 쓰지 않으면 0.
	 * @throws IllegalArgumentException	{@code jdbcUrl}이나 {@code driverClsName}이 {@code null}인 경우.
	 */
	private JdbcProcessor(String jdbcUrl, @Nullable String user, @Nullable String passwd, String driverClsName,
							@Nullable File jarFile, @Nullable ClassLoader classLoader,
							@Nullable PoolKey poolKey, int maxPoolSize) {
		Preconditions.checkNotNullArgument(jdbcUrl, "JDBC URL is null");
		Preconditions.checkNotNullArgument(driverClsName, "JDBC driver class is null");
		
		// 모든 설정 필드를 생성자에서 final로 대입해 다른 스레드에 안전하게 공개되도록 한다.
		m_jdbcUrl = jdbcUrl;
		m_user = user;
		m_passwd = passwd;
		m_driverClsName = driverClsName;
		m_jarFile = jarFile;
		m_cloader = classLoader;
		m_poolKey = poolKey;
		m_maxPoolSize = maxPoolSize;
	}
	
	/**
	 * 이 인스턴스에 설정된 JDBC URL을 반환한다.
	 *
	 * @return	이 인스턴스에 설정된 JDBC URL.
	 */
	public String getJdbcUrl() {
		return m_jdbcUrl;
	}

	/**
	 * JDBC URL의 두 번째 콜론 토큰을 시스템 이름으로 반환한다.
	 * <p>
	 * 예: {@code "jdbc:mysql://localhost/db"} → {@code "mysql"}.
	 *
	 * @return	시스템 단축 이름.
	 * @throws IllegalArgumentException	URL 형식이 콜론 두 토큰 이상으로 분리되지 않는 경우.
	 */
	public String getSystem() {
		return CSV.parseCsv(m_jdbcUrl, ':')
					.drop(1).findFirst()
					.getOrThrow(() -> new IllegalArgumentException("jdbc_url=" + m_jdbcUrl));
	}

	/**
	 * 이 인스턴스에 설정된 JDBC 사용자를 반환한다.
	 *
	 * @return	이 인스턴스에 설정된 JDBC 사용자. 지정하지 않았으면 {@code null}.
	 */
	public @Nullable String getUser() {
		return m_user;
	}

	/**
	 * 이 인스턴스에 설정된 JDBC 패스워드를 반환한다.
	 * <p>
	 * 비밀번호가 밖으로 새지 않도록 package-private이다.
	 *
	 * @return	이 인스턴스에 설정된 JDBC 패스워드. 지정하지 않았으면 {@code null}.
	 */
	@Nullable String getPassword() {
		return m_passwd;
	}

	/**
	 * 이 인스턴스에 설정된 JDBC 드라이버 클래스명을 반환한다.
	 *
	 * @return	이 인스턴스에 설정된 JDBC 드라이버 클래스명.
	 */
	public String getDriverClassName() {
		return m_driverClsName;
	}

	/**
	 * 드라이버 적재에 쓰는 {@link ClassLoader}를 반환한다.
	 * <ul>
	 *   <li>{@link Builder#classLoader(ClassLoader)}로 지정했으면 그 ClassLoader.</li>
	 *   <li>{@link Builder#jarFile(File)}만 지정했으면 그 jar를 적재하는 공유 {@link URLClassLoader}. 아직 만들어지지
	 *       않았으면(그 jar로 연결한 적이 없으면) {@code null}.</li>
	 *   <li>둘 다 지정하지 않았으면 {@code null}. 이때 드라이버는 {@code JdbcProcessor}를 정의한 ClassLoader로
	 *       {@link Class#forName(String)}을 호출해 적재한다.</li>
	 * </ul>
	 *
	 * @return	드라이버 적재용 {@link ClassLoader}. 위의 경우에 따라 {@code null}.
	 */
	public @Nullable ClassLoader getClassLoader() {
		if ( m_cloader != null ) {
			return m_cloader;
		}
		return (m_jarFile != null) ? s_jarLoaders.get(jarLoaderKey(m_jarFile)) : null;
	}

	/**
	 * 연결 풀 사용 여부를 반환한다.
	 *
	 * @return	{@link Builder#pooled()}로 생성된 경우 {@code true}.
	 */
	public boolean isPooled() {
		return m_poolKey != null;
	}

	/**
	 * 이 processor에 설정된 연결 풀의 최대 크기를 반환한다.
	 * <p>
	 * 실제 풀의 크기가 아니라 설정 값이다. 풀은 JVM 안에서 같은 연결 정보끼리 공유하므로({@link JdbcConnectionPools}),
	 * 같은 연결 정보의 풀이 이미 있었다면 실제 풀의 크기는 그 풀을 처음 만든 processor의 값을 따른다.
	 *
	 * @return	설정된 최대 풀 크기. 풀을 사용하지 않으면 0.
	 */
	public int getMaxPoolSize() {
		return m_maxPoolSize;
	}
	
	/**
	 * 설정된 정보를 이용하여 JDBC {@link Connection}을 반환한다.
	 * <p>
	 * {@link Builder#pooled()}로 생성된 경우는 {@link JdbcConnectionPools}의 연결 풀에서 연결을 얻으며,
	 * 반환된 연결을 {@link Connection#close()}하면 연결은 풀로 반환된다. 풀의 연결이 모두 사용 중이면
	 * 최대 {@value JdbcConnectionPools#CONNECTION_TIMEOUT_MILLIS}ms 동안 기다린다.
	 * 그렇지 않은 경우는 호출할 때마다 새 연결을 만든다.
	 * <p>
	 * 드라이버 클래스 적재 순서는 다음과 같다.
	 * <ol>
	 *     <li>{@link Builder#classLoader(ClassLoader)}로 명시 ClassLoader가 지정된 경우 그것을 사용.</li>
	 *     <li>그렇지 않고 {@link Builder#jarFile(File)}로 jar 파일이 지정된 경우, 해당 jar를 적재하는
	 *         {@link URLClassLoader}를 사용한다. 이 ClassLoader는 jar 파일별로 하나만 만들어 모든 processor가
	 *         공유하며 닫지 않는다.</li>
	 *     <li>둘 다 지정되지 않은 경우 {@code JdbcProcessor}를 정의한 ClassLoader로 {@link Class#forName(String)}을
	 *         호출하고 {@link DriverManager}로 연결한다.</li>
	 * </ol>
	 * (1)·(2)의 경우 {@link Driver}를 한 번 인스턴스화하여 재사용하며, {@link DriverManager}에 등록하지 않고
	 * 직접 연결한다.
	 *
	 * @return	JDBC 연결 객체.
	 * @throws SQLException	jar URL 변환·드라이버 클래스 적재·연결이 실패한 경우, 또는 풀에서 제한 시간 안에
	 * 						연결을 얻지 못한 경우(이때 드라이버 적재·연결 실패는 이 예외의 원인으로 보고된다).
	 */
	public Connection connect() throws SQLException {
		if ( m_poolKey != null ) {
			HikariDataSource pool = currentPool();
			try {
				return pool.getConnection();
			}
			catch ( SQLException e ) {
				if ( !pool.isClosed() ) {
					throw e;
				}
				// 풀을 얻은 뒤 getConnection() 전에 다른 스레드가 풀을 닫은 경우(closeAll, close)는 새 풀로 한 번 더
				// 시도한다.
				return currentPool().getConnection();
			}
		}
		else {
			return connectDirect();
		}
	}
	
	private HikariDataSource currentPool() throws SQLException {
		HikariDataSource pool = m_pool;
		if ( pool == null || pool.isClosed() ) {
			// 처음 연결하거나 JdbcConnectionPools.closeAll()·close()로 풀이 닫힌 경우.
			m_pool = pool = JdbcConnectionPools.getOrCreate(this, m_poolKey, m_maxPoolSize);
		}
		return pool;
	}
	
	/**
	 * 이 processor의 연결 풀 키. 풀을 쓰지 않으면 {@code null}.
	 */
	@Nullable PoolKey getPoolKey() {
		return m_poolKey;
	}

	/**
	 * 연결 풀을 거치지 않고 새 JDBC 연결을 만든다.
	 * <p>
	 * 연결 풀도 새 물리 연결을 만들 때 이 메소드를 사용한다.
	 *
	 * @return	새 JDBC 연결 객체.
	 * @throws SQLException	jar URL 변환·드라이버 클래스 적재·연결이 실패한 경우. 메시지에 드라이버 이름과
	 * 						비밀번호를 가린 URL이 들어가며, 드라이버가 보고한 예외가 원인으로 붙는다.
	 */
	Connection connectDirect() throws SQLException {
		Driver driver;
		try {
			driver = loadDriver();
		}
		catch ( ReflectiveOperationException | SQLException e ) {
			throw new SQLException("fails to load JDBC driver class: name=" + m_driverClsName
									+ ", jdbcUrl=" + JdbcUtils.maskPassword(m_jdbcUrl), e);
		}

		try {
			if ( driver != null ) {
				Connection conn = driver.connect(m_jdbcUrl, credentials());
				if ( conn == null ) {
					// Driver.connect()는 처리할 수 없는 URL이면 null을 반환한다.
					throw new SQLException("JDBC driver does not accept the URL: driver=" + m_driverClsName);
				}
				return conn;
			}
			else {
				return DriverManager.getConnection(m_jdbcUrl, credentials());
			}
		}
		catch ( SQLException e ) {
			// SQLState·오류 코드는 그대로 두고 연결 정보(비밀번호 제외)만 메시지에 더한다. 드라이버 메시지에도
			// URL이 그대로 들어갈 수 있어(예: DriverManager의 "No suitable driver found for <url>") 같이 가린다.
			// 원인 예외(e)의 메시지는 바꿀 수 없으므로 그대로 남는다.
			throw new SQLException("fails to connect: jdbcUrl=" + JdbcUtils.maskPassword(m_jdbcUrl)
									+ ", user=" + m_user
									+ ", cause=" + JdbcUtils.maskPassword(e.getMessage()),
									e.getSQLState(), e.getErrorCode(), e);
		}
	}

	// 지정된 사용자·패스워드만 담는다. 없으면 URL에 든 자격 증명이나 드라이버 기본값을 쓴다.
	private Properties credentials() {
		Properties props = new Properties();
		if ( m_user != null ) {
			props.setProperty("user", m_user);
		}
		if ( m_passwd != null ) {
			props.setProperty("password", m_passwd);
		}
		return props;
	}

	/**
	 * 드라이버 클래스를 적재한다.
	 *
	 * @return	별도 ClassLoader로 적재한 드라이버. 시스템 기본 ClassLoader로 적재한 경우는 {@code null}.
	 */
	private synchronized @Nullable Driver loadDriver() throws SQLException, ReflectiveOperationException {
		if ( m_driver != null ) {
			return m_driver;
		}

		ClassLoader loader = m_cloader;
		if ( loader == null && m_jarFile != null ) {
			loader = jarLoader(m_jarFile);
		}

		if ( loader != null ) {
			m_driver = (Driver)Class.forName(m_driverClsName, true, loader)
										.getDeclaredConstructor()
										.newInstance();
			return m_driver;
		}
		else {
			Class.forName(m_driverClsName);
			return null;
		}
	}
	
	private static URLClassLoader jarLoader(File jarFile) throws SQLException {
		String key = jarLoaderKey(jarFile);
		URLClassLoader loader = s_jarLoaders.get(key);
		if ( loader != null ) {
			return loader;
		}
		
		URL url;
		try {
			// 파일 API가 공백·#·% 등을 인코딩한 URL을 만든다. URLClassLoader는 jar 파일 URL을 그대로 받는다.
			url = jarFile.toURI().toURL();
		}
		catch ( MalformedURLException e ) {
			throw new SQLException("invalid JDBC driver jar path: " + jarFile, e);
		}
		return s_jarLoaders.computeIfAbsent(key, k -> new URLClassLoader(new URL[]{url}));
	}
	
	// 같은 파일의 다른 경로 표기(상대 경로, '..', 심볼릭 링크)가 한 로더·한 풀을 공유하도록 정규 경로를 쓴다.
	static String jarLoaderKey(File jarFile) {
		try {
			return jarFile.getCanonicalPath();
		}
		catch ( IOException e ) {
			return jarFile.getAbsolutePath();
		}
	}
	
	/**
	 * 연결의 현재 schema에 주어진 이름의 테이블 또는 뷰가 있는지 여부를 반환한다.
	 * <p>
	 * {@link #tableExists(String, String) tableExists(null, tableName)}과 같다.
	 *
	 * @param tableName	검색 대상 테이블 이름. {@code null}이면 안 된다.
	 * @return			존재하면 {@code true}, 그렇지 않으면 {@code false}.
	 * @throws SQLException	JDBC 연결 또는 메타데이터 조회 중 오류가 발생한 경우.
	 */
	public boolean tableExists(String tableName) throws SQLException {
		return tableExists(null, tableName);
	}

	/**
	 * 주어진 schema에 주어진 이름의 테이블 또는 뷰가 있는지 여부를 반환한다.
	 * <p>
	 * 이름은 {@link #getColumns(String, String)}와 같은 규칙으로 찾는다. 주어진 그대로 먼저 찾고, 없으면
	 * 데이터베이스가 따옴표 없는 식별자를 저장하는 대소문자(예: H2·Oracle은 대문자, PostgreSQL은 소문자)로 바꿔
	 * 다시 찾는다. 명시한 {@code schema} 이름도 같은 규칙으로 찾는다. {@code _}·{@code %}는 패턴 문자가 아니라
	 * 글자 그대로 비교한다.
	 *
	 * @param schema	대상 schema. {@code null}이면 연결의 현재 schema({@link Connection#getSchema()}; 드라이버가
	 * 					알려 주지 않으면 모든 schema).
	 * @param tableName	검색 대상 테이블 이름. {@code null}이면 안 된다.
	 * @return			존재하면 {@code true}, 그렇지 않으면 {@code false}.
	 * @throws SQLException	JDBC 연결 또는 메타데이터 조회 중 오류가 발생한 경우.
	 */
	public boolean tableExists(@Nullable String schema, String tableName) throws SQLException {
		Preconditions.checkNotNullArgument(tableName, "table name is null");

		try ( Connection conn = connect() ) {
			DatabaseMetaData meta = conn.getMetaData();
			for ( String schemaPattern: schemaPatterns(conn, meta, schema) ) {
				for ( String name: identifierCandidates(meta, tableName) ) {
					String[] types = {"TABLE", "VIEW"};
					try ( ResultSet rs = meta.getTables(conn.getCatalog(), schemaPattern,
														escapePattern(meta, name), types) ) {
						if ( rs.next() ) {
							return true;
						}
					}
				}
			}
			return false;
		}
	}
	
	/**
	 * 주어진 이름의 테이블을 삭제한다.
	 * <p>
	 * 내부적으로 {@code DROP TABLE} 문을 실행한다.
	 *
	 * @param tblName	삭제할 테이블 이름. {@code null}이면 안 되며, 따옴표 없는 식별자(유니코드 글자·숫자·밑줄·
	 * 					{@code $}) 또는 큰따옴표 식별자이고, 선택적으로 schema prefix({@code "schema.table"})를 둔다.
	 * @throws IllegalArgumentException	{@code tblName}이 식별자 형식에 맞지 않는 경우.
	 * @throws SQLException	연결이나 삭제가 실패한 경우(테이블이 없는 경우 포함).
	 */
	public void dropTable(String tblName) throws SQLException {
		checkSqlIdentifier(tblName, "table name");

		try ( Connection conn = connect();
				Statement stmt = conn.createStatement() ) {
			String sql = String.format("drop table %s", tblName);
			s_logger.debug("delete table '{}': {}", tblName, sql);

			stmt.executeUpdate(sql);
		}
	}
	
	/**
	 * 주어진 SQL 질의문을 실행시켜 결과 {@link ResultSet} 객체를 반환한다.
	 * <p>
	 * 반환된 ResultSet을 {@link ResultSet#close()}하면 그 Statement와 JDBC 연결도 함께 close된다
	 * ({@link JdbcUtils#bindToConnection(ResultSet, Statement, Connection)}). try-with-resources로 닫으면 된다.
	 * Statement·연결을 직접 관리해야 하면 {@link #connect()}로 연결을 얻어 쓴다.
	 * <p>
	 * 연결 후 결과를 반환하기 전에 어떤 예외(런타임 예외 포함)로든 실패하면 Statement와 연결을 닫고 그 예외를 그대로
	 * 던진다.
	 *
	 * @param sql		실행할 SQL 질의문.
	 * @return			질의 결과 {@link ResultSet}. 닫으면 Statement·연결도 닫힌다.
	 * @throws SQLException	JDBC 연결 또는 질의 실행 중 오류가 발생한 경우.
	 */
	public ResultSet executeQuery(String sql) throws SQLException {
		Connection conn = connect();
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			return JdbcUtils.bindToConnection(stmt.executeQuery(sql), stmt, conn);
		}
		catch ( Throwable e ) {
			// 연결 이후의 실패는 종류와 상관없이 Statement와 연결을 닫는다(풀 고갈 방지).
			IOUtils.closeQuietly(stmt, conn);
			throw e;
		}
	}

	/**
	 * 호출자가 {@link Statement}를 사전 설정한 뒤 SQL 질의를 실행한다.
	 * <p>
	 * {@code stmtSetter}는 생성된 {@link Statement}에 fetch size·timeout 등을 설정할 기회를
	 * 제공한다. 반환된 ResultSet은 {@link JdbcUtils#bindToConnection(ResultSet, Statement, Connection)}으로 감싸여
	 * close 시 Connection도 함께 close된다.
	 * <p>
	 * 결과를 반환하기 전에 어떤 예외(런타임 예외 포함)로든 실패하면 Statement와 연결을 닫고 그 예외를 그대로 던진다.
	 *
	 * @param sql			실행할 SQL 질의문.
	 * @param stmtSetter	Statement에 추가 설정을 적용하는 함수.
	 * @return				질의 결과 {@link ResultSet}.
	 * @throws SQLException	JDBC 연결 또는 질의 실행 중 오류가 발생한 경우.
	 */
	public ResultSet executeQuery(String sql,
								CheckedConsumerX<Statement,SQLException> stmtSetter) throws SQLException {
		Connection conn = connect();
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			stmtSetter.accept(stmt);
			return JdbcUtils.bindToConnection(stmt.executeQuery(sql), stmt, conn);
		}
		catch ( Throwable e ) {
			// stmtSetter나 결과 감싸기(bindToConnection)의 런타임 예외에서도 Statement와 연결을 닫는다.
			IOUtils.closeQuietly(stmt, conn);
			throw e;
		}
	}
	
	/**
	 * SQL 질의 결과를 {@code deserializer}로 변환하여 {@link FStream}으로 반환한다.
	 * <p>
	 * 호출하는 시점에 연결하고 질의를 실행한다. 반환된 스트림을 닫으면 ResultSet, Statement, Connection이 모두
	 * 닫힌다. {@code toList()}·{@code findFirst()} 같은 종단 연산은 스트림을 닫으므로 따로 닫을 필요가 없지만,
	 * {@code next()}로 직접 읽거나 중간에 멈출 때는 호출자가 닫아야 한다(try-with-resources 권장). 질의를 열다가
	 * 실패하면 이미 연 자원을 닫는다.
	 *
	 * @param <T>			변환된 행 객체의 타입.
	 * @param sql			실행할 SQL 질의문.
	 * @param deserializer	각 ResultSet 행을 {@code T} 객체로 변환하는 함수.
	 * @return				변환된 객체들의 {@link FStream}.
	 * <p>
	 * 질의를 여는 단계의 실패는 {@link SQLException}으로 던진다. 스트림을 읽는 도중의 실패(다음 행 읽기,
	 * {@code deserializer})는 checked 예외를 던질 수 없으므로 {@link JdbcException}으로 감싸 던진다.
	 *
	 * @throws IllegalArgumentException	{@code sql}이나 {@code deserializer}가 {@code null}인 경우.
	 * @throws SQLException	연결이나 질의 실행이 실패한 경우. 첫 행도 스트림을 읽을 때 읽으므로, 그 실패는
	 * 						{@link JdbcException}이다.
	 */
	public <T> FStream<T> query(String sql, CheckedFunctionX<ResultSet, T, SQLException> deserializer)
		throws SQLException {
		Preconditions.checkNotNullArgument(sql, "sql is null");
		Preconditions.checkNotNullArgument(deserializer, "deserializer is null");

		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;
		try {
			conn = connect();
			stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			return toOwningStream(rs, stmt, conn, deserializer);
		}
		catch ( Throwable e ) {
			// SQLException이든 런타임 예외든 연 자원을 닫고 그대로 던진다.
			IOUtils.closeQuietly(rs, stmt, conn);
			throw e;
		}
	}

	/**
	 * 파라미터가 있는 SQL 질의를 {@link PreparedStatement}로 실행하고, 결과를 {@code deserializer}로 변환하여
	 * {@link FStream}으로 반환한다.
	 * <p>
	 * {@code binder}로 파라미터를 설정한 뒤 이 메소드가 {@link PreparedStatement#executeQuery()}를 호출한다.
	 * 그 밖의 동작(자원 소유, 지연 읽기, 예외)은 {@link #query(String, CheckedFunctionX)}와 같다.
	 * {@code binder}가 던진 예외는 감싸지 않고 그대로 전파되며, 이때 Statement와 연결은 닫힌다.
	 * <pre>{@code
	 * try ( FStream<String> names = jdbc.query("select name from item where id > ?",
	 *                                          pstmt -> pstmt.setInt(1, 10),
	 *                                          rs -> rs.getString(1)) ) {
	 *     ...
	 * }
	 * }</pre>
	 *
	 * @param <T>			변환된 행 객체의 타입.
	 * @param sql			실행할 SQL 질의문.
	 * @param binder		{@link PreparedStatement}에 파라미터를 설정하는 함수.
	 * @param deserializer	각 ResultSet 행을 {@code T} 객체로 변환하는 함수.
	 * @return				변환된 객체들의 {@link FStream}.
	 * @throws IllegalArgumentException	{@code sql}, {@code binder}, {@code deserializer} 중 하나가
	 * 									{@code null}인 경우.
	 * @throws SQLException	연결, 파라미터 설정 또는 질의 실행이 실패한 경우.
	 */
	public <T> FStream<T> query(String sql, CheckedConsumerX<PreparedStatement,SQLException> binder,
								CheckedFunctionX<ResultSet, T, SQLException> deserializer)
		throws SQLException {
		Preconditions.checkNotNullArgument(sql, "sql is null");
		Preconditions.checkNotNullArgument(binder, "binder is null");
		Preconditions.checkNotNullArgument(deserializer, "deserializer is null");

		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			conn = connect();
			pstmt = conn.prepareStatement(sql);
			binder.accept(pstmt);
			rs = pstmt.executeQuery();
			return toOwningStream(rs, pstmt, conn, deserializer);
		}
		catch ( Throwable e ) {
			// SQLException이든 binder의 런타임 예외든 연 자원을 닫고 그대로 던진다.
			IOUtils.closeQuietly(rs, pstmt, conn);
			throw e;
		}
	}

	// 닫으면 ResultSet, Statement, Connection을 모두 닫는 스트림.
	private static <T> FStream<T> toOwningStream(ResultSet rs, Statement stmt, Connection conn,
												CheckedFunctionX<ResultSet, T, SQLException> deserializer) {
		AutoCloseable[] resources = { rs, stmt, conn };
		return new ResultSetObjectFStream<>(rs, deserializer)
						.onClose(() -> IOUtils.closeQuietly(resources));
	}

	/**
	 * SQL 질의 결과의 첫 행만 변환하여 반환한다.
	 * <p>
	 * 첫 행을 읽은 뒤 ResultSet, Statement, Connection을 모두 닫는다({@link #query(String, CheckedFunctionX)}).
	 *
	 * @param <T>			변환된 행 객체의 타입.
	 * @param sql			실행할 SQL 질의문.
	 * @param deserializer	ResultSet의 한 행을 {@code T} 객체로 변환하는 함수.
	 * @return				첫 행 변환 결과를 담은 {@link FOption}. 결과가 없으면 빈 값.
	 * @throws SQLException	연결, 질의 실행, 첫 행 읽기 또는 그 변환({@code deserializer})이 실패한 경우.
	 * 						스트림 순회 중의 {@link JdbcException}은 원인 {@link SQLException}으로 풀어서 던진다.
	 */
	public <T> FOption<T> queryFirst(String sql,
										CheckedFunctionX<ResultSet, T, SQLException> deserializer)
		throws SQLException {
		return findFirst(query(sql, deserializer));
	}

	/**
	 * 파라미터가 있는 SQL 질의를 {@link PreparedStatement}로 실행하고, 결과의 첫 행만 변환하여 반환한다.
	 * <p>
	 * 첫 행을 읽은 뒤 ResultSet, Statement, Connection을 모두 닫는다
	 * ({@link #query(String, CheckedConsumerX, CheckedFunctionX)}).
	 *
	 * @param <T>			변환된 행 객체의 타입.
	 * @param sql			실행할 SQL 질의문.
	 * @param binder		{@link PreparedStatement}에 파라미터를 설정하는 함수.
	 * @param deserializer	ResultSet의 한 행을 {@code T} 객체로 변환하는 함수.
	 * @return				첫 행 변환 결과를 담은 {@link FOption}. 결과가 없으면 빈 값.
	 * @throws IllegalArgumentException	{@code sql}, {@code binder}, {@code deserializer} 중 하나가
	 * 									{@code null}인 경우.
	 * @throws SQLException	연결, 파라미터 설정, 질의 실행, 첫 행 읽기 또는 그 변환({@code deserializer})이 실패한
	 * 						경우. 스트림 순회 중의 {@link JdbcException}은 원인 {@link SQLException}으로 풀어서 던진다.
	 */
	public <T> FOption<T> queryFirst(String sql, CheckedConsumerX<PreparedStatement,SQLException> binder,
										CheckedFunctionX<ResultSet, T, SQLException> deserializer)
		throws SQLException {
		return findFirst(query(sql, binder, deserializer));
	}

	// 첫 원소를 읽고 스트림을 닫는다. 순회 중의 JdbcException은 원인 SQLException으로 푼다.
	private static <T> FOption<T> findFirst(FStream<T> strm) throws SQLException {
		try {
			return strm.findFirst();
		}
		catch ( JdbcException e ) {
			if ( e.getCause() instanceof SQLException cause ) {
				throw cause;
			}
			throw e;
		}
	}

	/**
	 * SQL update/DDL 문을 실행하고 영향받은 행 수를 반환한다.
	 * <p>
	 * 실행에 쓴 Statement와 연결은 성공·실패와 상관없이 닫는다.
	 *
	 * @param sql	실행할 SQL 문.
	 * @return		영향받은 행 수 (DDL의 경우 통상 0).
	 * @throws SQLException	JDBC 연결 또는 실행 중 오류가 발생한 경우.
	 */
	public int executeUpdate(String sql) throws SQLException {
		try ( Connection conn = connect();
				Statement stmt = conn.createStatement() ) {
			return stmt.executeUpdate(sql);
		}
	}

	/**
	 * 파라미터가 있는 SQL update 문을 {@link PreparedStatement}로 실행하고 영향받은 행 수를 반환한다.
	 * <p>
	 * {@code binder}로 파라미터를 설정한 뒤 이 메소드가 {@link PreparedStatement#executeUpdate()}를 한 번 호출한다.
	 * {@code binder}가 던진 예외는 감싸지 않고 그대로 전파되며, 이때 Statement와 연결은 닫힌다.
	 * <pre>{@code
	 * int count = jdbc.executeUpdate("update item set name = ? where id = ?", pstmt -> {
	 *     pstmt.setString(1, "x");
	 *     pstmt.setInt(2, 1);
	 * });
	 * }</pre>
	 *
	 * @param sql		실행할 SQL 문.
	 * @param binder	{@link PreparedStatement}에 파라미터를 설정하는 함수.
	 * @return		영향받은 행 수.
	 * @throws SQLException	JDBC 연결, 파라미터 설정 또는 실행 중 오류가 발생한 경우.
	 */
	public int executeUpdate(String sql, CheckedConsumerX<PreparedStatement,SQLException> binder)
		throws SQLException {
		Preconditions.checkNotNullArgument(binder, "binder is null");
		
		try ( Connection conn = connect();
				PreparedStatement pstmt = conn.prepareStatement(sql) ) {
			binder.accept(pstmt);
			return pstmt.executeUpdate();
		}
	}
	
	/**
	 * 주어진 테이블의 행 개수를 반환한다.
	 *
	 * @param tableName	대상 테이블 이름. 따옴표 없는 식별자(유니코드 글자·숫자·밑줄·{@code $}) 또는 큰따옴표
	 * 					식별자이며, 선택적으로 schema prefix({@code "schema.table"})를 둔다.
	 * @return			테이블의 행 개수.
	 * @throws IllegalArgumentException	{@code tableName}이 식별자 형식에 맞지 않는 경우.
	 * @throws SQLException	JDBC 연결 또는 질의 실행 중 오류가 발생한 경우.
	 */
	public long countRows(String tableName) throws SQLException {
		checkSqlIdentifier(tableName, "table name");

		try ( Connection conn = connect();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("select count(*) from " + tableName) ) {
			if ( !rs.next() ) {
				throw new SQLException("count query returned no row: table=" + tableName);
			}
			return rs.getLong(1);
		}
	}

	/**
	 * 주어진 질의문의 결과 행 개수를 반환한다.
	 * <p>
	 * {@code SELECT COUNT(*) FROM (<query>) subquery} 형태의 질의를 실행하며, 이때 사용한
	 * ResultSet과 연결은 메소드 종료 시 close된다. 별칭 앞에 {@code AS}를 쓰지 않으므로 Oracle에서도 동작한다.
	 * {@code query}는 검증 없이 그대로 SQL에 삽입되므로 신뢰할 수 있는 질의문만 전달해야 한다.
	 * <p>
	 * 질의 끝의 공백과 {@code ;}는 지운다. {@code query} 뒤에서 줄을 바꾸므로 끝에 행 주석({@code -- ...})이
	 * 있어도 된다. 다만 SQL을 해석하지는 않으므로 {@code select 1; -- comment}처럼 {@code ;} 뒤에 주석이 오는
	 * 질의는 지원하지 않는다.
	 *
	 * @param query	행 개수를 셀 SQL 질의문.
	 * @return	질의 결과의 행 개수.
	 * @throws SQLException	JDBC 연결 또는 질의 실행이 실패했거나, count 질의가 결과 행을 반환하지 않은 경우.
	 */
	public long countQueryRows(String query) throws SQLException {
		Preconditions.checkNotNullArgument(query, "query is null");
		
		String body = query.strip();
		while ( body.endsWith(";") ) {
			body = body.substring(0, body.length() - 1).strip();
		}
		// 질의 끝의 행 주석(-- ...)이 닫는 괄호까지 주석으로 만들지 않도록 줄을 바꾼다.
		String countQuery = String.format("SELECT COUNT(*) FROM (%s\n) subquery", body);
		try ( ResultSet rs = executeQuery(countQuery) ) {
			if ( !rs.next() ) {
				throw new SQLException("count query returned no row: query=" + query);
			}
			return rs.getLong(1);
		}
	}

	/**
	 * 데이터베이스 컬럼의 메타데이터를 표현하는 값 객체.
	 */
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
		
		/**
		 * 컬럼 이름을 반환한다.
		 *
		 * @return	컬럼 이름.
		 */
		public String name() {
			return m_name;
		}

		/**
		 * 컬럼의 SQL 데이터 타입 코드를 반환한다.
		 *
		 * @return	{@link java.sql.Types}에 정의된 SQL 데이터 타입 코드.
		 */
		public int type() {
			return m_type;
		}

		/**
		 * 컬럼의 데이터베이스 종속 SQL 타입 이름을 반환한다.
		 *
		 * @return	데이터베이스 종속의 SQL 타입 이름.
		 */
		public String typeName() {
			return m_typeName;
		}

		/**
		 * 컬럼이 null 값을 허용하는지 여부를 반환한다.
		 *
		 * @return	null 값을 허용하는지 여부. JDBC {@link java.sql.DatabaseMetaData#getColumns}의
		 * 			{@code NULLABLE} 컬럼이 0이 아닌 모든 경우({@code columnNullable},
		 * 			{@code columnNullableUnknown})에 대해 {@code true}를 반환한다.
		 */
		public boolean nullable() {
			return m_nullable;
		}
	}
	
	/**
	 * 연결의 현재 schema에 있는 주어진 테이블의 컬럼 메타데이터를 반환한다.
	 * <p>
	 * {@link #getColumns(String, String) getColumns(null, tblName)}과 같다.
	 *
	 * @param tblName	대상 테이블 이름.
	 * @return			컬럼명 → {@link ColumnInfo} 맵(선언 순서). 테이블이 없으면 빈 맵.
	 * @throws SQLException	JDBC 연결 또는 메타데이터 조회 중 오류가 발생한 경우.
	 */
	public Map<String,ColumnInfo> getColumns(String tblName) throws SQLException {
		return getColumns(null, tblName);
	}

	/**
	 * 주어진 schema에 있는 주어진 테이블(또는 뷰)의 컬럼 메타데이터를 컬럼 이름을 키로 하는 맵으로 반환한다.
	 * <p>
	 * 반환되는 맵은 컬럼이 데이터베이스에 선언된 순서를 보존한다(실제 객체는 {@link LinkedHashMap}이라 수정할 수
	 * 있다).
	 * 이름은 {@link #tableExists(String, String)}와 같은 규칙으로 찾는다. 주어진 그대로 먼저 찾고, 없으면
	 * 데이터베이스가 따옴표 없는 식별자를 저장하는 대소문자로 바꿔 다시 찾는다. 명시한 {@code schema} 이름도 같은
	 * 규칙으로 찾는다. {@code _}·{@code %}는 글자 그대로 비교한다.
	 *
	 * @param schema	대상 schema. {@code null}이면 연결의 현재 schema({@link Connection#getSchema()}; 드라이버가
	 * 					알려 주지 않으면 모든 schema).
	 * @param tblName	대상 테이블 이름.
	 * @return			컬럼명 → {@link ColumnInfo} 맵. 테이블이 없으면 빈 맵.
	 * @throws SQLException	JDBC 연결 또는 메타데이터 조회 중 오류가 발생한 경우.
	 */
	public Map<String,ColumnInfo> getColumns(@Nullable String schema, String tblName)
		throws SQLException {
		Preconditions.checkNotNullArgument(tblName, "table name is null");

		try ( Connection conn = connect() ) {
			DatabaseMetaData meta = conn.getMetaData();
			for ( String schemaPattern: schemaPatterns(conn, meta, schema) ) {
				for ( String name: identifierCandidates(meta, tblName) ) {
					LinkedHashMap<String,ColumnInfo> columns = new LinkedHashMap<>();
					try ( ResultSet rs = meta.getColumns(conn.getCatalog(), schemaPattern,
															escapePattern(meta, name), null) ) {
						while ( rs.next() ) {
							String colName = rs.getString("COLUMN_NAME");
							int type = rs.getInt("DATA_TYPE");
							String typeName = rs.getString("TYPE_NAME");
							boolean nullable = rs.getInt("NULLABLE") != 0;
							columns.put(colName, new ColumnInfo(colName, type, typeName, nullable));
						}
					}
					if ( !columns.isEmpty() ) {
						return columns;
					}
				}
			}
			return new LinkedHashMap<>();
		}
	}
	
	// 주어진 이름, 그리고 다르면 데이터베이스가 따옴표 없는 식별자를 저장하는 대소문자로 바꾼 이름.
	private static List<String> identifierCandidates(DatabaseMetaData meta, String name) throws SQLException {
		String stored = meta.storesUpperCaseIdentifiers() ? name.toUpperCase(java.util.Locale.ROOT)
						: meta.storesLowerCaseIdentifiers() ? name.toLowerCase(java.util.Locale.ROOT)
						: name;
		return stored.equals(name) ? List.of(name) : List.of(name, stored);
	}
	
	// 조회할 schema 패턴 후보. schema가 주어지면 identifierCandidates()와 같은 순서(주어진 그대로, 저장 대소문자)이고,
	// 없으면 연결의 현재 schema 하나다(드라이버가 알려 주지 않으면 null 하나 = 모든 schema).
	private static List<String> schemaPatterns(Connection conn, DatabaseMetaData meta,
														@Nullable String schema) throws SQLException {
		if ( schema != null ) {
			List<String> patterns = new ArrayList<>();
			for ( String name: identifierCandidates(meta, schema) ) {
				patterns.add(escapePattern(meta, name));
			}
			return patterns;
		}
		String current;
		try {
			current = conn.getSchema();
		}
		catch ( SQLException | AbstractMethodError e ) {
			current = null;		// 드라이버가 지원하지 않으면 모든 schema
		}
		return Collections.singletonList((current != null) ? escapePattern(meta, current) : null);
	}
	
	// LIKE 패턴 문자(_, %)를 글자 그대로 비교하도록 escape한다.
	private static String escapePattern(DatabaseMetaData meta, String name) throws SQLException {
		String esc = meta.getSearchStringEscape();
		if ( esc == null || esc.isEmpty() ) {
			return name;
		}
		return name.replace(esc, esc + esc).replace("_", esc + "_").replace("%", esc + "%");
	}

	/**
	 * 연결 정보를 사람이 읽을 수 있는 문자열로 반환한다. 비밀번호는 출력하지 않는다.
	 * <p>
	 * 형식: {@code JdbcProcessor[url=<URL>, user=<user>, driver=<class>, maxPoolSize=<n>]}. {@code maxPoolSize}는
	 * 풀을 쓸 때만 나온다. 이 문자열은 다시 파싱하는 용도가 아니다.
	 */
	@Override
	public String toString() {
		String poolStr = (m_poolKey != null) ? ", maxPoolSize=" + m_maxPoolSize : "";
		return String.format("JdbcProcessor[url=%s, user=%s, driver=%s%s]", JdbcUtils.maskPassword(m_jdbcUrl),
							m_user, m_driverClsName, poolStr);
	}

	/**
	 * 새 {@link Builder} 인스턴스를 반환한다.
	 *
	 * @return	빈 상태의 {@link Builder}.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * 주어진 {@link JdbcConfiguration}으로부터 {@link Builder}를 미리 채워 반환한다.
	 * <p>
	 * URL·사용자·패스워드가 빌더에 적용된다. {@code config}에 드라이버 클래스명이 명시되어 있으면
	 * {@link Builder#driverClassName(String)}로도 적용된다. 호출자가 반환된 빌더에서
	 * {@code driverClassName}을 다시 호출하면 그 값이 우선한다 (마지막 setter 호출이 우선).
	 * {@code config}의 {@link JdbcConfiguration#getMaxPoolSize() maxPoolSize}가 0보다 크면, 반환된 빌더에서
	 * {@link Builder#pooled()}를 호출했을 때의 최대 풀 크기로 쓰인다. 이 값만으로 풀이 사용되지는 않는다.
	 *
	 * <p>
	 * 사용자·패스워드는 선택 항목이다. 없으면 드라이버에 넘기지 않으므로, JDBC URL에 든 자격 증명이나 인증이 없는
	 * DB(H2, SQLite 등)를 그대로 쓸 수 있다.
	 *
	 * @param config	JDBC 연결 설정 객체.
	 * @return			주어진 설정이 적용된 {@link Builder}.
	 * @throws IllegalArgumentException	{@code config}나 그 {@code jdbcUrl}이 {@code null}인 경우.
	 */
	public static Builder builder(JdbcConfiguration config) {
		Preconditions.checkNotNullArgument(config, "config is null");
		Preconditions.checkNotNullArgument(config.getJdbcUrl(), "config.jdbcUrl is null");

		Builder builder = new Builder().jdbcUrl(config.getJdbcUrl());
		if ( config.getUser() != null ) {
			builder.user(config.getUser());
		}
		if ( config.getPassword() != null ) {
			builder.password(config.getPassword());
		}
		if ( config.getDriverClassName() != null ) {
			builder.driverClassName(config.getDriverClassName());
		}
		builder.m_configMaxPoolSize = config.getMaxPoolSize();
		return builder;
	}

	/**
	 * 콜론({@code :})으로 구분된 locator 문자열로부터 {@link Builder}를 미리 채워 반환한다.
	 * <p>
	 * 입력 형식: {@code system:host:port:user:passwd:dbname} (정확히 6개 토큰).
	 *
	 * @param str	콜론 구분 연결 정보 문자열.
	 * @return		주어진 정보가 적용된 {@link Builder}.
	 * @throws IllegalArgumentException	토큰 수가 6이 아니거나 port가 정수가 아닌 경우.
	 */
	public static Builder builderFromLocator(String str) {
		Preconditions.checkNotNullArgument(str, "input string is null");

		List<String> parts = CSV.parseCsv(str, ':').toList();
		Preconditions.checkArgument(parts.size() == 6,
								"expected 6 colon-separated tokens "
								+ "(system:host:port:user:passwd:dbname), got %s",
								parts.size());

		int port;
		try {
			port = Integer.parseInt(parts.get(2));
		}
		catch ( NumberFormatException e ) {
			throw new IllegalArgumentException("invalid port: " + parts.get(2), e);
		}

		return new Builder()
					.system(parts.get(0))
					.host(parts.get(1))
					.port(port)
					.user(parts.get(3))
					.password(parts.get(4))
					.dbName(parts.get(5));
	}

	/**
	 * {@link JdbcProcessor}를 단계적으로 구성하는 빌더 클래스.
	 * <p>
	 * 다음 두 가지 방식 중 하나로 JDBC URL을 지정할 수 있다.
	 * <ul>
	 *     <li>{@link #jdbcUrl(String)}로 완성된 JDBC URL을 직접 지정.</li>
	 *     <li>{@link #system(String)} + {@link #host(String)} + {@link #port(int)}
	 *         + {@link #dbName(String)} 조합으로 시스템 단축 이름과 구성요소를 지정 (URL은 자동 생성).</li>
	 * </ul>
	 * 드라이버 클래스명은 {@link #driverClassName(String)}로 명시하지 않으면 시스템 또는 URL prefix로부터
	 * 자동 추정된다. {@code user}와 {@code password}는 반드시 지정해야 한다.
	 * <p>
	 * 예:
	 * <pre>
	 * JdbcProcessor jdbc = JdbcProcessor.builder()
	 *                                  .system("mysql")
	 *                                  .host("localhost")
	 *                                  .port(3306)
	 *                                  .dbName("mydb")
	 *                                  .user("root")
	 *                                  .password("secret")
	 *                                  .build();
	 * </pre>
	 */
	public static final class Builder {
		private @Nullable String m_jdbcUrl;
		private @Nullable String m_user;
		private @Nullable String m_passwd;
		private @Nullable String m_driverClsName;
		private @Nullable String m_system;
		private @Nullable String m_host;
		private @Nullable Integer m_port;
		private @Nullable String m_dbName;
		private @Nullable File m_jarFile;
		private @Nullable ClassLoader m_classLoader;
		private boolean m_pooled = false;
		private int m_maxPoolSize = 0;
		private int m_configMaxPoolSize = 0;	// 연결 설정(JdbcConfiguration 등)에 지정된 값

		private Builder() { }

		/**
		 * JDBC URL을 직접 지정한다.
		 * <p>
		 * 이 메서드를 사용한 경우 {@link #system}/{@link #host}/{@link #port}/{@link #dbName}은
		 * 함께 지정될 수 없다.
		 *
		 * @param jdbcUrl	완성된 JDBC URL. {@code null}이면 안 된다.
		 * @return			자기 자신.
		 * @throws IllegalArgumentException	{@code jdbcUrl}이 {@code null}인 경우.
		 */
		public Builder jdbcUrl(String jdbcUrl) {
			Preconditions.checkNotNullArgument(jdbcUrl, "jdbcUrl is null");
			m_jdbcUrl = jdbcUrl;
			return this;
		}

		/**
		 * JDBC 사용자를 지정한다.
		 *
		 * @param user	JDBC 사용자. {@code null}이면 안 된다.
		 * @return		자기 자신.
		 * @throws IllegalArgumentException	{@code user}가 {@code null}인 경우.
		 */
		public Builder user(String user) {
			Preconditions.checkNotNullArgument(user, "user is null");
			m_user = user;
			return this;
		}

		/**
		 * JDBC 패스워드를 지정한다.
		 *
		 * @param passwd	JDBC 패스워드. {@code null}이면 안 된다.
		 * @return			자기 자신.
		 * @throws IllegalArgumentException	{@code passwd}가 {@code null}인 경우.
		 */
		public Builder password(String passwd) {
			Preconditions.checkNotNullArgument(passwd, "password is null");
			m_passwd = passwd;
			return this;
		}

		/**
		 * JDBC 드라이버 클래스명을 명시한다. 생략 시 system 또는 URL prefix로부터 자동 추정된다.
		 *
		 * @param driverClsName	드라이버 클래스명. {@code null}이면 안 된다.
		 * @return				자기 자신.
		 * @throws IllegalArgumentException	{@code driverClsName}이 {@code null}인 경우.
		 */
		public Builder driverClassName(String driverClsName) {
			Preconditions.checkNotNullArgument(driverClsName, "driverClsName is null");
			m_driverClsName = driverClsName;
			return this;
		}

		/**
		 * 시스템 단축 이름을 지정한다 (예: {@code "mysql"}, {@code "postgresql"}).
		 * <p>
		 * {@link #jdbcUrl}을 직접 지정한 경우와 함께 쓸 수 없다.
		 *
		 * @param system	시스템 단축 이름. {@code null}이면 안 된다.
		 * @return			자기 자신.
		 * @throws IllegalArgumentException	{@code system}이 {@code null}인 경우.
		 */
		public Builder system(String system) {
			Preconditions.checkNotNullArgument(system, "system is null");
			m_system = system;
			return this;
		}

		/**
		 * 데이터베이스 호스트명 또는 IP 주소를 지정한다.
		 *
		 * @param host	호스트. {@code null}이면 안 된다.
		 * @return		자기 자신.
		 * @throws IllegalArgumentException	{@code host}가 {@code null}인 경우.
		 */
		public Builder host(String host) {
			Preconditions.checkNotNullArgument(host, "host is null");
			m_host = host;
			return this;
		}

		/**
		 * 데이터베이스 포트 번호를 지정한다.
		 *
		 * @param port	포트 번호.
		 * @return		자기 자신.
		 */
		public Builder port(int port) {
			m_port = port;
			return this;
		}

		/**
		 * 데이터베이스 이름을 지정한다.
		 *
		 * @param dbName	데이터베이스 이름. {@code null}이면 안 된다.
		 * @return			자기 자신.
		 * @throws IllegalArgumentException	{@code dbName}이 {@code null}인 경우.
		 */
		public Builder dbName(String dbName) {
			Preconditions.checkNotNullArgument(dbName, "dbName is null");
			m_dbName = dbName;
			return this;
		}

		/**
		 * JDBC 드라이버 jar 파일을 지정한다 (선택).
		 * <p>
		 * 별도의 ClassLoader가 설정되지 않은 상태에서 {@link JdbcProcessor#connect()}가 호출되면,
		 * 이 jar 파일을 적재하는 {@link URLClassLoader}가 자동으로 생성되어 사용된다.
		 * {@link #classLoader(ClassLoader)}로 ClassLoader가 명시 설정된 경우는 jar 파일은 무시된다.
		 *
		 * @param jarFile	드라이버 jar 파일.
		 * @return			자기 자신.
		 */
		public Builder jarFile(File jarFile) {
			m_jarFile = jarFile;
			return this;
		}

		/**
		 * 드라이버 적재용 {@link ClassLoader}를 지정한다 (선택).
		 * <p>
		 * 명시 설정 시 {@link #jarFile(File)}의 jar 파일은 사용되지 않는다.
		 *
		 * @param cloader	ClassLoader.
		 * @return			자기 자신.
		 */
		public Builder classLoader(ClassLoader cloader) {
			m_classLoader = cloader;
			return this;
		}

		/**
		 * 연결 풀을 사용하도록 지정한다.
		 * <p>
		 * 생성된 processor의 {@link JdbcProcessor#connect()}는 {@link JdbcConnectionPools}의 연결 풀에서
		 * 연결을 얻는다. 최대 풀 크기는 {@link JdbcProcessor#builder(JdbcConfiguration)}로 전달된 연결 설정의
		 * {@code maxPoolSize}가 0보다 크면 그 값을, 아니면 {@link JdbcConnectionPools#DEFAULT_MAX_POOL_SIZE}를 사용한다.
		 * <p>
		 * 풀은 반환된 연결의 세션 상태 중 일부(auto-commit 등)만 복원하므로, {@code SET} 문 등으로 세션
		 * 상태를 바꾸는 호출자는 사용 후 원래대로 돌려 놓아야 한다. 또한 연결 검사에 JDBC 4의
		 * {@link Connection#isValid(int)}를 사용하므로, 이를 지원하지 않는 드라이버에는 쓸 수 없다.
		 *
		 * @return	자기 자신.
		 */
		public Builder pooled() {
			m_pooled = true;
			return this;
		}

		/**
		 * 주어진 최대 크기의 연결 풀을 사용하도록 지정한다.
		 * <p>
		 * 같은 연결 정보의 풀이 이미 있으면 그 풀을 공유하며, 이때 풀의 크기는 바뀌지 않는다.
		 *
		 * @param maxPoolSize	최대 풀 크기. 0보다 커야 한다.
		 * @return	자기 자신.
		 * @throws IllegalArgumentException	{@code maxPoolSize}가 0 이하인 경우.
		 * @see #pooled()
		 */
		public Builder pooled(int maxPoolSize) {
			Preconditions.checkArgument(maxPoolSize > 0, "invalid maxPoolSize: %d", maxPoolSize);
			m_pooled = true;
			m_maxPoolSize = maxPoolSize;
			return this;
		}

		/**
		 * 설정된 정보로부터 {@link JdbcProcessor}를 생성한다.
		 *
		 * @return	생성된 {@link JdbcProcessor}.
		 * @throws IllegalArgumentException	필수 정보가 빠지거나 {@code jdbcUrl}과
		 * 					{@code system}/host/port/dbName이 동시에 지정된 경우, 또는 시스템·URL prefix가
		 * 					지원되지 않는 경우.
		 */
		public JdbcProcessor build() {
			// 사용자·패스워드는 선택 항목이다. 없으면 드라이버에 넘기지 않는다.

			String jdbcUrl;
			String driver;

			if ( m_jdbcUrl != null ) {
				List<String> conflicting = new ArrayList<>();
				if ( m_system != null ) conflicting.add("system");
				if ( m_host != null ) conflicting.add("host");
				if ( m_port != null ) conflicting.add("port");
				if ( m_dbName != null ) conflicting.add("dbName");
				Preconditions.checkArgument(conflicting.isEmpty(), "jdbcUrl cannot be combined with %s", conflicting);
				jdbcUrl = m_jdbcUrl;
				if ( m_driverClsName != null ) {
					driver = m_driverClsName;
				}
				else {
					FOption<JdbcConnectInfo> oConnInfo = getJdbcConnectInfoByUrl(jdbcUrl);
					if ( !oConnInfo.isPresent() ) {
						throw new IllegalArgumentException("invalid JdbcUrl: "
															+ JdbcUtils.maskPassword(jdbcUrl));
					}
					driver = oConnInfo.get().m_driverClassName;
				}
			}
			else {
				Preconditions.checkNotNullArgument(m_system, "system or jdbcUrl is required");
				Preconditions.checkNotNullArgument(m_host, "host is required when system is set");
				Preconditions.checkNotNullArgument(m_port, "port is required when system is set");
				Preconditions.checkNotNullArgument(m_dbName, "dbName is required when system is set");

				JdbcConnectInfo connInfo = getJdbcConnectInfo(m_system);
				Map<String,String> values = Map.of("host", m_host,
													"port", Integer.toString(m_port),
													"dbname", m_dbName);
				jdbcUrl = new StringSubstitutor(values).replace(connInfo.m_urlFormat);
				driver = m_driverClsName != null ? m_driverClsName : connInfo.m_driverClassName;
			}

			PoolKey poolKey = null;
			int maxPoolSize = 0;
			if ( m_pooled ) {
				maxPoolSize = (m_maxPoolSize > 0) ? m_maxPoolSize
							: (m_configMaxPoolSize > 0) ? m_configMaxPoolSize
							: JdbcConnectionPools.DEFAULT_MAX_POOL_SIZE;
				poolKey = new PoolKey(jdbcUrl, m_user, m_passwd, driver, m_classLoader, m_jarFile);
			}
			return new JdbcProcessor(jdbcUrl, m_user, m_passwd, driver, m_jarFile, m_classLoader, poolKey, maxPoolSize);
		}
	}

	// 따옴표 없는 식별자(유니코드 글자·숫자·밑줄·$, 첫 글자는 글자나 밑줄) 또는 큰따옴표 식별자(안에 큰따옴표 없음).
	// 선택적으로 "schema." prefix를 둔다.
	private static final String SQL_IDENTIFIER_PART = "(?:[\\p{L}_][\\p{L}\\p{N}_$]*|\"[^\"]+\")";
	private static final java.util.regex.Pattern SQL_IDENTIFIER
			= java.util.regex.Pattern.compile("^" + SQL_IDENTIFIER_PART + "(?:\\." + SQL_IDENTIFIER_PART + ")?$");

	private static void checkSqlIdentifier(String identifier, String role) {
		Preconditions.checkNotNullArgument(identifier, role + " is null");
		Preconditions.checkArgument(SQL_IDENTIFIER.matcher(identifier).matches(), "invalid %s: %s", role, identifier);
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
		if ( parts.length < 2 ) {
			return FOption.empty();
		}
		// 끝에 ':'를 붙여 하위 프로토콜 이름 전체를 비교한다("jdbc:my"가 "jdbc:mysql"과 일치하지 않도록).
		String prefix = String.format("%s:%s:", parts[0], parts[1]);
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
	
	private static final Map<String,JdbcConnectInfo> JDBC_URLS = Map.ofEntries(
		Map.entry("mysql", new JdbcConnectInfo(
				"jdbc:mysql://${host}:${port}/${dbname}?characterEncoding=utf8&useSSL=false&useCursorFetch=true",
				"com.mysql.cj.jdbc.Driver")),
		Map.entry("postgresql", new JdbcConnectInfo(
				"jdbc:postgresql://${host}:${port}/${dbname}",
				"org.postgresql.Driver")),
		Map.entry("mariadb", new JdbcConnectInfo(
				"jdbc:mariadb://${host}:${port}/${dbname}",
				"org.mariadb.jdbc.Driver")),
		Map.entry("kairos", new JdbcConnectInfo(
				"jdbc:kairos://${host}:${port}/${dbname}",
				"kr.co.realtimetech.kairos.jdbc.kairosDriver")),
		Map.entry("h2_remote", new JdbcConnectInfo(
				"jdbc:h2:tcp://${host}:${port}/${dbname}", "org.h2.Driver")),
		Map.entry("h2_local", new JdbcConnectInfo(
				"jdbc:h2:${dbname}", "org.h2.Driver")));
}
