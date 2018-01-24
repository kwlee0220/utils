package utils.jdbc;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import utils.Throwables;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcProcessor implements Serializable {
	private static final long serialVersionUID = -7730009459945414576L;
	private final Logger s_logger = LoggerFactory.getLogger(JdbcProcessor.class);

	private final String m_jdbcUrl;
	private final String m_user;
	private final String m_passwd;
	private final String m_driverClsName;
	
	/**
	 * 주어진 정보를 이용하한 JDBC 연결기 객체를 생성한다.
	 * 
	 * @param jdbcUrl	JDBC URL
	 * @param user		JDBC user
	 * @param passwd	JDBC password
	 * @param driverClsName	JDBC driver class name
	 */
	public JdbcProcessor(String jdbcUrl, String user, String passwd, String driverClsName) {
		Preconditions.checkNotNull(jdbcUrl, "JDBC URL is null");
		Preconditions.checkNotNull(user, "JDBC user is null");
		Preconditions.checkNotNull(passwd, "JDBC password is null");
		Preconditions.checkNotNull(driverClsName, "JDBC driver class is null");
		
		m_jdbcUrl = jdbcUrl;
		m_user = user;
		m_passwd = passwd;
		m_driverClsName = driverClsName;
	}
	
	public String getJdbcUrl() {
		return m_jdbcUrl;
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
	
	/**
	 * 주어진 연결 정보를 이용하여 JDBC 연결 객체를 반환한다.
	 * 
	 * @return	JDBC 연결 객체
	 * @throws SQLException	JDBC 연결 도중 오류가 발생된 경우.
	 */
	public Connection connect() throws SQLException {
		try {
			Class.forName(m_driverClsName);
			return DriverManager.getConnection(m_jdbcUrl, m_user, m_passwd);
		}
		catch ( ClassNotFoundException e ) {
			throw new JdbcException("fails to load JDBC driver class: name=" + m_driverClsName);
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
		Preconditions.checkNotNull(tblName, "table name is null");
		
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
	public ResultSet executeQuery(String sql) throws SQLException {
		ResultSet rs = connect().createStatement().executeQuery(sql);
		return JdbcUtils.bindToConnection(rs);
	}
	
	public Stream<ResultSet> streamQuery(String sql) throws SQLException {
		ResultSet rs = connect().createStatement().executeQuery(sql);
		rs = JdbcUtils.bindToConnection(rs);
		return StreamSupport.stream(new ResultSetSpliterator(rs), false);
	}
	
	public FStream<ResultSet> fstreamQuery(String sql) throws SQLException {
		ResultSet rs = connect().createStatement().executeQuery(sql);
		rs = JdbcUtils.bindToConnection(rs);
		return new ResultSetFStream(rs);
	}
	
	public Stream<ResultSet> executeQuery(PreparedStatement pstmt) throws SQLException {
		ResultSet rs = pstmt.executeQuery();
		return StreamSupport.stream(new ResultSetSpliterator(rs), false);
	}
	
	public FStream<ResultSet> fexecuteQuery(PreparedStatement pstmt) throws SQLException {
		ResultSet rs = pstmt.executeQuery();
		return new ResultSetFStream(rs);
	}
	
	public void processQuery(String sql, JdbcConsumer<ResultSet> resultConsumer)
		throws SQLException, ExecutionException {
		try ( Connection conn = connect() ) {
			Statement stmt = conn.createStatement();
			
			ResultSet rs = stmt.executeQuery(sql);
			while ( rs.next() ) {
				resultConsumer.accept(rs);
			}
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			throw new ExecutionException(Throwables.unwrapThrowable(e));
		}
	}
	
	public int executeUpdate(String sql) throws SQLException, ExecutionException {
		try ( Connection conn = connect() ) {
			return conn.createStatement().executeUpdate(sql);
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			throw new ExecutionException(Throwables.unwrapThrowable(e));
		}
	}
	
	public int executeUpdate(String sql, JdbcConsumer<PreparedStatement> pstmtSetter)
		throws SQLException, ExecutionException {
		try ( Connection conn = connect() ) {
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmtSetter.accept(pstmt);
			
			return pstmt.executeUpdate();
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			throw new ExecutionException(Throwables.unwrapThrowable(e));
		}
	}
	
	public void execute(JdbcConsumer<Statement> job) throws SQLException, ExecutionException {
		try ( Connection conn = connect() ) {
			Statement stmt = conn.createStatement();
			
			job.accept(stmt);
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			throw new ExecutionException(Throwables.unwrapThrowable(e));
		}
	}
	
	public Map<String,String> getColumns(String tblName) throws SQLException, ExecutionException {
		Map<String,String> columns = Maps.newLinkedHashMap();

		try ( Connection conn = connect() ) {
			DatabaseMetaData meta = conn.getMetaData();
			ResultSet rs = meta.getColumns(null, null, tblName, null);
			while ( rs.next() ) {
				String name = rs.getString("COLUMN_NAME");
				String type = rs.getString("TYPE_NAME");
				
				columns.put(name, type);
			}
			
			return columns;
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			throw new ExecutionException(Throwables.unwrapThrowable(e));
		}
	}
	
	@Override
	public String toString() {
		return String.format("url=%s,user=%s,driver=%s", m_jdbcUrl, m_user, m_driverClsName);
	}
	
	@FunctionalInterface
	public static interface JdbcConsumer<T> {
		public void accept(T data) throws SQLException;
	}
}