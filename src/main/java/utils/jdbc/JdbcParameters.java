package utils.jdbc;

import java.io.File;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;


import utils.CSV;
import utils.Preconditions;

import picocli.CommandLine.Option;


/**
 * JDBC 접속 파라미터(시스템, 호스트, 포트, 사용자, 비밀번호, 데이터베이스, 드라이버 jar 경로)를
 * 담는 객체로, picocli 명령행 옵션({@code --jdbc-url}, {@code --jdbc_jar})으로도 설정할 수 있다.
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
	@Nullable private String m_jdbcJarPath = null;
	
	/**
	 * 모든 파라미터가 비어 있는 {@code JdbcParameters} 객체를 생성한다.
	 * <p>
	 * 주로 picocli가 옵션 값을 채워 넣을 때 사용된다.
	 */
	public JdbcParameters() { }
	
	/**
	 * 주어진 접속 파라미터로 {@code JdbcParameters} 객체를 생성한다.
	 * 인자의 {@code null} 여부는 검사하지 않으며, 드라이버 jar 경로는 지정되지 않는다.
	 *
	 * @param system	DBMS 시스템 이름 (예: {@code "mysql"}, {@code "postgresql"}).
	 * @param host		DBMS 호스트 이름.
	 * @param port		DBMS 포트 번호.
	 * @param user		접속 사용자 이름.
	 * @param passwd	접속 비밀번호.
	 * @param dbName	데이터베이스 이름.
	 */
	public JdbcParameters(String system, String host, int port, String user, String passwd, String dbName) {
		m_system = system;
		m_host = host;
		m_port = port;
		m_user = user;
		m_passwd = passwd;
		m_dbName = dbName;
	}
	
	/**
	 * 이 파라미터로 {@link JdbcProcessor}를 생성한다.
	 * <p>
	 * 드라이버 jar 경로가 지정된 경우 {@link JdbcProcessor.Builder#jarFile(File)}로 전달된다.
	 *
	 * @return	생성된 {@link JdbcProcessor}.
	 */
	public JdbcProcessor createJdbcProcessor() {
		JdbcProcessor.Builder builder = JdbcProcessor.builder()
													.system(m_system)
													.host(m_host)
													.port(m_port)
													.user(m_user)
													.password(m_passwd)
													.dbName(m_dbName);
		if ( m_jdbcJarPath != null ) {
			builder.jarFile(new File(m_jdbcJarPath));
		}
		
		return builder.build();
	}
	
	/**
	 * DBMS 시스템 이름을 반환한다.
	 *
	 * @return	DBMS 시스템 이름.
	 */
	public String system() {
		return m_system;
	}

	/**
	 * {@code <system>:<host>:<port>:<user>:<passwd>:<db_name>} 형식의 JDBC 위치 문자열을
	 * 파싱하여 시스템, 호스트, 포트, 사용자, 비밀번호, 데이터베이스 이름을 설정한다.
	 * <p>
	 * 문자열은 {@link CSV#parseCsv(String, char)}로 '{@code :}' 기준으로 분리된다.
	 * 드라이버 jar 경로는 변경되지 않는다.
	 *
	 * @param loc	JDBC 위치 문자열.
	 * @return	이 객체.
	 * @throws NullPointerException	{@code loc}이 {@code null}인 경우.
	 * @throws IllegalArgumentException	분리된 항목이 정확히 6개가 아니거나, 포트가 정수가 아닌 경우
	 * 									({@link NumberFormatException}).
	 */
	@Option(names={"--jdbc-url"},
			paramLabel="<system>:<jdbc_host>:<jdbc_port>:<user_id>:<passwd>:<db_name>",
			required=true,
			description={"JDBC locator, (eg. 'mysql:localhost:3306:sbdata:<passwd>:<db-name>')"})
	public JdbcParameters jdbcLoc(String loc) {
		Preconditions.checkNotNullArgument(loc, "JDBC locator is null");
		
		List<String> parts = CSV.parseCsv(loc, ':').toList();
		// 비밀번호가 들어 있으므로 입력 문자열은 메시지에 넣지 않는다.
		Preconditions.checkArgument(parts.size() == 6,
								"invalid JDBC locator: expected 6 colon-separated tokens "
								+ "(system:host:port:user:passwd:dbname), got %s", parts.size());
		
		m_system = parts.get(0);
		m_host = parts.get(1);
		m_port = Integer.parseInt(parts.get(2));
		m_user = parts.get(3);
		m_passwd = parts.get(4);
		m_dbName = parts.get(5);
		
		return this;
	}
	
	/**
	 * DBMS 호스트 이름을 반환한다.
	 *
	 * @return	호스트 이름.
	 */
	public String host() {
		return m_host;
	}
	
	/**
	 * DBMS 포트 번호를 반환한다.
	 *
	 * @return	포트 번호.
	 */
	public int port() {
		return m_port;
	}
	
	/**
	 * 접속 사용자 이름을 반환한다.
	 *
	 * @return	사용자 이름.
	 */
	public String user() {
		return m_user;
	}
	
	/**
	 * 접속 비밀번호를 반환한다.
	 *
	 * @return	비밀번호.
	 */
	public String password() {
		return m_passwd;
	}
	
	/**
	 * 데이터베이스 이름을 반환한다.
	 *
	 * @return	데이터베이스 이름.
	 */
	public String database() {
		return m_dbName;
	}
	
	/**
	 * JDBC 드라이버 jar 파일 경로를 반환한다.
	 *
	 * @return	드라이버 jar 경로. 지정되지 않은 경우 빈 {@link Optional}.
	 */
	public Optional<String> jdbcJarPath() {
		return Optional.ofNullable(m_jdbcJarPath);
	}

	/**
	 * JDBC 드라이버 jar 파일 경로를 설정한다.
	 *
	 * @param jarPath	드라이버 jar 경로. {@code null}이면 지정 해제된다.
	 */
	@Option(names={"--jdbc_jar"}, paramLabel="jdbc_jar_path", required=false,
			description={"the path to JDBC driver jar"})
	public void jdbcJarPath(String jarPath) {
		m_jdbcJarPath = jarPath;
	}
	
	/**
	 * 드라이버 jar 경로를 포함한 모든 파라미터를 복사한 새 {@code JdbcParameters} 객체를 반환한다.
	 *
	 * @return	복사된 {@code JdbcParameters} 객체.
	 */
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
