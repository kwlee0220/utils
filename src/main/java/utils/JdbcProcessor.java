package utils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.log4j.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcProcessor {
	private final Logger s_logger = Logger.getLogger(JdbcProcessor.class);

	private final String m_jdbcUrl;
	private final String m_user;
	private final String m_passwd;
	private final String m_driverClsName;
	
	public JdbcProcessor(String jdbcUrl, String user, String passwd, String driverClsName) {
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
	
	public Connection connect() throws SQLException {
		try {
			Class.forName(m_driverClsName);
			return DriverManager.getConnection(m_jdbcUrl, m_user, m_passwd);
		}
		catch ( ClassNotFoundException e ) {
			throw new JDBCException("fails to load JDBC driver class: name=" + m_driverClsName);
		}
	}
	
	public void dropTable(String tblName) throws SQLException {
		Statement stmt = connect().createStatement();
		
		try {
			stmt.executeUpdate(String.format("drop table %s", tblName));
		}
		finally {
			Connection conn = stmt.getConnection();
			
			JDBCUtils.closeQuietly(stmt);
			JDBCUtils.closeQuietly(conn);
		}
	}
	
	public static class ConnectedResultSet implements AutoCloseable {
		private ResultSet m_rs;
		
		ConnectedResultSet(ResultSet rs) {
			m_rs = rs;
		}

		@Override
		public void close() throws Exception {
			if ( m_rs != null ) {
				Statement stmt = m_rs.getStatement();
				Connection conn = stmt.getConnection();
				
				JDBCUtils.closeQuietly(stmt);
				JDBCUtils.closeQuietly(conn);
				
				m_rs = null;
			}
		}
		
		public ResultSet getResultSet() {
			Preconditions.checkState(m_rs != null, "closed already");
			return m_rs;
		}
	}
	
	public ConnectedResultSet executeQuery(String sql) throws SQLException {
		return new ConnectedResultSet(connect().createStatement().executeQuery(sql));
	}
	
	public <T> Stream<T> executeQuery(String sql, Function<ResultSet,T> functor) throws SQLException {
		ResultSet rs = connect().createStatement().executeQuery(sql);
		return Utilities.stream(new ResultSetIterator<>(rs, functor));
	}
	
	public <T> Stream<T> executeQuery(PreparedStatement pstmt, Function<ResultSet,T> functor)
		throws SQLException {
		ResultSet rs = pstmt.executeQuery();
		return Utilities.stream(new ResultSetIterator<>(rs, functor));
	}
	
	public void processQuery(String sql, JdbcConsumer<ResultSet> resultConsumer)
									throws SQLException, ExecutionException {
		Connection conn = null;
		Statement stmt = null;
		try {
			conn = connect();
			stmt = conn.createStatement();
			
			ResultSet rs = stmt.executeQuery(sql);
			while ( rs.next() ) {
				resultConsumer.accept(rs);
			}
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			Throwable cause = e.getCause();
			if ( cause != null ) {
				throw new ExecutionException(cause);
			}
			else {
				throw new ExecutionException(e);
			}
		}
		finally {
			JDBCUtils.closeQuietly(stmt);
			JDBCUtils.closeQuietly(conn);
		}
	}
	
	public int executeUpdate(String sql) throws SQLException, ExecutionException {
		Connection conn = null;
		Statement stmt = null;
		try {
			conn = connect();
			stmt = conn.createStatement();
			return stmt.executeUpdate(sql);
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			Throwable cause = e.getCause();
			if ( cause != null ) {
				throw new ExecutionException(cause);
			}
			else {
				throw new ExecutionException(e);
			}
		}
		finally {
			JDBCUtils.closeQuietly(stmt);
			JDBCUtils.closeQuietly(conn);
		}
	}
	
	public int executeUpdate(PreparedStatement pstmt) throws SQLException, ExecutionException {
		try {
			return pstmt.executeUpdate();
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			Throwable cause = e.getCause();
			if ( cause != null ) {
				throw new ExecutionException(cause);
			}
			else {
				throw new ExecutionException(e);
			}
		}
		finally {
			Connection conn = pstmt.getConnection();
			JDBCUtils.closeQuietly(pstmt);
			JDBCUtils.closeQuietly(conn);
		}
	}
	
	public void execute(JdbcConsumer<Statement> job) throws SQLException, ExecutionException {
		Connection conn = connect();
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			
			job.accept(stmt);
		}
		catch ( SQLException e ) {
			throw e;
		}
		catch ( Throwable e ) {
			Throwable cause = e.getCause();
			if ( cause != null ) {
				throw new ExecutionException(cause);
			}
			else {
				throw new ExecutionException(e);
			}
		}
		finally {
			JDBCUtils.closeQuietly(stmt);
			JDBCUtils.closeQuietly(conn);
		}
	}
	
	public Map<String,String> getColumns(String tblName) throws SQLException, ExecutionException {
		Map<String,String> columns = Maps.newLinkedHashMap();
		
		Connection conn = null;
		Statement stmt = null;
		try {
			conn = connect();
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
			Throwable cause = e.getCause();
			if ( cause != null ) {
				throw new ExecutionException(cause);
			}
			else {
				throw new ExecutionException(e);
			}
		}
		finally {
			JDBCUtils.closeQuietly(stmt);
			JDBCUtils.closeQuietly(conn);
		}
	}
	
	@Override
	public String toString() {
		return String.format("url=%s,user=%s,driver=%s", m_jdbcUrl, m_user, m_driverClsName);
	}
	
	private static class ResultSetIterator<T> implements Iterator<T> {
		private ResultSet m_rs;
		private final Function<ResultSet,T> m_functor;
		private boolean m_hasNext;
		
		ResultSetIterator(ResultSet rs, Function<ResultSet,T> functor) throws SQLException {
			m_rs = rs;
			m_functor = functor;
			
			m_hasNext = m_rs.next();
		}
		
		@Override
		public boolean hasNext() {
			Preconditions.checkState(m_rs != null, "already closed");
			return m_hasNext;
		}

		@Override
		public T next() {
			Preconditions.checkState(m_rs != null, "already closed");
			
			try {
				T result = m_functor.apply(m_rs);
				m_hasNext = m_rs.next();
				
				return result;
			}
			catch ( SQLException e ) {
				throw new RuntimeException(e);
			}
		}
		
		public void close() throws Exception {
			if ( m_rs != null ) {
				Statement stmt = m_rs.getStatement();
				Connection conn = stmt.getConnection();
				
				JDBCUtils.closeQuietly(stmt);
				JDBCUtils.closeQuietly(conn);
				
				m_rs = null;
			}
		}
	}
	
	@FunctionalInterface
	public static interface JdbcConsumer<T> {
		public void accept(T data) throws SQLException;
	}
}
