package utils.jdbc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * {@link JdbcUtils#bindToConnection(ResultSet)}와
 * {@link JdbcUtils#bindToConnection(ResultSet, Statement, Connection)} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcUtilsBindToConnectionTest {
	private ResultSet m_rs;
	private Statement m_stmt;
	private Connection m_conn;

	private ResultSet bind() throws SQLException {
		m_rs = mock(ResultSet.class);
		m_stmt = mock(Statement.class);
		m_conn = mock(Connection.class);
		when(m_rs.getStatement()).thenReturn(m_stmt);
		when(m_stmt.getConnection()).thenReturn(m_conn);
		return JdbcUtils.bindToConnection(m_rs);
	}

	@Test
	public void reusesProxyClass() throws SQLException {
		Assertions.assertSame(bind().getClass(), bind().getClass());
	}

	@Test
	public void closeClosesResultSetStatementAndConnectionOnce() throws SQLException {
		ResultSet bound = bind();
		bound.close();
		bound.close();
		verify(m_rs, times(1)).close();
		verify(m_stmt, times(1)).close();
		verify(m_conn, times(1)).close();
	}

	@Test
	public void delegatesOtherCallsAndKeepsCheckedExceptions() throws SQLException {
		ResultSet bound = bind();
		when(m_rs.next()).thenReturn(true);
		when(m_rs.getString(1)).thenReturn("a");
		Assertions.assertTrue(bound.next());
		Assertions.assertEquals("a", bound.getString(1));

		// 원본이 던진 SQLException이 감싸지지 않고 그대로 나온다.
		when(m_rs.getInt(1)).thenThrow(new SQLException("bad column"));
		SQLException e = Assertions.assertThrows(SQLException.class, () -> bound.getInt(1));
		Assertions.assertEquals("bad column", e.getMessage());

		Assertions.assertEquals(bound, bound);
		Assertions.assertNotEquals(bound, m_rs);
	}

	@Test
	public void rejectsNull() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> JdbcUtils.bindToConnection(null));
	}

	/**
	 * 닫을 Statement와 Connection은 프록시를 만들 때 확보한다. 원본 ResultSet이 먼저 닫혀 {@code getStatement()}가
	 * 실패해도 프록시를 닫으면 Statement와 연결이 닫힌다.
	 */
	@Test
	public void closesResourcesCapturedAtBindTime() throws SQLException {
		ResultSet bound = bind();
		when(m_rs.getStatement()).thenThrow(new SQLException("result set closed"));
		bound.close();
		verify(m_stmt, times(1)).close();
		verify(m_conn, times(1)).close();
	}

	@Test
	public void bindFailsIfStatementIsUnavailable() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getStatement()).thenThrow(new SQLException("result set closed"));
		Assertions.assertThrows(SQLException.class, () -> JdbcUtils.bindToConnection(rs));
	}

	@Test
	public void explicitResourcesAreClosedAndMayBeNull() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		Statement stmt = mock(Statement.class);
		Connection conn = mock(Connection.class);
		JdbcUtils.bindToConnection(rs, stmt, conn).close();
		verify(rs).close();
		verify(stmt).close();
		verify(conn).close();

		ResultSet rs2 = mock(ResultSet.class);
		JdbcUtils.bindToConnection(rs2, null, null).close();
		verify(rs2).close();
	}
}
