package utils.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


/**
 * {@link JdbcConnectionPools}와 {@link JdbcProcessor}의 연결 풀 사용을 검사한다.
 * <p>
 * 실제 데이터베이스 대신 Mockito 연결을 돌려주는 {@link FakeDriver}를 ClassLoader 경로로 적재해 사용한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcConnectionPoolsTest {
	private static final String URL = "jdbc:fake:testdb";
	private static final String FAIL_URL = "jdbc:fake:fail";

	@BeforeEach
	void setUp() {
		JdbcConnectionPools.closeAll();
		FakeDriver.reset();
	}

	@AfterEach
	void tearDown() {
		JdbcConnectionPools.closeAll();
	}

	@Test
	void pooledProcessorReusesPhysicalConnection() throws SQLException {
		JdbcProcessor jdbc = builder(URL, "user").pooled().build();
		assertTrue(jdbc.isPooled());

		try ( Connection conn = jdbc.connect() ) { }
		try ( Connection conn = jdbc.connect() ) { }

		assertEquals(1, FakeDriver.s_connectCount.get());
		assertEquals(0, FakeDriver.s_closed.size());
		assertEquals(1, JdbcConnectionPools.size());
	}

	@Test
	void processorsWithSameConnectionInfoSharePool() throws SQLException {
		JdbcProcessor jdbc1 = builder(URL, "user").pooled().build();
		JdbcProcessor jdbc2 = builder(URL, "user").pooled().build();

		try ( Connection conn = jdbc1.connect() ) { }
		try ( Connection conn = jdbc2.connect() ) { }

		assertEquals(1, FakeDriver.s_connectCount.get());
		assertEquals(1, JdbcConnectionPools.size());
	}

	@Test
	void differentConnectionInfoUsesDifferentPool() throws SQLException {
		JdbcProcessor jdbc1 = builder(URL, "user1").pooled().build();
		JdbcProcessor jdbc2 = builder(URL, "user2").pooled().build();

		try ( Connection conn = jdbc1.connect() ) { }
		try ( Connection conn = jdbc2.connect() ) { }

		assertEquals(2, FakeDriver.s_connectCount.get());
		assertEquals(2, JdbcConnectionPools.size());
	}

	@Test
	void nonPooledProcessorConnectsEachTimeWithoutRegisteringDriver() throws SQLException {
		JdbcProcessor jdbc = builder(URL, "user").build();
		assertFalse(jdbc.isPooled());
		assertEquals(0, jdbc.getMaxPoolSize());

		try ( Connection conn = jdbc.connect() ) { }
		try ( Connection conn = jdbc.connect() ) { }

		assertEquals(2, FakeDriver.s_connectCount.get());
		assertEquals(2, FakeDriver.s_closed.size());
		// 드라이버는 한 번만 인스턴스화되고, DriverManager에는 등록되지 않는다.
		assertEquals(1, FakeDriver.s_instanceCount.get());
		assertTrue(Collections.list(DriverManager.getDrivers()).stream()
								.noneMatch(d -> d instanceof DriverDelegate || d instanceof FakeDriver));
		assertEquals(0, JdbcConnectionPools.size());
	}

	@Test
	void nonPooledConnectionFailureKeepsCause() {
		JdbcProcessor jdbc = builder(FAIL_URL, "user").build();
		JdbcException error = assertThrows(JdbcException.class, jdbc::connect);
		assertEquals(FakeDriver.FAILURE_MESSAGE, error.getCause().getMessage());
	}

	@Test
	void maxPoolSizeResolution() {
		assertEquals(JdbcConnectionPools.DEFAULT_MAX_POOL_SIZE, builder(URL, "user").pooled().build().getMaxPoolSize());
		assertEquals(7, builder(URL, "user").pooled(7).build().getMaxPoolSize());
		assertThrows(IllegalArgumentException.class, () -> builder(URL, "user").pooled(0));

		JdbcConfiguration conf = new JdbcConfiguration();
		conf.setJdbcUrl(URL);
		conf.setUser("user");
		conf.setPassword("passwd");
		conf.setDriverClassName(FakeDriver.class.getName());
		conf.setMaxPoolSize(5);
		assertEquals(5, JdbcProcessor.builder(conf).classLoader(getClass().getClassLoader())
												.pooled().build().getMaxPoolSize());
		assertEquals(2, JdbcProcessor.builder(conf).classLoader(getClass().getClassLoader())
												.pooled(2).build().getMaxPoolSize());
		// maxPoolSize만으로는 풀을 사용하지 않는다.
		assertFalse(JdbcProcessor.builder(conf).classLoader(getClass().getClassLoader()).build().isPooled());

		String fullUrl = URL + "?user=user&password=passwd&maxPoolSize=6";
		assertEquals(6, JdbcProcessor.builderFromFullJdbcUrl(fullUrl).driverClassName(FakeDriver.class.getName())
											.pooled().build().getMaxPoolSize());
	}

	@Test
	void closeAllClosesPoolsAndAllowsRecreation() throws SQLException {
		JdbcProcessor jdbc = builder(URL, "user").pooled().build();
		try ( Connection conn = jdbc.connect() ) { }

		JdbcConnectionPools.closeAll();
		assertEquals(0, JdbcConnectionPools.size());
		assertEquals(1, FakeDriver.s_closed.size());

		// 풀이 닫힌 뒤 연결을 요청하면 새 풀이 만들어진다.
		try ( Connection conn = jdbc.connect() ) { }
		assertEquals(2, FakeDriver.s_connectCount.get());
		assertEquals(1, JdbcConnectionPools.size());
	}

	@Test
	@Timeout(30)
	void exhaustedPoolTimesOut() throws SQLException {
		JdbcProcessor jdbc = builder(URL, "user").pooled(1).build();
		try ( Connection held = jdbc.connect() ) {
			assertThrows(SQLException.class, jdbc::connect);
		}
		// 반환된 연결은 다시 쓸 수 있다.
		try ( Connection conn = jdbc.connect() ) { }
		assertEquals(1, FakeDriver.s_connectCount.get());
	}

	@Test
	@Timeout(30)
	void connectionFailureIsReportedAsCause() {
		JdbcProcessor jdbc = builder(FAIL_URL, "user").pooled().build();
		SQLException error = assertThrows(SQLException.class, jdbc::connect);

		Throwable cause = error;
		while ( cause != null && !FakeDriver.FAILURE_MESSAGE.equals(cause.getMessage()) ) {
			cause = cause.getCause();
		}
		assertTrue(cause != null, "the driver failure is not reported: " + error);
	}

	@Test
	void processorsWithDifferentPoolSizeSharePool() throws SQLException {
		JdbcProcessor jdbc1 = builder(URL, "user").pooled(1).build();
		JdbcProcessor jdbc2 = builder(URL, "user").pooled(4).build();
		try ( Connection conn = jdbc1.connect() ) { }
		try ( Connection conn = jdbc2.connect() ) { }

		assertEquals(1, FakeDriver.s_connectCount.get());
		assertEquals(1, JdbcConnectionPools.size());
	}

	private JdbcProcessor.Builder builder(String url, String user) {
		return JdbcProcessor.builder()
							.jdbcUrl(url)
							.user(user)
							.password("passwd")
							.driverClassName(FakeDriver.class.getName())
							.classLoader(getClass().getClassLoader());
	}

	/**
	 * 연결 생성·종료 횟수를 세는 가짜 JDBC 드라이버.
	 */
	public static class FakeDriver implements Driver {
		static final String FAILURE_MESSAGE = "fake connection failure";

		static final AtomicInteger s_instanceCount = new AtomicInteger();
		static final AtomicInteger s_connectCount = new AtomicInteger();
		/** 닫힌 물리 연결의 수. 같은 연결을 여러 번 닫아도 한 번으로 센다. */
		static final Set<Connection> s_closed = ConcurrentHashMap.newKeySet();

		static void reset() {
			s_instanceCount.set(0);
			s_connectCount.set(0);
			s_closed.clear();
		}

		public FakeDriver() {
			s_instanceCount.incrementAndGet();
		}

		@Override
		public Connection connect(String url, Properties info) throws SQLException {
			if ( !acceptsURL(url) ) {
				return null;
			}
			if ( url.equals(FAIL_URL) ) {
				throw new SQLException(FAILURE_MESSAGE);
			}

			s_connectCount.incrementAndGet();
			Connection conn = mock(Connection.class);
			when(conn.isValid(anyInt())).thenReturn(true);
			when(conn.getAutoCommit()).thenReturn(true);
			doAnswer(inv -> s_closed.add(conn)).when(conn).close();
			return conn;
		}

		@Override
		public boolean acceptsURL(String url) {
			return url.startsWith("jdbc:fake:");
		}

		@Override
		public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
			return new DriverPropertyInfo[0];
		}

		@Override
		public int getMajorVersion() {
			return 1;
		}

		@Override
		public int getMinorVersion() {
			return 0;
		}

		@Override
		public boolean jdbcCompliant() {
			return false;
		}

		@Override
		public Logger getParentLogger() {
			return Logger.getGlobal();
		}
	}
}
