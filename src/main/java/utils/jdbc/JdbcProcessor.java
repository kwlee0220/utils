package utils.jdbc;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.commons.text.StringSubstitutor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.CSV;
import utils.Preconditions;
import utils.Throwables;
import utils.func.CheckedConsumerX;
import utils.func.CheckedFunctionX;
import utils.func.FOption;
import utils.io.IOUtils;
import utils.stream.FStream;
import utils.stream.KeyValueFStream;


/**
 * {@code JdbcProcessor}는 JDBC 연결과 SQL 질의 실행을 다루는 헬퍼 클래스이다.
 * <p>
 * JDBC URL·사용자·패스워드·드라이버 클래스 이름을 이용하여 {@link Connection}을 생성하고,
 * SQL 질의 실행, 테이블 존재 확인/삭제, 컬럼 메타데이터 조회 등의 기능을 제공한다.
 * <p>
 * 인스턴스 생성은 {@link #builder()} 또는 입력 형식별 빌더 진입점({@link #builder(JdbcConfiguration)},
 * {@link #builderFromFullJdbcUrl(String)}, {@link #builderFromCsv(String)})을 통해 이루어진다.
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
 * try ( ResultSet rs = jdbc.executeQuery("select * from mytable", true) ) {
 *     // ...
 * }
 * </pre>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcProcessor {
	private static final Logger s_logger = LoggerFactory.getLogger(JdbcProcessor.class);

	private final String m_jdbcUrl;
	private final String m_user;
	private final String m_passwd;
	private final String m_driverClsName;
	private @Nullable File m_jarFile;
	private @Nullable ClassLoader m_cloader;
	
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
	 * @param user			JDBC 사용자. {@code null}이면 안 된다.
	 * @param passwd		JDBC 패스워드. {@code null}이면 안 된다.
	 * @param driverClsName	JDBC 드라이버 클래스명. {@code null}이면 안 된다.
	 * @throws IllegalArgumentException	인자 중 하나라도 {@code null}인 경우.
	 */
	private JdbcProcessor(String jdbcUrl, String user, String passwd, String driverClsName) {
		Preconditions.checkNotNullArgument(jdbcUrl, "JDBC URL is null");
		Preconditions.checkNotNullArgument(user, "JDBC user is null");
		Preconditions.checkNotNullArgument(passwd, "JDBC password is null");
		Preconditions.checkNotNullArgument(driverClsName, "JDBC driver class is null");
		
		m_jdbcUrl = jdbcUrl;
		m_user = user;
		m_passwd = passwd;
		m_driverClsName = driverClsName;
	}
	
	/**
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
	 * @return	이 인스턴스에 설정된 JDBC 사용자.
	 */
	public String getUser() {
		return m_user;
	}

	/**
	 * @return	이 인스턴스에 설정된 JDBC 패스워드.
	 */
	public String getPassword() {
		return m_passwd;
	}

	/**
	 * @return	이 인스턴스에 설정된 JDBC 드라이버 클래스명.
	 */
	public String getDriverClassName() {
		return m_driverClsName;
	}

	/**
	 * @return	빌더 시점에 지정된 드라이버 적재용 {@link ClassLoader}, 미지정 시 {@code null}.
	 */
	public ClassLoader getClassLoader() {
		return m_cloader;
	}
	
	/**
	 * 설정된 정보를 이용하여 JDBC {@link Connection}을 생성하여 반환한다.
	 * <p>
	 * 드라이버 클래스 적재 순서는 다음과 같다.
	 * <ol>
	 *     <li>{@link Builder#classLoader(ClassLoader)}로 명시 ClassLoader가 지정된 경우 그것을 사용.</li>
	 *     <li>그렇지 않고 {@link Builder#jarFile(File)}로 jar 파일이 지정된 경우, 해당 jar를 적재하는
	 *         {@link URLClassLoader}를 한 번 생성해 그 이후의 호출에서 재사용한다.</li>
	 *     <li>둘 다 지정되지 않은 경우 시스템 기본 ClassLoader로 {@link Class#forName(String)}을 호출.</li>
	 * </ol>
	 * (1)·(2)의 경우 {@link Driver}가 인스턴스화되어 {@link DriverManager#registerDriver(Driver)}로
	 * 등록된다.
	 *
	 * @return	JDBC 연결 객체.
	 * @throws SQLException	jar URL 변환 실패, 드라이버 클래스 적재 실패, 또는
	 * 						{@link DriverManager#getConnection(String, String, String)} 실패 시.
	 */
	public Connection connect() throws SQLException {
		if ( m_jarFile != null && m_cloader == null ) {
			try {
				URI uri = new URI(String.format("jar:file:%s!/", m_jarFile.getAbsolutePath()));
				m_cloader = new URLClassLoader(new URL[]{uri.toURL()});
			}
			catch ( MalformedURLException | URISyntaxException e ) {
				throw new SQLException("fails to create " + getClass()
										+ ", invalid jar path=" + m_jarFile, e);
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
		catch ( ReflectiveOperationException | SQLException e ) {
			throw new JdbcException("fails to load JDBC driver class: name=" + m_driverClsName
									+ ", jdbcUrl=" + m_jdbcUrl, e);
		}
	}
	
	/**
	 * 주어진 이름의 테이블이 데이터베이스에 존재하는지 여부를 반환한다.
	 * <p>
	 * 비교는 대소문자를 구분하지 않는다 (case-insensitive).
	 *
	 * @param tableName	검색 대상 테이블 이름. {@code null}이면 안 된다.
	 * @return			존재하면 {@code true}, 그렇지 않으면 {@code false}.
	 * @throws JdbcException	JDBC 연결 또는 메타데이터 조회 중 오류가 발생한 경우.
	 */
	public boolean existsTable(String tableName) {
		Preconditions.checkNotNullArgument(tableName, "table name is null");

		try ( Connection conn = connect();
				ResultSet rs = conn.getMetaData()
									.getTables(null, null, null, new String[]{"TABLE"}) ) {
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
	 * 주어진 이름의 테이블을 삭제한다.
	 * <p>
	 * 내부적으로 {@code DROP TABLE} 문을 실행한다. 실행 중 {@link SQLException}이 발생하면
	 * (예: 테이블이 존재하지 않는 경우) 예외 메시지를 로그로 남기고 {@code false}를 반환한다.
	 *
	 * @param tblName	삭제할 테이블 이름. {@code null}이면 안 되며, 영문자/숫자/밑줄(과
	 * 					선택적 schema prefix {@code "schema.table"})만 허용된다.
	 * @return			삭제에 성공한 경우 {@code true}, 실패한 경우 {@code false}.
	 * @throws IllegalArgumentException	{@code tblName}이 식별자 형식에 맞지 않는 경우.
	 */
	public boolean dropTable(String tblName) {
		checkSqlIdentifier(tblName, "table name");

		try ( Connection conn = connect();
				Statement stmt = conn.createStatement() ) {
			String sql = String.format("drop table %s", tblName);
			s_logger.debug("delete table '{}': {}", tblName, sql);

			stmt.executeUpdate(sql);
			return true;
		}
		catch ( SQLException e ) {
			s_logger.info("fails to delete table={}", tblName, e);
			return false;
		}
	}
	
	/**
	 * 주어진 SQL 질의문을 실행시켜 결과 {@link ResultSet} 객체를 반환한다.
	 * <p>
	 * {@code autoClose}가 {@code true}인 경우, 반환된 ResultSet의 {@link ResultSet#close()}가
	 * 호출될 때 기반 JDBC 연결도 함께 close된다 ({@link JdbcUtils#bindToConnection(ResultSet)}).
	 * {@code false}인 경우 호출자가 ResultSet과 연결의 정리 책임을 진다.
	 *
	 * @param sql		실행할 SQL 질의문.
	 * @param autoClose	{@code true}이면 ResultSet close 시 Connection도 함께 close.
	 * @return			질의 결과 {@link ResultSet}.
	 * @throws SQLException	JDBC 연결 또는 질의 실행 중 오류가 발생한 경우.
	 */
	public ResultSet executeQuery(String sql, boolean autoClose) throws SQLException {
		Connection conn = connect();
		try {
			ResultSet rs = conn.createStatement().executeQuery(sql);
			return autoClose ? JdbcUtils.bindToConnection(rs) : rs;
		}
		catch ( SQLException e ) {
			IOUtils.closeQuietly(conn);
			throw e;
		}
	}

	/**
	 * 호출자가 {@link Statement}를 사전 설정한 뒤 SQL 질의를 실행한다.
	 * <p>
	 * {@code stmtSetter}는 생성된 {@link Statement}에 fetch size·timeout 등을 설정할 기회를
	 * 제공한다. 반환된 ResultSet은 {@link JdbcUtils#bindToConnection(ResultSet)}으로 감싸여
	 * close 시 Connection도 함께 close된다.
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
			return JdbcUtils.bindToConnection(stmt.executeQuery(sql));
		}
		catch ( SQLException e ) {
			IOUtils.closeQuietly(stmt, conn);
			throw e;
		}
	}
	
	/**
	 * SQL 질의 결과를 {@code deserializer}로 변환하여 {@link FStream}으로 반환한다.
	 * <p>
	 * 반환된 스트림이 close되거나 끝까지 소진되면 기반 ResultSet과 Connection이 모두 close된다.
	 *
	 * @param <T>			변환된 행 객체의 타입.
	 * @param sql			실행할 SQL 질의문.
	 * @param deserializer	각 ResultSet 행을 {@code T} 객체로 변환하는 함수.
	 * @return				변환된 객체들의 {@link FStream}.
	 */
	public <T> FStream<T> streamQuery(String sql, CheckedFunctionX<ResultSet, T, SQLException> deserializer) {
		return JdbcRowSource.select(deserializer)
							.from(this)
							.executeQuery(sql)
							.fstream();
	}

	/**
	 * SQL 질의 결과의 첫 행만 변환하여 반환한다.
	 *
	 * @param <T>			변환된 행 객체의 타입.
	 * @param sql			실행할 SQL 질의문.
	 * @param deserializer	ResultSet의 한 행을 {@code T} 객체로 변환하는 함수.
	 * @return				첫 행 변환 결과를 담은 {@link Optional}. 결과가 없으면 빈 값.
	 */
	public <T> FOption<T> getFirstQuery(String sql,
										CheckedFunctionX<ResultSet, T, SQLException> deserializer) {
		return JdbcRowSource.select(deserializer)
							.from(this)
							.executeQuery(sql)
							.first();
	}

	/**
	 * SQL update/DDL 문을 실행하고 영향받은 행 수를 반환한다.
	 *
	 * @param sql	실행할 SQL 문.
	 * @return		영향받은 행 수 (DDL의 경우 통상 0).
	 * @throws SQLException	JDBC 연결 또는 실행 중 오류가 발생한 경우.
	 */
	public int executeUpdate(String sql) throws SQLException {
		try ( Connection conn = connect() ) {
			return conn.createStatement().executeUpdate(sql);
		}
	}

	/**
	 * 호출자가 {@link PreparedStatement}에 파라미터를 바인딩한 뒤 update를 수행한다.
	 *
	 * @param sql		실행할 SQL 문.
	 * @param update	{@link PreparedStatement}에 파라미터를 설정하고 {@code executeUpdate}를
	 * 					호출하는 콜백.
	 * @throws SQLException			JDBC 연결 또는 실행 중 SQL 오류가 발생한 경우.
	 * @throws ExecutionException	{@code update}에서 SQL 외 예외가 발생한 경우.
	 */
	public void executeUpdate(String sql, JdbcConsumer<PreparedStatement> update)
		throws SQLException, ExecutionException {
		try ( Connection conn = connect();
				PreparedStatement pstmt = conn.prepareStatement(sql) ) {
			update.accept(pstmt);
		}
		catch ( SQLException e ) {
			// SQLException은 ExecutionException으로 wrap하지 않고 그대로 전파한다.
			throw e;
		}
		catch ( Throwable e ) {
			throw new ExecutionException(Throwables.unwrapThrowable(e));
		}
	}
	
	/**
	 * 주어진 테이블의 행 개수를 반환한다.
	 *
	 * @param tableName	대상 테이블 이름. 영문자/숫자/밑줄(과 선택적 schema prefix
	 * 					{@code "schema.table"})만 허용된다.
	 * @return			테이블의 행 개수.
	 * @throws IllegalArgumentException	{@code tableName}이 식별자 형식에 맞지 않는 경우.
	 * @throws SQLException	JDBC 연결 또는 질의 실행 중 오류가 발생한 경우.
	 */
	public long rowCount(String tableName) throws SQLException {
		checkSqlIdentifier(tableName, "table name");
		return streamQuery("select count(*) from " + tableName, rs -> rs.getLong(1))
				.findFirst()
				.get();
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
		 * @return	컬럼 이름.
		 */
		public String name() {
			return m_name;
		}

		/**
		 * @return	{@link java.sql.Types}에 정의된 SQL 데이터 타입 코드.
		 */
		public int type() {
			return m_type;
		}

		/**
		 * @return	데이터베이스 종속의 SQL 타입 이름.
		 */
		public String typeName() {
			return m_typeName;
		}

		/**
		 * @return	null 값을 허용하는지 여부. JDBC {@link java.sql.DatabaseMetaData#getColumns}의
		 * 			{@code NULLABLE} 컬럼이 0이 아닌 모든 경우({@code columnNullable},
		 * 			{@code columnNullableUnknown})에 대해 {@code true}를 반환한다.
		 */
		public boolean nullable() {
			return m_nullable;
		}
	}
	
	/**
	 * 주어진 테이블의 컬럼 메타데이터를 컬럼 이름을 키로 하는 맵으로 반환한다.
	 * <p>
	 * 반환되는 맵은 컬럼이 데이터베이스에 선언된 순서를 보존한다 ({@link java.util.LinkedHashMap}).
	 *
	 * @param tblName	대상 테이블 이름.
	 * @return			컬럼명 → {@link ColumnInfo} 맵.
	 * @throws SQLException	JDBC 연결 또는 메타데이터 조회 중 오류가 발생한 경우.
	 */
	public Map<String,ColumnInfo> getColumns(String tblName) throws SQLException {
		Preconditions.checkNotNullArgument(tblName, "table name is null");

		Map<String,ColumnInfo> columns = new LinkedHashMap<>();

		try ( Connection conn = connect();
				ResultSet rs = conn.getMetaData().getColumns(null, null, tblName, null) ) {
			while ( rs.next() ) {
				String name = rs.getString("COLUMN_NAME");
				int type = rs.getInt("DATA_TYPE");
				String typeName = rs.getString("TYPE_NAME");
				boolean nullable = rs.getInt("NULLABLE") != 0;

				columns.put(name, new ColumnInfo(name, type, typeName, nullable));
			}

			return columns;
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s?user=%s,driver=%s", m_jdbcUrl, m_user, m_driverClsName);
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
	 *
	 * @param config	JDBC 연결 설정 객체.
	 * @return			주어진 설정이 적용된 {@link Builder}.
	 */
	public static Builder builder(JdbcConfiguration config) {
		Preconditions.checkNotNullArgument(config, "config is null");
		Preconditions.checkNotNullArgument(config.getJdbcUrl(), "config.jdbcUrl is null");
		Preconditions.checkNotNullArgument(config.getUser(), "config.user is null");
		Preconditions.checkNotNullArgument(config.getPassword(), "config.password is null");

		Builder builder = new Builder()
							.jdbcUrl(config.getJdbcUrl())
							.user(config.getUser())
							.password(config.getPassword());
		if ( config.getDriverClassName() != null ) {
			builder.driverClassName(config.getDriverClassName());
		}
		return builder;
	}

	/**
	 * URL 쿼리 파라미터 형식으로 사용자·패스워드를 함께 담은 문자열로부터 {@link Builder}를 미리
	 * 채워 반환한다.
	 * <p>
	 * 입력 형식: {@code <jdbcUrl>?user=<user>&password=<passwd>}
	 * <br>예: {@code jdbc:mysql://localhost:3306/mydb?user=root&password=secret}
	 * <p>
	 * 파라미터 값은 URL 인코딩되어 있을 수 있으며 {@link FullJdbcUrlParser}에 의해 디코딩된다.
	 *
	 * @param fullJdbcUrl	JDBC URL·사용자·패스워드가 합쳐진 문자열.
	 * @return				주어진 정보가 적용된 {@link Builder}.
	 */
	public static Builder builderFromFullJdbcUrl(String fullJdbcUrl) {
		Preconditions.checkNotNullArgument(fullJdbcUrl, "fullJdbcUrl is null");

		Map<String,String> parsed = FullJdbcUrlParser.parse(fullJdbcUrl);
		String jdbcUrl = parsed.get("jdbcUrl");
		String user = parsed.get("user");
		String password = parsed.get("password");
		Preconditions.checkArgument(jdbcUrl != null && user != null && password != null,
									"fullJdbcUrl must include jdbcUrl, user and password"
									+ " (format: <jdbcUrl>?user=<user>&password=<passwd>): %s", fullJdbcUrl);

		return new Builder()
					.jdbcUrl(jdbcUrl)
					.user(user)
					.password(password);
	}

	/**
	 * 콜론({@code :})으로 구분된 문자열로부터 {@link Builder}를 미리 채워 반환한다.
	 * <p>
	 * 입력 형식: {@code system:host:port:user:passwd:dbname} (정확히 6개 토큰).
	 *
	 * @param str	콜론 구분 연결 정보 문자열.
	 * @return		주어진 정보가 적용된 {@link Builder}.
	 * @throws IllegalArgumentException	토큰 수가 6이 아니거나 port가 정수가 아닌 경우.
	 */
	public static Builder builderFromCsv(String str) {
		Preconditions.checkNotNullArgument(str, "input string is null");

		List<String> parts = CSV.parseCsv(str, ':').toList();
		Preconditions.checkArgument(parts.size() == 6,
								"expected 6 colon-separated tokens (system:host:port:user:passwd:dbname), got %d: %s",
								parts.size(), str);

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
		 * 설정된 정보로부터 {@link JdbcProcessor}를 생성한다.
		 *
		 * @return	생성된 {@link JdbcProcessor}.
		 * @throws IllegalArgumentException	필수 정보가 빠지거나 {@code jdbcUrl}과
		 * 					{@code system}/host/port/dbName이 동시에 지정된 경우, 또는 시스템·URL prefix가
		 * 					지원되지 않는 경우.
		 */
		public JdbcProcessor build() {
			Preconditions.checkNotNullArgument(m_user, "user is required");
			Preconditions.checkNotNullArgument(m_passwd, "password is required");

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
						throw new IllegalArgumentException("invalid JdbcUrl: " + jdbcUrl);
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

			JdbcProcessor processor = new JdbcProcessor(jdbcUrl, m_user, m_passwd, driver);
			if ( m_jarFile != null ) {
				processor.m_jarFile = m_jarFile;
			}
			if ( m_classLoader != null ) {
				processor.m_cloader = m_classLoader;
			}
			return processor;
		}
	}

	/**
	 * SQL 실행 중에 호출되는 소비자 인터페이스. {@link SQLException}을 throw 할 수 있다.
	 *
	 * @param <T>	소비할 객체의 타입.
	 */
	@FunctionalInterface
	public static interface JdbcConsumer<T> {
		void accept(T data) throws SQLException;
	}

	private static final java.util.regex.Pattern SQL_IDENTIFIER
			= java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$");

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
