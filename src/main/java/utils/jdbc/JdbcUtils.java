package utils.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.func.CheckedFunctionX;
import utils.io.IOUtils;
import utils.stream.FStream;



/**
 * JDBC 관련 유틸리티 메서드를 모아둔 클래스.
 * <p>
 * {@link ResultSet} 변환·스트림화, Connection-Statement-ResultSet 자원 자동 정리,
 * PostgreSQL interval 문자열 변환 등 JDBC 작업 시 반복적으로 필요한 헬퍼를 제공한다.
 * 인스턴스화는 불가능하며 모든 메서드는 정적 메서드이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class JdbcUtils {
	private JdbcUtils() {
		throw new AssertionError("Should not be called: " + JdbcUtils.class);
	}

	// URL 파라미터 형식의 비밀번호: ?password=xxx, &pwd=xxx, ;password=xxx (SQL Server 등)
	private static final Pattern PASSWORD_PARAM
					= Pattern.compile("([?&;](?:password|passwd|pwd)=)[^&;]*", Pattern.CASE_INSENSITIVE);
	// 사용자 정보 형식의 비밀번호: //user:secret@host
	private static final Pattern PASSWORD_USERINFO = Pattern.compile("(//[^/:@]+:)[^@/]*@");
	private static final String MASK = "****";

	/**
	 * JDBC URL에 포함된 비밀번호를 가린 문자열을 반환한다.
	 * <p>
	 * 로그나 예외 메시지에 JDBC URL을 출력할 때 사용한다. {@code password=}·{@code passwd=}·{@code pwd=}
	 * 파라미터(구분자 {@code ?}, {@code &}, {@code ;})와 {@code //user:password@host} 형식의 비밀번호를
	 * {@value #MASK}로 바꾼다. 그 밖의 부분은 그대로 둔다.
	 *
	 * @param jdbcUrl	JDBC URL.
	 * @return	비밀번호를 가린 URL. {@code jdbcUrl}이 {@code null}이면 {@code null}.
	 */
	public static String maskPassword(String jdbcUrl) {
		if ( jdbcUrl == null ) {
			return null;
		}
		String masked = PASSWORD_PARAM.matcher(jdbcUrl).replaceAll("$1" + MASK);
		return PASSWORD_USERINFO.matcher(masked).replaceAll("$1" + MASK + "@");
	}

	/**
	 * 주어진 Instant 객체를 Timestamp 객체로 변환한다.
	 *
	 * @param instant	변환할 Instant 객체.
	 * @return	변환된 Timestamp 객체.
	 */
	public static Timestamp toTimestamp(Instant instant) {
		return Timestamp.from(instant);
	}
	
	/**
	 * 주어진 {@link ResultSet}의 현재 행에 있는 모든 컬럼 값을 컬럼 순서대로 리스트로 반환한다.
	 * <p>
	 * 컬럼 인덱스 1부터 메타데이터의 컬럼 개수까지 순회하며,
	 * {@link ResultSet#getObject(int)}로 각 컬럼 값을 읽어 리스트에 담는다.
	 *
	 * @param rs	대상 ResultSet 객체. 현재 행이 유효한 상태여야 한다.
	 * @return		현재 행의 컬럼 값들을 컬럼 순서대로 담은 리스트.
	 * @throws SQLException	메타데이터 조회 또는 컬럼 값 읽기 중 오류가 발생한 경우.
	 */
	public static List<Object> toColumnObjectList(ResultSet rs) throws SQLException {
		return FStream.range(1, rs.getMetaData().getColumnCount()+1)
						.mapOrThrow(idx -> rs.getObject(idx))
						.toList();
	}

	/**
	 * 주어진 {@link ResultSet}의 각 행을 {@code trans} 함수로 변환하여 {@link FStream}으로 반환한다.
	 * <p>
	 * 생성된 {@link FStream} 객체가 close() 될 때, 주어진 ResultSet 객체도 함께 close()된다. 종단 연산({@code toList()}
	 * 등)은 스트림을 닫지만, {@code next()}로 끝까지 읽은 것만으로는 닫히지 않는다.
	 * <p>
	 * 행은 스트림을 읽을 때 지연해서 읽으며, 행 읽기나 변환이 {@link SQLException}으로 실패하면 {@link JdbcException}으로
	 * 감싸 던진다.
	 *
	 * @param <T>	각 행을 변환한 결과 객체의 타입.
	 * @param rs	대상 ResultSet 객체.
	 * @param trans	{@link ResultSet}의 한 행을 {@code T} 타입 객체로 변환하는 함수.
	 * @return		변환된 객체들의 {@link FStream}.
	 * @throws SQLException	지금은 던지지 않는다. 호출처와의 호환을 위해 선언만 남겨 두었다.
	 */
	public static <T> FStream<T> fstream(ResultSet rs,
										CheckedFunctionX<ResultSet,T,SQLException> trans)
		throws SQLException {
		return new ResultSetObjectFStream<>(rs, trans);
	}

	/**
	 * 주어진 ResultSet 객체에 대해 close() 메소드가 호출될 때, 해당 ResultSet 객체가
	 * 참조하는 Statement와 Connection 객체도 함께 close() 되도록 하는 Proxy 객체를 생성하여 반환한다.
	 * <p>
	 * 닫을 Statement와 Connection은 이 메소드를 호출할 때 {@link ResultSet#getStatement()}와
	 * {@link Statement#getConnection()}으로 확보해 둔다. 그래서 원본 ResultSet이나 Statement가 먼저 닫혀도
	 * 프록시의 {@code close()}는 연결까지 닫는다. 그 밖의 동작은
	 * {@link #bindToConnection(ResultSet, Statement, Connection)}과 같다.
	 *
	 * @param rset	대상 ResultSet 객체. 열려 있어야 한다.
	 * @return	{@link Connection}을 close() 시키는 동작이 포함된 ResultSet proxy 객체.
	 * @throws IllegalArgumentException	{@code rset}이 {@code null}인 경우.
	 * @throws SQLException	Statement나 Connection을 얻지 못한 경우(예: {@code rset}이 이미 닫힌 경우).
	 */
	public static ResultSet bindToConnection(ResultSet rset) throws SQLException {
		Preconditions.checkNotNullArgument(rset, "ResultSet is null");

		Statement stmt = rset.getStatement();
		Connection conn = (stmt != null) ? stmt.getConnection() : null;
		return bindToConnection(rset, stmt, conn);
	}

	/**
	 * 주어진 ResultSet 객체에 대해 close() 메소드가 호출될 때, 주어진 Statement와 Connection도 함께
	 * close() 되도록 하는 Proxy 객체를 생성하여 반환한다.
	 * <p>
	 * 반환되는 객체는 {@link ResultSet} 인터페이스만 구현하는 JDK 동적 프록시다. 프록시 클래스는 한 번만 만들어
	 * 재사용된다. {@code close()} 외의 호출은 원본에 그대로 위임하며, {@code close()}는 처음 한 번만 원본 ResultSet,
	 * {@code stmt}, {@code conn}을 차례로 닫는다(닫기 오류는 무시). 닫을 객체를 미리 받으므로 원본 ResultSet이나
	 * Statement가 먼저 닫혀도 연결은 닫힌다. 드라이버의 구체 클래스가 필요하면 {@link ResultSet#unwrap(Class)}를
	 * 쓴다.
	 *
	 * @param rset	대상 ResultSet 객체.
	 * @param stmt	함께 닫을 Statement. {@code null}이면 닫지 않는다.
	 * @param conn	함께 닫을 Connection. {@code null}이면 닫지 않는다.
	 * @return	{@code stmt}와 {@code conn}을 close() 시키는 동작이 포함된 ResultSet proxy 객체.
	 * @throws IllegalArgumentException	{@code rset}이 {@code null}인 경우.
	 */
	public static ResultSet bindToConnection(ResultSet rset, @Nullable Statement stmt,
											@Nullable Connection conn) {
		Preconditions.checkNotNullArgument(rset, "ResultSet is null");

		return (ResultSet)Proxy.newProxyInstance(JdbcUtils.class.getClassLoader(), new Class<?>[]{ ResultSet.class },
												new ConnectionBoundHandler(rset, stmt, conn));
	}

	// close() 때 ResultSet과 Statement·Connection을 한 번만 닫고, 나머지 호출은 원본에 위임한다.
	private static final class ConnectionBoundHandler implements InvocationHandler {
		private final ResultSet m_rset;
		private final @Nullable Statement m_stmt;
		private final @Nullable Connection m_conn;
		private final AtomicBoolean m_closed = new AtomicBoolean(false);

		ConnectionBoundHandler(ResultSet rset, @Nullable Statement stmt, @Nullable Connection conn) {
			m_rset = rset;
			m_stmt = stmt;
			m_conn = conn;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String name = method.getName();
			int nargs = (args != null) ? args.length : 0;
			if ( name.equals("close") && nargs == 0 ) {
				if ( m_closed.compareAndSet(false, true) ) {
					IOUtils.closeQuietly(m_rset, m_stmt, m_conn);
				}
				return null;
			}
			else if ( name.equals("equals") && nargs == 1 && method.getDeclaringClass() == Object.class ) {
				return proxy == args[0];
			}
			else if ( name.equals("hashCode") && nargs == 0 ) {
				return System.identityHashCode(proxy);
			}
			else if ( name.equals("toString") && nargs == 0 ) {
				return "ConnectionBoundResultSet[" + m_rset + "]";
			}

			try {
				return method.invoke(m_rset, args);
			}
			catch ( InvocationTargetException e ) {
				throw e.getCause();
			}
		}
	}
	
	/**
	 * 주어진 {@link Duration}을 PostgreSQL의 interval 문자열 표기로 변환한다.
	 * <p>
	 * 결과는 {@code "[D days] [H hours] [M minutes] [S seconds]"} 형식이며,
	 * 0인 단위는 생략된다. 단, 모든 단위가 0인 경우는 {@code "0 seconds"}가 반환된다.
	 * 초 미만 값은 버리지 않고 소수 초로 표기한다(예: 1500ms → {@code "1.5 seconds"}, 500ms →
	 * {@code "0.5 seconds"}). PostgreSQL interval의 정밀도는 마이크로초이므로 그보다 작은 값은 PostgreSQL이 반올림한다.
	 * 음수 Duration은 지원하지 않는다.
	 *
	 * @param dur	변환할 Duration 객체. {@code null}이면 안 되며, 음수가 아니어야 한다.
	 * @return		PostgreSQL interval 문자열.
	 * @throws IllegalArgumentException	{@code dur}가 {@code null}이거나 음수인 경우.
	 */
	public static String toPostgresInterval(Duration dur) {
		Preconditions.checkNotNullArgument(dur, "Duration is null");
		Preconditions.checkArgument(!dur.isNegative(), "Duration must be non-negative: %s", dur);

		long seconds = dur.getSeconds();
		int nanos = dur.getNano();
		long days = seconds / 86400;
		long hours = (seconds % 86400) / 3600;
		long minutes = (seconds % 3600) / 60;
		long secs = seconds % 60;
		
		StringBuilder sb = new StringBuilder();
		if ( days > 0 ) {
			sb.append(days).append(" days ");
		}
		if ( hours > 0 ) {
			sb.append(hours).append(" hours ");
		}
		if ( minutes > 0 ) {
			sb.append(minutes).append(" minutes ");
		}
		if ( nanos > 0 ) {
			BigDecimal fraction = BigDecimal.valueOf(secs).add(BigDecimal.valueOf(nanos, 9));
			sb.append(fraction.stripTrailingZeros().toPlainString()).append(" seconds");
		}
		else if ( secs > 0 || sb.length() == 0 ) {
			sb.append(secs).append(" seconds");
		}
		
		return sb.toString().trim();	
	}
}
