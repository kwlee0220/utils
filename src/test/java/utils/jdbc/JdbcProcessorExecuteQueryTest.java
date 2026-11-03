package utils.jdbc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * {@link JdbcProcessor}의 질의 메소드가 정상·실패·조기 종료 경로에서 자원을 닫는지 확인한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcProcessorExecuteQueryTest {
	private JdbcProcessor m_proc;
	private Connection m_conn;
	private Statement m_stmt;

	@BeforeEach
	public void setUp() throws SQLException {
		JdbcProcessor proc = JdbcProcessor.builder()
										.jdbcUrl("jdbc:postgresql://localhost:5432/test")
										.user("u")
										.password("p")
										.build();
		m_conn = mock(Connection.class);
		m_stmt = mock(Statement.class);
		when(m_conn.createStatement()).thenReturn(m_stmt);

		m_proc = spy(proc);
		doReturn(m_conn).when(m_proc).connect();
	}

	@Test
	public void stmtSetterRuntimeException_closesStatementAndConnection() throws SQLException {
		IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
								() -> m_proc.executeQuery("select 1", stmt -> { throw new IllegalStateException("boom"); }));
		Assertions.assertEquals("boom", e.getMessage());
		verify(m_stmt).close();
		verify(m_conn).close();
	}

	@Test
	public void queryRuntimeException_closesConnection() throws SQLException {
		when(m_stmt.executeQuery(anyString())).thenThrow(new IllegalStateException("driver bug"));

		Assertions.assertThrows(IllegalStateException.class, () -> m_proc.executeQuery("select 1"));
		verify(m_conn).close();
	}

	@Test
	public void sqlException_stillClosesAndRethrows() throws SQLException {
		when(m_stmt.executeQuery(anyString())).thenThrow(new SQLException("bad sql"));

		SQLException e = Assertions.assertThrows(SQLException.class,
										() -> m_proc.executeQuery("selec 1", stmt -> stmt.setFetchSize(10)));
		Assertions.assertEquals("bad sql", e.getMessage());
		verify(m_stmt).close();
		verify(m_conn).close();
	}

	// --- query / queryFirst / countRows ---

	private ResultSet rows(String... values) throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		Boolean[] nexts = new Boolean[values.length];
		java.util.Arrays.fill(nexts, true);
		org.mockito.stubbing.OngoingStubbing<Boolean> st = when(rs.next());
		for ( int i = 0; i < values.length; ++i ) {
			st = st.thenReturn(true);
		}
		st.thenReturn(false);
		if ( values.length > 0 ) {
			org.mockito.stubbing.OngoingStubbing<String> gs = when(rs.getString(1));
			for ( String v: values ) {
				gs = gs.thenReturn(v);
			}
		}
		when(m_stmt.executeQuery(anyString())).thenReturn(rs);
		return rs;
	}

	@Test
	public void streamQuery_closesAllAfterTerminalOperation() throws Exception {
		ResultSet rs = rows("a", "b");
		Assertions.assertEquals(java.util.List.of("a", "b"), m_proc.query("select x", r -> r.getString(1)).toList());
		verify(rs, atLeastOnce()).close();
		verify(m_stmt).close();
		verify(m_conn).close();
	}

	@Test
	public void streamQuery_closeBeforeReadingClosesAll() throws Exception {
		ResultSet rs = rows("a", "b");
		m_proc.query("select x", r -> r.getString(1)).close();
		verify(rs, atLeastOnce()).close();
		verify(m_stmt).close();
		verify(m_conn).close();
	}

	@Test
	public void streamQuery_queryFailure_throwsSQLExceptionAndCloses() throws Exception {
		when(m_stmt.executeQuery(anyString())).thenThrow(new SQLException("bad sql"));
		SQLException e = Assertions.assertThrows(SQLException.class,
												() -> m_proc.query("selec x", r -> r.getString(1)));
		Assertions.assertEquals("bad sql", e.getMessage());
		verify(m_stmt).close();
		verify(m_conn).close();
	}

	/**
	 * 순회 중 행 읽기 실패는 checked 예외를 던질 수 없으므로 원인을 담은 JdbcException이다.
	 */
	@Test
	public void streamQuery_readFailureDuringIteration_isJdbcException() throws Exception {
		ResultSet rs = rows("a", "b");
		utils.stream.FStream<String> stream = m_proc.query("select x", r -> {
			String v = r.getString(1);
			if ( "b".equals(v) ) {
				throw new SQLException("broken row");
			}
			return v;
		});
		java.util.Iterator<String> iter = stream.iterator();
		Assertions.assertEquals("a", iter.next());
		JdbcException e = Assertions.assertThrows(JdbcException.class, iter::next);
		Assertions.assertEquals("broken row", e.getCause().getMessage());

		// 순회 중 실패로 멈춘 경우는 호출자가 닫는다(try-with-resources 권장). 닫으면 자원이 모두 닫힌다.
		stream.close();
		verify(rs, atLeastOnce()).close();
		verify(m_conn).close();
	}

	/**
	 * 첫 행도 스트림을 읽을 때 읽는다. 그 실패는
	 * 스트림에서는 {@link JdbcException}이고, {@code queryFirst}는 원인 {@link SQLException}으로 풀어서 던진다.
	 */
	@Test
	public void firstRowIsReadLazily() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(m_stmt.executeQuery(anyString())).thenReturn(rs);
		when(rs.next()).thenThrow(new SQLException("broken cursor"));

		utils.stream.FStream<String> stream = m_proc.query("select x", r -> r.getString(1));
		verify(rs, org.mockito.Mockito.never()).next();
		JdbcException e = Assertions.assertThrows(JdbcException.class, stream::next);
		Assertions.assertEquals("broken cursor", e.getCause().getMessage());
		stream.close();

		SQLException se = Assertions.assertThrows(SQLException.class,
												() -> m_proc.queryFirst("select x", r -> r.getString(1)));
		Assertions.assertEquals("broken cursor", se.getMessage());
		verify(m_conn, org.mockito.Mockito.times(2)).close();
	}

	/**
	 * {@link ResultSetObjectFStream}: 마지막 행 뒤에는 {@code ResultSet.next()}를 다시 부르지 않고, {@code null} 변환
	 * 결과도 원소로 내보내며, 닫으면 ResultSet을 한 번만 닫고 그 뒤 {@code next()}는 {@link IllegalStateException}이다.
	 */
	@Test
	public void resultSetObjectFStream_contract() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.next()).thenReturn(true, true, false);
		when(rs.getString(1)).thenReturn("a", (String)null);

		ResultSetObjectFStream<String> stream = new ResultSetObjectFStream<>(rs, r -> r.getString(1));
		verify(rs, org.mockito.Mockito.never()).next();
		Assertions.assertEquals("a", stream.next().get());
		Assertions.assertNull(stream.next().get());
		Assertions.assertTrue(stream.next().isAbsent());
		Assertions.assertTrue(stream.next().isAbsent());
		verify(rs, org.mockito.Mockito.times(3)).next();
		verify(rs, org.mockito.Mockito.never()).close();

		stream.close();
		stream.close();
		verify(rs, org.mockito.Mockito.times(1)).close();
		Assertions.assertThrows(IllegalStateException.class, stream::next);
	}

	@Test
	public void resultSetObjectFStream_runtimeExceptionIsNotWrapped() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.next()).thenReturn(true);
		try ( ResultSetObjectFStream<String> stream
						= new ResultSetObjectFStream<>(rs, r -> { throw new IllegalArgumentException("bad row"); }) ) {
			IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class, stream::next);
			Assertions.assertEquals("bad row", e.getMessage());
		}
		verify(rs).close();
	}

	@Test
	public void getFirstQuery_returnsFirstAndCloses() throws Exception {
		ResultSet rs = rows("a", "b");
		Assertions.assertEquals("a", m_proc.queryFirst("select x", r -> r.getString(1)).get());
		verify(rs, atLeastOnce()).close();
		verify(m_conn).close();

		rows();
		Assertions.assertTrue(m_proc.queryFirst("select x", r -> r.getString(1)).isAbsent());
	}

	@Test
	public void rowCount_returnsCountAndThrowsSQLException() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.next()).thenReturn(true);
		when(rs.getLong(1)).thenReturn(42L);
		when(m_stmt.executeQuery(anyString())).thenReturn(rs);
		Assertions.assertEquals(42L, m_proc.countRows("my_table"));
		verify(m_conn).close();

		when(m_stmt.executeQuery(anyString())).thenThrow(new SQLException("no table"));
		Assertions.assertThrows(SQLException.class, () -> m_proc.countRows("my_table"));
	}
}
