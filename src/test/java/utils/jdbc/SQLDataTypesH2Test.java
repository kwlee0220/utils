package utils.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * 인메모리 H2 DB로 {@link SQLDataTypes} 구현들의 값·{@code NULL} 왕복을 확인한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SQLDataTypesH2Test {
	private Connection m_conn;

	@BeforeEach
	public void setUp() throws SQLException {
		m_conn = java.sql.DriverManager.getConnection("jdbc:h2:mem:types-" + UUID.randomUUID(), "sa", "");
		try ( Statement stmt = m_conn.createStatement() ) {
			stmt.executeUpdate("create table t (id int primary key, b boolean, s varchar(20), sh smallint, i int, "
								+ "l bigint, f real, d double precision, ts timestamp, dt date, tm time, dur varchar(40), "
								+ "bin varbinary(10), dec decimal(38,18), tstz timestamp with time zone, cl clob, bl blob)");
		}
	}

	@AfterEach
	public void tearDown() throws SQLException {
		m_conn.close();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object roundTrip(SQLDataType type, String column, Object value) throws SQLException {
		try ( Statement stmt = m_conn.createStatement() ) {
			stmt.executeUpdate("delete from t");
		}
		try ( PreparedStatement pstmt = m_conn.prepareStatement("insert into t (id, " + column + ") values (1, ?)") ) {
			type.write(pstmt, 1, value);
			pstmt.executeUpdate();
		}
		try ( Statement stmt = m_conn.createStatement();
				ResultSet rs = stmt.executeQuery("select " + column + " from t") ) {
			Assertions.assertTrue(rs.next());
			return type.read(rs, 1);
		}
	}

	@Test
	public void nullRoundTripsForEveryType() throws SQLException {
		List<Object[]> cases = List.of(
			new Object[]{ SQLDataTypes.BOOLEAN, "b" }, new Object[]{ SQLDataTypes.STRING, "s" },
			new Object[]{ SQLDataTypes.SHORT, "sh" }, new Object[]{ SQLDataTypes.INTEGER, "i" },
			new Object[]{ SQLDataTypes.LONG, "l" }, new Object[]{ SQLDataTypes.FLOAT, "f" },
			new Object[]{ SQLDataTypes.DOUBLE, "d" }, new Object[]{ SQLDataTypes.INSTANT, "ts" },
			new Object[]{ SQLDataTypes.DATE, "dt" }, new Object[]{ SQLDataTypes.TIME, "tm" },
			new Object[]{ SQLDataTypes.DURATION, "dur" }, new Object[]{ SQLDataTypes.BINARY, "bin" },
			new Object[]{ SQLDataTypes.DECIMAL, "dec" });
		for ( Object[] c: cases ) {
			Assertions.assertNull(roundTrip((SQLDataType<?>)c[0], (String)c[1], null), "type=" + c[1]);
		}
	}

	@Test
	public void valuesRoundTrip() throws SQLException {
		Assertions.assertEquals(false, roundTrip(SQLDataTypes.BOOLEAN, "b", false));
		Assertions.assertEquals("abc", roundTrip(SQLDataTypes.STRING, "s", "abc"));
		Assertions.assertEquals((short)0, roundTrip(SQLDataTypes.SHORT, "sh", (short)0));
		Assertions.assertEquals(0, roundTrip(SQLDataTypes.INTEGER, "i", 0));
		Assertions.assertEquals(42L, roundTrip(SQLDataTypes.LONG, "l", 42L));
		Assertions.assertEquals(1.5f, roundTrip(SQLDataTypes.FLOAT, "f", 1.5f));
		Assertions.assertEquals(2.5, roundTrip(SQLDataTypes.DOUBLE, "d", 2.5));
		Instant now = Instant.parse("2026-11-02T01:02:03Z");
		Assertions.assertEquals(now, roundTrip(SQLDataTypes.INSTANT, "ts", now));
		Assertions.assertEquals(Duration.ofSeconds(90), roundTrip(SQLDataTypes.DURATION, "dur", Duration.ofSeconds(90)));
		Assertions.assertArrayEquals(new byte[]{ 1, 2 }, (byte[])roundTrip(SQLDataTypes.BINARY, "bin", new byte[]{ 1, 2 }));
		Assertions.assertNotNull(roundTrip(SQLDataTypes.DATE, "dt", new Date()));
		Assertions.assertEquals(LocalTime.of(13, 4, 5), roundTrip(SQLDataTypes.TIME, "tm", LocalTime.of(13, 4, 5)));
	}

	/**
	 * {@link SQLDataTypes#TIME}은 {@link LocalTime}으로 주고받으며, JVM 기본 시간대와 상관없이 컬럼의 시각이 그대로
	 * 오간다. {@code TIME} 컬럼은 {@code fromSqlType}·{@code findByClass}로 {@link SQLDataTypes#TIME}을 찾는다.
	 */
	@Test
	public void timeIsLocalTime() throws SQLException {
		LocalTime value = LocalTime.of(23, 59, 58);
		TimeZone saved = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
			Assertions.assertEquals(value, roundTrip(SQLDataTypes.TIME, "tm", value));
			try ( Statement stmt = m_conn.createStatement();
					ResultSet rs = stmt.executeQuery("select cast(tm as varchar), tm from t") ) {
				Assertions.assertTrue(rs.next());
				Assertions.assertEquals("23:59:58", rs.getString(1));
				Assertions.assertSame(SQLDataTypes.TIME, SQLDataTypes.fromSqlType(rs.getMetaData().getColumnType(2)));
			}
		}
		finally {
			TimeZone.setDefault(saved);
		}
		Assertions.assertSame(SQLDataTypes.TIME, SQLDataTypes.findByClass(LocalTime.class).get());
		Assertions.assertEquals(LocalTime.class, SQLDataTypes.TIME.getJavaClass());
	}

	/**
	 * {@code TIMESTAMP WITH TIME ZONE}·{@code CLOB}·{@code BLOB} 컬럼은 각각 {@link SQLDataTypes#INSTANT_TZ}·
	 * {@link SQLDataTypes#STRING}·{@link SQLDataTypes#BINARY}로 다룬다.
	 */
	@Test
	public void timestampWithTimeZoneClobAndBlob() throws SQLException {
		Instant instant = Instant.parse("2026-11-02T01:02:03Z");
		TimeZone saved = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
			Assertions.assertEquals(instant, roundTrip(SQLDataTypes.INSTANT_TZ, "tstz", instant));
		}
		finally {
			TimeZone.setDefault(saved);
		}
		Assertions.assertNull(roundTrip(SQLDataTypes.INSTANT_TZ, "tstz", null));
		Assertions.assertEquals("long text", roundTrip(SQLDataTypes.STRING, "cl", "long text"));
		Assertions.assertArrayEquals(new byte[]{ 3, 4, 5 }, (byte[])roundTrip(SQLDataTypes.BINARY, "bl", new byte[]{ 3, 4, 5 }));

		try ( Statement stmt = m_conn.createStatement();
				ResultSet rs = stmt.executeQuery("select tstz, cl, bl from t") ) {
			java.sql.ResultSetMetaData meta = rs.getMetaData();
			Assertions.assertSame(SQLDataTypes.INSTANT_TZ, SQLDataTypes.fromSqlType(meta.getColumnType(1)));
			Assertions.assertSame(SQLDataTypes.STRING, SQLDataTypes.fromSqlType(meta.getColumnType(2)));
			Assertions.assertSame(SQLDataTypes.BINARY, SQLDataTypes.fromSqlType(meta.getColumnType(3)));
		}
		Assertions.assertSame(SQLDataTypes.STRING, SQLDataTypes.fromSqlType(java.sql.Types.NCLOB));
	}

	/**
	 * 지원하지 않는 타입 코드는 타입 코드와 {@link java.sql.JDBCType} 이름을 담은 {@link IllegalArgumentException}이다.
	 */
	@Test
	public void unsupportedSqlTypeNamesTheType() {
		IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
								() -> SQLDataTypes.fromSqlType(java.sql.Types.ARRAY));
		Assertions.assertEquals("Unsupported SQL type code: 2003 (ARRAY)", e.getMessage());
		e = Assertions.assertThrows(IllegalArgumentException.class, () -> SQLDataTypes.fromSqlType(-12345));
		Assertions.assertEquals("Unsupported SQL type code: -12345 (unknown)", e.getMessage());
	}

	/**
	 * {@code findByClass}는 원시 타입과 {@link java.sql.Date}도 찾는다. Java 값 타입이 아닌 {@link java.sql.Timestamp}·
	 * {@link java.sql.Time}과 모르는 클래스는 빈 값이다.
	 */
	@Test
	public void findByClassAliases() {
		Assertions.assertSame(SQLDataTypes.BOOLEAN, SQLDataTypes.findByClass(boolean.class).get());
		Assertions.assertSame(SQLDataTypes.SHORT, SQLDataTypes.findByClass(short.class).get());
		Assertions.assertSame(SQLDataTypes.INTEGER, SQLDataTypes.findByClass(int.class).get());
		Assertions.assertSame(SQLDataTypes.LONG, SQLDataTypes.findByClass(long.class).get());
		Assertions.assertSame(SQLDataTypes.FLOAT, SQLDataTypes.findByClass(float.class).get());
		Assertions.assertSame(SQLDataTypes.DOUBLE, SQLDataTypes.findByClass(double.class).get());
		Assertions.assertSame(SQLDataTypes.DATE, SQLDataTypes.findByClass(java.sql.Date.class).get());
		Assertions.assertTrue(SQLDataTypes.findByClass(java.sql.Timestamp.class).isAbsent());
		Assertions.assertTrue(SQLDataTypes.findByClass(java.sql.Time.class).isAbsent());
		Assertions.assertTrue(SQLDataTypes.findByClass(Object.class).isAbsent());
		Assertions.assertTrue(SQLDataTypes.findByName("NoSuchType").isAbsent());
		Assertions.assertSame(SQLDataTypes.INSTANT_TZ, SQLDataTypes.findByName("InstantTz").get());
		// Instant의 기본 핸들러는 시간대 없는 TIMESTAMP용이다.
		Assertions.assertSame(SQLDataTypes.INSTANT, SQLDataTypes.findByClass(Instant.class).get());
	}

	/**
	 * {@code NUMERIC}·{@code DECIMAL}은 {@link SQLDataTypes#DECIMAL}({@link BigDecimal})로 다뤄 정밀도를 잃지 않는다.
	 */
	@Test
	public void decimalKeepsPrecision() throws SQLException {
		BigDecimal value = new BigDecimal("12345678901234567890.123456789012345678");
		Assertions.assertEquals(value, roundTrip(SQLDataTypes.DECIMAL, "dec", value));
		Assertions.assertNotEquals(value, new BigDecimal(value.doubleValue()).setScale(18));

		try ( Statement stmt = m_conn.createStatement();
				ResultSet rs = stmt.executeQuery("select dec from t") ) {
			Assertions.assertSame(SQLDataTypes.DECIMAL, SQLDataTypes.fromSqlType(rs.getMetaData().getColumnType(1)));
		}
		Assertions.assertSame(SQLDataTypes.DECIMAL, SQLDataTypes.fromSqlType(java.sql.Types.NUMERIC));
		Assertions.assertSame(SQLDataTypes.DECIMAL, SQLDataTypes.findByClass(BigDecimal.class).get());
		Assertions.assertSame(SQLDataTypes.DECIMAL, SQLDataTypes.findByName("Decimal").get());
	}

	/**
	 * {@link SQLDataTypes#INSTANT}은 {@code TIMESTAMP}(시간대 없음) 컬럼에 UTC 벽시계 시각으로 저장한다. 그래서 쓰는
	 * JVM과 읽는 JVM의 기본 시간대가 달라도 같은 {@link Instant}가 나온다.
	 */
	@Test
	public void dateTimeIsStoredAsUtcRegardlessOfDefaultTimeZone() throws SQLException {
		Instant instant = Instant.parse("2026-11-02T01:02:03Z");
		TimeZone saved = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
			try ( PreparedStatement pstmt = m_conn.prepareStatement("insert into t (id, ts) values (1, ?)") ) {
				SQLDataTypes.INSTANT.write(pstmt, 1, instant);
				pstmt.executeUpdate();
			}
			try ( Statement stmt = m_conn.createStatement();
					ResultSet rs = stmt.executeQuery("select cast(ts as varchar) from t") ) {
				Assertions.assertTrue(rs.next());
				Assertions.assertEquals("2026-11-02 01:02:03", rs.getString(1));
			}

			TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
			try ( Statement stmt = m_conn.createStatement();
					ResultSet rs = stmt.executeQuery("select ts from t") ) {
				Assertions.assertTrue(rs.next());
				Assertions.assertEquals(instant, SQLDataTypes.INSTANT.read(rs, 1));
			}
		}
		finally {
			TimeZone.setDefault(saved);
		}
	}

	/**
	 * 구현 클래스는 감춰져 있고, 핸들러의 {@code toString()}은 타입 이름이다. 형식이 틀린 Duration 문자열은
	 * {@link SQLException}이다.
	 */
	@Test
	public void handlersAreNamedAndDurationParseFailureIsSQLException() throws SQLException {
		Assertions.assertEquals("Instant", SQLDataTypes.INSTANT.toString());
		Assertions.assertSame(SQLDataTypes.INSTANT, SQLDataTypes.findByName(SQLDataTypes.INSTANT.toString()).get());
		Assertions.assertFalse(java.lang.reflect.Modifier.isPublic(SQLDataTypes.INSTANT.getClass().getModifiers()));

		try ( Statement stmt = m_conn.createStatement() ) {
			stmt.executeUpdate("insert into t (id, dur) values (1, 'not-a-duration')");
		}
		try ( Statement stmt = m_conn.createStatement();
				ResultSet rs = stmt.executeQuery("select dur from t") ) {
			Assertions.assertTrue(rs.next());
			SQLException e = Assertions.assertThrows(SQLException.class, () -> SQLDataTypes.DURATION.read(rs, 1));
			Assertions.assertTrue(e.getMessage().contains("not-a-duration"), e.getMessage());
		}
	}

	/**
	 * {@link SQLDataTypes#LOCAL_DATE}는 {@code DATE} 컬럼의 날짜를 JVM 기본 시간대와 상관없이 그대로 주고받는다.
	 * {@code DATE} 컬럼의 기본 핸들러({@code fromSqlType})는 계속 {@link SQLDataTypes#DATE}이다.
	 */
	@Test
	public void localDateRoundTripsRegardlessOfTimeZone() throws SQLException {
		LocalDate value = LocalDate.of(2026, 1, 31);
		TimeZone saved = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));		// UTC+14
			Assertions.assertEquals(value, roundTrip(SQLDataTypes.LOCAL_DATE, "dt", value));
			Assertions.assertNull(roundTrip(SQLDataTypes.LOCAL_DATE, "dt", null));
		}
		finally {
			TimeZone.setDefault(saved);
		}

		Assertions.assertSame(SQLDataTypes.LOCAL_DATE, SQLDataTypes.findByClass(LocalDate.class).get());
		Assertions.assertSame(SQLDataTypes.LOCAL_DATE, SQLDataTypes.findByName("LocalDate").get());
		Assertions.assertSame(SQLDataTypes.DATE, SQLDataTypes.fromSqlType(Types.DATE));
	}
}
