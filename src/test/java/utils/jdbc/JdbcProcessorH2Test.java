package utils.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import utils.stream.FStream;


/**
 * 인메모리 H2 DB로 {@link JdbcProcessor}의 질의 메소드와 자원 반환을 확인한다.
 * <p>
 * 풀 크기가 1인 processor로 실패하는 질의를 여러 번 실행해도 다음 연결을 얻을 수 있어야 한다. 연결이 하나라도
 * 반환되지 않으면 풀이 고갈되어 연결 대기 시간(5초) 뒤 실패한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcProcessorH2Test {
	private String m_url;
	private JdbcProcessor m_proc;

	@BeforeEach
	public void setUp() throws SQLException {
		// 테스트마다 다른 DB. 마지막 연결이 닫혀도 DB가 사라지지 않도록 DB_CLOSE_DELAY=-1.
		m_url = "jdbc:h2:mem:jdbc-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
		m_proc = JdbcProcessor.builder()
								.jdbcUrl(m_url)
								.driverClassName("org.h2.Driver")
								.user("sa")
								.password("")
								.pooled(1)
								.build();
		try ( Connection conn = m_proc.connect();
				Statement stmt = conn.createStatement() ) {
			stmt.executeUpdate("create table item (id int primary key, name varchar(20))");
			stmt.executeUpdate("insert into item values (1, 'a'), (2, 'b'), (3, 'c')");
		}
	}

	@AfterEach
	public void tearDown() throws SQLException {
		try ( Connection conn = m_proc.connect();
				Statement stmt = conn.createStatement() ) {
			stmt.execute("drop all objects delete files");
		}
	}

	@Test
	public void queries_returnRows() throws SQLException {
		Assertions.assertEquals(List.of("a", "b", "c"),
								m_proc.query("select name from item order by id", rs -> rs.getString(1)).toList());
		Assertions.assertEquals("b", m_proc.queryFirst("select name from item where id = 2", rs -> rs.getString(1))
											.get());
		Assertions.assertEquals(3L, m_proc.countRows("item"));
		Assertions.assertEquals(2L, m_proc.countQueryRows("select id from item where id > 1"));
		Assertions.assertTrue(m_proc.tableExists("ITEM"));
		try ( ResultSet rs = m_proc.executeQuery("select count(*) from item") ) {
			Assertions.assertTrue(rs.next());
			Assertions.assertEquals(3, rs.getInt(1));
		}
	}

	/**
	 * 풀 크기 1에서 실패와 조기 종료를 반복해도 연결이 고갈되지 않는다.
	 */
	@Test
	@Timeout(30)
	public void failuresAndEarlyCloseDoNotLeakPooledConnection() throws Exception {
		for ( int i = 0; i < 3; ++i ) {
			// 잘못된 SQL
			Assertions.assertThrows(SQLException.class,
									() -> m_proc.query("selec name from item", rs -> rs.getString(1)));
			Assertions.assertThrows(SQLException.class, () -> m_proc.executeQuery("selec 1"));
			Assertions.assertThrows(SQLException.class, () -> m_proc.countRows("no_such_table"));

			// stmtSetter의 런타임 예외
			Assertions.assertThrows(IllegalStateException.class,
									() -> m_proc.executeQuery("select 1", stmt -> { throw new IllegalStateException(); }));

			// 첫 원소를 읽기 전에 닫기
			m_proc.query("select name from item", rs -> rs.getString(1)).close();

			// 중간에 멈추고 닫기
			try ( FStream<String> names = m_proc.query("select name from item order by id", rs -> rs.getString(1)) ) {
				Assertions.assertEquals("a", names.next().get());
			}
		}

		// 연결이 하나라도 남았다면 여기서 풀 고갈로 실패한다.
		Assertions.assertEquals(3L, m_proc.countRows("item"));
	}

	/**
	 * {@code executeUpdate}는 영향받은 행 수를 돌려주고, 바인딩 함수의 예외를 감싸지 않으며, 실패해도 연결을 돌려준다.
	 * 풀 크기가 1이라 연결이 하나라도 남으면 다음 호출이 풀 고갈로 실패한다.
	 */
	@Test
	@Timeout(30)
	public void executeUpdate_returnsCountAndPropagatesBinderException() throws SQLException {
		Assertions.assertEquals(2, m_proc.executeUpdate("update item set name = 'z' where id > 1"));
		Assertions.assertEquals(1, m_proc.executeUpdate("update item set name = ? where id = ?", pstmt -> {
			pstmt.setString(1, "x");
			pstmt.setInt(2, 1);
		}));
		Assertions.assertEquals("x", m_proc.queryFirst("select name from item where id = 1", rs -> rs.getString(1))
											.get());

		for ( int i = 0; i < 3; ++i ) {
			IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
						() -> m_proc.executeUpdate("update item set name = ?", pstmt -> { throw new IllegalStateException("boom"); }));
			Assertions.assertEquals("boom", e.getMessage());
			Assertions.assertThrows(SQLException.class, () -> m_proc.executeUpdate("updat item set name = 'q'"));
			Assertions.assertThrows(SQLException.class,
									() -> m_proc.executeUpdate("update item set name = ? where id = ?", pstmt -> pstmt.setString(1, "q")));
		}
		Assertions.assertEquals(0L, m_proc.countQueryRows("select id from item where name = 'q'"));
	}

	/**
	 * 연결·질의 실패는 풀 사용 여부와 상관없이 SQLException이다.
	 */
	@Test
	public void failuresAreSQLException() throws Exception {
		JdbcProcessor direct = JdbcProcessor.builder()
											.jdbcUrl(m_url)
											.driverClassName("org.h2.Driver")
											.user("sa")
											.password("")
											.build();
		for ( JdbcProcessor proc: List.of(m_proc, direct) ) {
			Assertions.assertThrows(SQLException.class, () -> proc.dropTable("no_such_table"));
			Assertions.assertThrows(SQLException.class, () -> proc.countQueryRows("selec 1"));
			Assertions.assertThrows(SQLException.class, () -> proc.queryFirst("selec 1", rs -> rs.getInt(1)));
		}

		JdbcProcessor noDriver = JdbcProcessor.builder()
											.jdbcUrl(m_url)
											.driverClassName("no.such.Driver")
											.user("sa")
											.password("")
											.build();
		Assertions.assertThrows(SQLException.class, noDriver::connect);
		Assertions.assertThrows(SQLException.class, () -> noDriver.tableExists("item"));

		direct.dropTable("item");
		Assertions.assertFalse(m_proc.tableExists("item"));
	}

	/**
	 * 사용자·패스워드 없이도 만들고 연결할 수 있다. 자격 증명은 URL에 넣거나 생략한다. 풀 키도 null 자격 증명을 다룬다.
	 */
	@Test
	public void credentialsAreOptional() throws Exception {
		String url = "jdbc:h2:mem:nocred-" + UUID.randomUUID() + ";USER=sa;PASSWORD=";
		JdbcConfiguration config = JdbcConfiguration.parse(url);
		for ( JdbcProcessor proc: List.of(JdbcProcessor.builder(config).driverClassName("org.h2.Driver").build(),
											JdbcProcessor.builder(config).driverClassName("org.h2.Driver").pooled(1).build()) ) {
			Assertions.assertNull(proc.getUser());
			Assertions.assertEquals(1, proc.queryFirst("select 1", rs -> rs.getInt(1)).get());
			Assertions.assertEquals(1, proc.queryFirst("select 1", rs -> rs.getInt(1)).get());
		}
	}

	private long openSessions() throws SQLException {
		try ( Connection conn = java.sql.DriverManager.getConnection(m_url, "sa", "");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("select count(*) from information_schema.sessions") ) {
			rs.next();
			return rs.getLong(1) - 1;		// 이 조회에 쓴 세션은 뺀다
		}
	}

	/**
	 * 풀을 쓰지 않는 processor도 실패·조기 종료 뒤에 열린 연결(H2 세션)을 남기지 않는다.
	 */
	@Test
	public void directProcessorLeavesNoOpenSession() throws Exception {
		JdbcProcessor direct = JdbcProcessor.builder().jdbcUrl(m_url).user("sa").password("").build();
		JdbcConnectionPools.closeAll();		// setUp의 pooled 연결도 닫아 세션 수를 0에서 시작한다
		long before = openSessions();

		Assertions.assertThrows(SQLException.class, () -> direct.query("selec 1", rs -> rs.getInt(1)));
		Assertions.assertThrows(SQLException.class, () -> direct.executeQuery("selec 1"));
		Assertions.assertThrows(SQLException.class, () -> direct.executeUpdate("updat item set name = 'x'"));
		Assertions.assertThrows(IllegalStateException.class,
								() -> direct.executeQuery("select 1", stmt -> { throw new IllegalStateException(); }));
		direct.query("select name from item", rs -> rs.getString(1)).close();
		try ( FStream<String> names = direct.query("select name from item order by id", rs -> rs.getString(1)) ) {
			names.next();
		}
		try ( ResultSet rs = direct.executeQuery("select count(*) from item") ) {
			rs.next();
		}
		Assertions.assertEquals(3L, direct.countRows("item"));
		Assertions.assertEquals("a", direct.queryFirst("select name from item order by id", rs -> rs.getString(1)).get());

		Assertions.assertEquals(before, openSessions());
	}

	/**
	 * full JDBC URL의 자격 증명이 실제 DB 접속까지 그대로 전달된다. 비밀번호의 {@code '+'}는 그대로, {@code %26}은
	 * {@code '&'}로 디코딩되고, 드라이버 옵션은 URL에 남는다.
	 */
	@Test
	public void fullJdbcUrlCredentialsReachTheDatabase() throws SQLException {
		try ( Connection conn = m_proc.connect();
				Statement stmt = conn.createStatement() ) {
			// URL의 DB_CLOSE_DELAY 설정을 적용하려면 H2에서 관리자 권한이 필요하다.
			stmt.executeUpdate("create user tester password 'a+b&c' admin");
		}
		JdbcConfiguration config = JdbcConfiguration.parse(m_url + "?user=tester&password=a+b%26c");
		Assertions.assertEquals(m_url, config.getJdbcUrl());
		Assertions.assertEquals("a+b&c", config.getPassword());

		JdbcProcessor proc = JdbcProcessor.builder(config).build();
		Assertions.assertEquals(3L, proc.countRows("item"));

		JdbcConfiguration wrong = JdbcConfiguration.parse(m_url + "?user=tester&password=a%20b%26c");
		Assertions.assertThrows(SQLException.class, () -> JdbcProcessor.builder(wrong).build().countRows("item"));
	}

	/**
	 * Statement를 먼저 닫아 원본 ResultSet까지 닫힌 뒤에 반환된 ResultSet을 닫아도 연결이 닫힌다.
	 */
	@Test
	public void executeQueryClosesConnectionEvenIfStatementClosedFirst() throws SQLException {
		JdbcProcessor direct = JdbcProcessor.builder().jdbcUrl(m_url).user("sa").password("").build();

		ResultSet rs = direct.executeQuery("select 1");
		Statement stmt = rs.getStatement();
		Connection conn = stmt.getConnection();
		stmt.close();
		rs.close();
		Assertions.assertTrue(conn.isClosed());

		ResultSet rs2 = direct.executeQuery("select 1", s -> s.setFetchSize(10));
		Statement stmt2 = rs2.getStatement();
		Connection conn2 = stmt2.getConnection();
		stmt2.close();
		rs2.close();
		Assertions.assertTrue(conn2.isClosed());
	}

	/**
	 * 질의 끝에 행 주석이 있어도 {@code countQueryRows}가 감싼 질의의 닫는 괄호가 주석에 묻히지 않는다.
	 */
	@Test
	public void countQueryRowsAcceptsTrailingLineComment() throws SQLException {
		Assertions.assertEquals(3L, m_proc.countQueryRows("select id from item -- trailing comment"));
		Assertions.assertEquals(1L, m_proc.countQueryRows("select id from item where id = 1 -- comment ;"));
		Assertions.assertEquals(1L, m_proc.countQueryRows("select id from item where id = 1 /* comment */;"));
	}

	/**
	 * 파라미터 바인딩 질의는 결과를 돌려주고, 바인딩 함수의 예외를 감싸지 않으며, 실패해도 연결을 돌려준다.
	 * 풀 크기가 1이라 연결이 하나라도 남으면 다음 호출이 풀 고갈로 실패한다.
	 */
	@Test
	@Timeout(30)
	public void queryWithBinder() throws Exception {
		String byId = "select name from item where id = ?";
		Assertions.assertEquals(List.of("b", "c"),
								m_proc.query("select name from item where id > ? order by id",
											pstmt -> pstmt.setInt(1, 1), rs -> rs.getString(1)).toList());
		Assertions.assertEquals("c", m_proc.queryFirst(byId, pstmt -> pstmt.setInt(1, 3), rs -> rs.getString(1))
											.get());
		Assertions.assertTrue(m_proc.queryFirst(byId, pstmt -> pstmt.setInt(1, 9), rs -> rs.getString(1))
									.isAbsent());

		for ( int i = 0; i < 3; ++i ) {
			Assertions.assertThrows(IllegalStateException.class,
									() -> m_proc.query(byId, pstmt -> { throw new IllegalStateException(); },
														rs -> rs.getString(1)));
			// 없는 파라미터 위치
			Assertions.assertThrows(SQLException.class,
									() -> m_proc.queryFirst(byId, pstmt -> pstmt.setInt(2, 1), rs -> rs.getString(1)));
			// 변환 실패는 queryFirst에서 원인 SQLException으로 나온다.
			Assertions.assertThrows(SQLException.class,
									() -> m_proc.queryFirst(byId, pstmt -> pstmt.setInt(1, 1), rs -> rs.getString(5)));
			m_proc.query(byId, pstmt -> pstmt.setInt(1, 1), rs -> rs.getString(1)).close();
		}
		Assertions.assertEquals(3L, m_proc.countRows("item"));
	}
}
