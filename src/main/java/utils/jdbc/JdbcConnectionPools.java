package utils.jdbc;

import java.io.File;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


/**
 * JVM 전역에서 공유하는 JDBC 연결 풀 레지스트리.
 * <p>
 * {@link JdbcProcessor.Builder#pooled()}로 생성한 {@link JdbcProcessor}는 연결 정보(JDBC URL, 사용자,
 * 패스워드, 드라이버 클래스, 드라이버 적재용 ClassLoader 또는 jar 파일)가 같으면 같은 풀을 공유한다.
 * 따라서 호출할 때마다 {@code JdbcProcessor}를 새로 만들어도 물리 연결은 재사용된다.
 * <p>
 * 풀은 연결 정보별로 처음 연결을 요청할 때 만들어지며, 최대 크기는 그 풀을 처음 만든 processor의 값을 따른다.
 * 풀은 유휴 연결을 미리 만들어 두지 않고({@code minimumIdle=0}), 일정 시간({@value #IDLE_TIMEOUT_MILLIS}ms)
 * 쓰이지 않은 연결은 닫는다. 따라서 사용하지 않는 동안에는 데이터베이스 연결을 점유하지 않는다.
 * 풀의 연결이 모두 사용 중이면 연결 요청은 최대 {@value #CONNECTION_TIMEOUT_MILLIS}ms 동안 기다린 뒤
 * {@link SQLException}으로 실패한다. 데이터베이스에 접속할 수 없는 경우도 같은 시간만큼 기다린 뒤 실패한다.
 * <p>
 * 등록된 풀은 {@link #closeAll()}로 닫을 수 있으며, JVM 종료 시에도 자동으로 닫힌다.
 * 풀에서 얻은 연결을 {@link Connection#close()}하면 연결은 닫히지 않고 풀로 반환된다. 반환 시
 * auto-commit, read-only, transaction isolation, catalog, schema는 원래 값으로 복원되지만,
 * 그 밖의 세션 상태(예: {@code SET} 문으로 바꾼 세션 변수)는 복원되지 않으므로 다음 사용자에게 남는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class JdbcConnectionPools {
	private static final Logger s_logger = LoggerFactory.getLogger(JdbcConnectionPools.class);

	/** 풀 크기가 지정되지 않은 경우에 사용하는 최대 풀 크기. */
	public static final int DEFAULT_MAX_POOL_SIZE = 3;
	/** 풀에서 연결을 얻기 위해 기다리는 최대 시간(ms). */
	public static final long CONNECTION_TIMEOUT_MILLIS = 5_000;
	/** 쓰이지 않는 연결을 닫기까지의 시간(ms). */
	public static final long IDLE_TIMEOUT_MILLIS = 60_000;

	private static final Map<PoolKey,HikariDataSource> s_pools = new HashMap<>();
	private static final AtomicInteger s_poolSeqNo = new AtomicInteger(0);
	private static boolean s_shutdownHookRegistered = false;

	private JdbcConnectionPools() {
		throw new AssertionError("Should not be called: class=" + JdbcConnectionPools.class);
	}

	/**
	 * 현재 등록된(닫히지 않은) 풀의 개수를 반환한다.
	 *
	 * @return	풀의 개수.
	 */
	public static int size() {
		synchronized ( s_pools ) {
			return s_pools.size();
		}
	}

	/**
	 * 등록된 모든 풀을 닫고 레지스트리에서 제거한다.
	 * <p>
	 * 풀을 닫으면 그 풀의 연결도 모두 닫힌다. 이후 pooled {@link JdbcProcessor}에서 연결을 요청하면
	 * 새로운 풀이 만들어진다.
	 */
	public static void closeAll() {
		List<HikariDataSource> pools;
		synchronized ( s_pools ) {
			pools = new ArrayList<>(s_pools.values());
			s_pools.clear();
		}
		for ( HikariDataSource pool: pools ) {
			try {
				pool.close();
			}
			catch ( Exception e ) {
				s_logger.warn("failed to close JDBC connection pool: name={}, cause={}", pool.getPoolName(), "" + e);
			}
		}
	}

	/**
	 * 주어진 processor의 연결 정보에 해당하는 풀을 반환한다. 없으면 새로 만들어 등록한다.
	 *
	 * @param jdbc			연결 정보를 담은 processor. 풀은 이 processor의 직접 연결 기능으로 연결을 만든다.
	 * @param key			연결 정보 키.
	 * @param maxPoolSize	풀을 새로 만드는 경우의 최대 크기.
	 * @return	풀.
	 */
	static HikariDataSource getOrCreate(JdbcProcessor jdbc, PoolKey key, int maxPoolSize) {
		synchronized ( s_pools ) {
			HikariDataSource pool = s_pools.get(key);
			if ( pool == null ) {
				pool = createPool(jdbc, maxPoolSize);
				s_pools.put(key, pool);
				registerShutdownHookIfNeeded();
			}
			else if ( pool.getMaximumPoolSize() != maxPoolSize && s_logger.isDebugEnabled() ) {
				s_logger.debug("reuse the existing JDBC connection pool with a different size: name={}, size={}, "
								+ "requested={}", pool.getPoolName(), pool.getMaximumPoolSize(), maxPoolSize);
			}
			return pool;
		}
	}

	private static HikariDataSource createPool(JdbcProcessor jdbc, int maxPoolSize) {
		String poolName = "jdbc-pool-" + s_poolSeqNo.incrementAndGet();

		HikariConfig conf = new HikariConfig();
		conf.setPoolName(poolName);
		conf.setDataSource(new DirectConnectDataSource(jdbc));
		conf.setMaximumPoolSize(maxPoolSize);
		conf.setMinimumIdle(0);
		conf.setIdleTimeout(IDLE_TIMEOUT_MILLIS);
		conf.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
		// 풀을 만들 때 데이터베이스에 접속하지 않는다. 접속 실패는 연결을 요청할 때 보고된다.
		conf.setInitializationFailTimeout(-1);

		HikariDataSource pool = new HikariDataSource(conf);
		// 비밀번호는 출력하지 않는다.
		s_logger.info("created JDBC connection pool: name={}, url={}, user={}, maxSize={}",
						poolName, JdbcUtils.maskPassword(jdbc.getJdbcUrl()), jdbc.getUser(), maxPoolSize);
		return pool;
	}

	private static void registerShutdownHookIfNeeded() {
		if ( !s_shutdownHookRegistered ) {
			Runtime.getRuntime().addShutdownHook(new Thread(JdbcConnectionPools::closeAll, "jdbc-pools-shutdown"));
			s_shutdownHookRegistered = true;
		}
	}

	/**
	 * 풀을 식별하는 연결 정보.
	 * <p>
	 * 비밀번호를 포함하므로 {@link #toString()}은 비밀번호를 출력하지 않는다.
	 */
	static final class PoolKey {
		private final String m_jdbcUrl;
		private final String m_user;
		private final String m_password;
		private final String m_driverClassName;
		private final @Nullable ClassLoader m_classLoader;
		private final @Nullable String m_jarPath;

		PoolKey(String jdbcUrl, String user, String password, String driverClassName,
				@Nullable ClassLoader classLoader, @Nullable File jarFile) {
			m_jdbcUrl = jdbcUrl;
			m_user = user;
			m_password = password;
			m_driverClassName = driverClassName;
			m_classLoader = classLoader;
			// ClassLoader가 명시된 경우는 jar 파일을 쓰지 않는다.
			m_jarPath = (classLoader == null && jarFile != null) ? jarFile.getAbsolutePath() : null;
		}

		@Override
		public boolean equals(Object obj) {
			if ( this == obj ) {
				return true;
			}
			if ( !(obj instanceof PoolKey other) ) {
				return false;
			}
			return m_jdbcUrl.equals(other.m_jdbcUrl)
				&& m_user.equals(other.m_user)
				&& m_password.equals(other.m_password)
				&& m_driverClassName.equals(other.m_driverClassName)
				&& m_classLoader == other.m_classLoader
				&& Objects.equals(m_jarPath, other.m_jarPath);
		}

		@Override
		public int hashCode() {
			return Objects.hash(m_jdbcUrl, m_user, m_password, m_driverClassName,
								System.identityHashCode(m_classLoader), m_jarPath);
		}

		@Override
		public String toString() {
			return String.format("%s?user=%s,driver=%s", JdbcUtils.maskPassword(m_jdbcUrl), m_user,
								m_driverClassName);
		}
	}

	/**
	 * 풀이 새 물리 연결을 만들 때 사용하는 {@link DataSource}.
	 * <p>
	 * {@link JdbcProcessor}의 직접 연결 기능을 그대로 사용하므로, 드라이버 적재 방식(jar 파일, ClassLoader)은
	 * 풀을 쓰지 않는 경우와 같다.
	 */
	private static final class DirectConnectDataSource implements DataSource {
		private final JdbcProcessor m_jdbc;
		private volatile int m_loginTimeout = 0;

		DirectConnectDataSource(JdbcProcessor jdbc) {
			m_jdbc = jdbc;
		}

		@Override
		public Connection getConnection() throws SQLException {
			try {
				return m_jdbc.connectDirect();
			}
			catch ( JdbcException e ) {
				// 풀은 SQLException인 실패를 연결 요청 시한 초과 예외의 원인으로 붙여 보고한다.
				throw (e.getCause() instanceof SQLException cause) ? cause : new SQLException(e.getMessage(), e);
			}
		}

		@Override
		public Connection getConnection(String username, String password) throws SQLException {
			throw new SQLFeatureNotSupportedException("getConnection(username, password)");
		}

		@Override
		public PrintWriter getLogWriter() {
			return null;
		}

		@Override
		public void setLogWriter(PrintWriter out) { }

		@Override
		public void setLoginTimeout(int seconds) {
			m_loginTimeout = seconds;
		}

		@Override
		public int getLoginTimeout() {
			return m_loginTimeout;
		}

		@Override
		public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
			throw new SQLFeatureNotSupportedException("getParentLogger");
		}

		@Override
		public <T> T unwrap(Class<T> iface) throws SQLException {
			if ( iface.isInstance(this) ) {
				return iface.cast(this);
			}
			throw new SQLException("not a wrapper for " + iface);
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) {
			return iface.isInstance(this);
		}
	}
}
