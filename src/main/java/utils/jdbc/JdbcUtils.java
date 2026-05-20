package utils.jdbc;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import utils.CallHandler;
import utils.Preconditions;
import utils.ProxyUtils;
import utils.func.CheckedFunctionX;
import utils.io.IOUtils;
import utils.stream.FStream;

import net.sf.cglib.proxy.MethodProxy;


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
	 * 생성된 {@link FStream} 객체가 close() 될 때, 주어진 ResultSet 객체도 함께 close()된다.
	 *
	 * @param <T>	각 행을 변환한 결과 객체의 타입.
	 * @param rs	대상 ResultSet 객체.
	 * @param trans	{@link ResultSet}의 한 행을 {@code T} 타입 객체로 변환하는 함수.
	 * @return		변환된 객체들의 {@link FStream}.
	 * @throws SQLException	ResultSet 순회·변환 준비 중 오류가 발생한 경우.
	 */
	public static <T> FStream<T> fstream(ResultSet rs,
										CheckedFunctionX<ResultSet,T,SQLException> trans)
		throws SQLException {
		return FStream.from(new JdbcObjectIterator<T>(rs, trans));
	}

	/**
	 * 주어진 ResultSet 객체에 대해 close() 메소드가 호출될 때, 해당 ResultSet 객체가
	 * 참조하는 Connection 객체도 함께 close() 되도록 하는 Proxy 객체를 생성하여 반환한다.
	 *
	 * @param rset	대상 ResultSet 객체.
	 * @return	{@link Connection}을 close() 시키는 동작이 포함된 ResultSet proxy 객체.
	 */
	public static ResultSet bindToConnection(ResultSet rset) {
		AtomicBoolean closed = new AtomicBoolean(false);
		CallHandler replacer = new CallHandler() {
			@Override
			public boolean test(Method method) {
				return method.getName().equals("close")
					&& method.getDeclaringClass() == ResultSet.class;
			}

			@Override
			public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
				throws Throwable {
				if ( !closed.compareAndSet(false, true) ) {
					return null;
				}

				Statement stmt = null;
				Connection conn = null;
				try {
					// proxy의 close()를 다시 부르지 않기 위해 원본 rset을 직접 close한다.
					stmt = rset.getStatement();
					if ( stmt != null ) {
						conn = stmt.getConnection();
					}
				}
				catch ( Throwable ignored ) { }

				IOUtils.closeQuietly(rset, stmt, conn);

				return null;
			}
		};

		return (ResultSet)ProxyUtils.replaceAction(rset, replacer);
	}
	
	/**
	 * 주어진 {@link Duration}을 PostgreSQL의 interval 문자열 표기로 변환한다.
	 * <p>
	 * 결과는 {@code "[D days] [H hours] [M minutes] [S seconds]"} 형식이며,
	 * 0인 단위는 생략된다. 단, 모든 단위가 0인 경우는 {@code "0 seconds"}가 반환된다.
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
		if ( secs > 0 || sb.length() == 0 ) {
			sb.append(secs).append(" seconds");
		}
		
		return sb.toString().trim();	
	}
}
