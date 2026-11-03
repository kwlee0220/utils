package utils.jdbc;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import utils.Preconditions;
import utils.func.FOption;

/**
 * 기본 {@link SQLDataType} 구현들과 타입 이름, Java 클래스, {@link java.sql.Types} 코드로부터
 * 해당 구현을 찾는 정적 메소드들을 제공하는 유틸리티 클래스.
 * <p>
 * 구현은 상태가 없으며 상수({@link #BOOLEAN}, {@link #STRING} 등)로만 공개된다. 구현 클래스는 감춘다.
 * <p>
 * <b>null 계약:</b> 모든 구현은 Java {@code null}과 SQL {@code NULL}을 서로 대응시킨다({@link SQLDataType} 참고).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class SQLDataTypes {
	private SQLDataTypes() {
		throw new AssertionError("This should not be called: class=" + getClass());
	}
	
	/** {@link Boolean} 타입 핸들러({@code setBoolean}/{@code getBoolean}). 타입 이름은 {@code "Boolean"}. */
	public static final SQLDataType<Boolean> BOOLEAN
		= new Handler<>("Boolean", Boolean.class, Types.BOOLEAN,
						(rs, i) -> wasNull(rs, rs.getBoolean(i)), (ps, i, v) -> ps.setBoolean(i, v));
	/** {@link String} 타입 핸들러({@code setString}/{@code getString}). 타입 이름은 {@code "String"}. */
	public static final SQLDataType<String> STRING
		= new Handler<>("String", String.class, Types.VARCHAR, ResultSet::getString, PreparedStatement::setString);
	/** {@link Short} 타입 핸들러({@code setShort}/{@code getShort}). 타입 이름은 {@code "Short"}. */
	public static final SQLDataType<Short> SHORT
		= new Handler<>("Short", Short.class, Types.SMALLINT,
						(rs, i) -> wasNull(rs, rs.getShort(i)), (ps, i, v) -> ps.setShort(i, v));
	/** {@link Integer} 타입 핸들러({@code setInt}/{@code getInt}). 타입 이름은 {@code "Integer"}. */
	public static final SQLDataType<Integer> INTEGER
		= new Handler<>("Integer", Integer.class, Types.INTEGER,
						(rs, i) -> wasNull(rs, rs.getInt(i)), (ps, i, v) -> ps.setInt(i, v));
	/** {@link Long} 타입 핸들러({@code setLong}/{@code getLong}). 타입 이름은 {@code "Long"}. */
	public static final SQLDataType<Long> LONG
		= new Handler<>("Long", Long.class, Types.BIGINT,
						(rs, i) -> wasNull(rs, rs.getLong(i)), (ps, i, v) -> ps.setLong(i, v));
	/** {@link Float} 타입 핸들러({@code setFloat}/{@code getFloat}). 타입 이름은 {@code "Float"}. */
	public static final SQLDataType<Float> FLOAT
		= new Handler<>("Float", Float.class, Types.REAL,
						(rs, i) -> wasNull(rs, rs.getFloat(i)), (ps, i, v) -> ps.setFloat(i, v));
	/** {@link Double} 타입 핸들러({@code setDouble}/{@code getDouble}). 타입 이름은 {@code "Double"}. */
	public static final SQLDataType<Double> DOUBLE
		= new Handler<>("Double", Double.class, Types.DOUBLE,
						(rs, i) -> wasNull(rs, rs.getDouble(i)), (ps, i, v) -> ps.setDouble(i, v));
	/**
	 * {@link BigDecimal} 타입 핸들러({@code setBigDecimal}/{@code getBigDecimal}). {@code NUMERIC}·{@code DECIMAL} 컬럼을
	 * 정밀도 손실 없이 다룬다. 읽은 값의 scale은 컬럼 정의를 따른다(예: {@code DECIMAL(10,2)}에 {@code 1.5}를 쓰면
	 * {@code 1.50}으로 읽힌다). 타입 이름은 {@code "Decimal"}.
	 */
	public static final SQLDataType<BigDecimal> DECIMAL
		= new Handler<>("Decimal", BigDecimal.class, Types.DECIMAL,
						ResultSet::getBigDecimal, PreparedStatement::setBigDecimal);
	/**
	 * {@link Instant}를 시간대 없는 {@code TIMESTAMP} 컬럼에 저장하는 타입 핸들러. 타입 이름은 {@code "Instant"}.
	 * <p>
	 * UTC {@link Calendar}를 넘긴 {@link PreparedStatement#setTimestamp(int, Timestamp, Calendar)}/
	 * {@link ResultSet#getTimestamp(int, Calendar)}로 주고받는다. 그래서 컬럼에는 UTC 벽시계 시각이 저장되고, 쓰는 JVM과
	 * 읽는 JVM의 기본 시간대가 달라도 같은 {@link Instant}가 나온다.
	 * <p>
	 * {@code TIMESTAMP WITH TIME ZONE} 컬럼에는 {@link #INSTANT_TZ}를 쓴다. 이 핸들러로 쓰면 드라이버에 따라(예: H2)
	 * Calendar로 해석한 시각을 세션 시간대 기준으로 다시 변환해 저장하므로 Instant가 어긋날 수 있다.
	 */
	public static final SQLDataType<Instant> INSTANT
		= new Handler<>("Instant", Instant.class, Types.TIMESTAMP,
						(rs, i) -> {
							Timestamp ts = rs.getTimestamp(i, utcCalendar());
							return (ts != null) ? ts.toInstant() : null;
						},
						(ps, i, v) -> ps.setTimestamp(i, Timestamp.from(v), utcCalendar()));
	/**
	 * {@link Instant}를 {@code TIMESTAMP WITH TIME ZONE} 컬럼에 저장하는 타입 핸들러. 타입 이름은 {@code "InstantTz"}.
	 * <p>
	 * JDBC 4.2의 {@link PreparedStatement#setObject(int, Object)}/
	 * {@link ResultSet#getObject(int, Class) getObject(index, OffsetDateTime.class)}로 주고받는다. 쓸 때는 UTC
	 * 오프셋으로 쓰고, 읽을 때는 저장된 오프셋을 반영한 {@link Instant}를 돌려준다. 시간대 없는 {@code TIMESTAMP}
	 * 컬럼에는 {@link #INSTANT}를 쓴다({@link OffsetDateTime}을 시간대 없는 컬럼에 쓰면 드라이버가 세션 시간대의 로컬
	 * 시각으로 바꿔 저장할 수 있다).
	 */
	public static final SQLDataType<Instant> INSTANT_TZ
		= new Handler<>("InstantTz", Instant.class, Types.TIMESTAMP_WITH_TIMEZONE,
						(rs, i) -> {
							OffsetDateTime odt = rs.getObject(i, OffsetDateTime.class);
							return (odt != null) ? odt.toInstant() : null;
						},
						(ps, i, v) -> ps.setObject(i, v.atOffset(ZoneOffset.UTC)));
	/**
	 * {@link Date java.util.Date}를 {@link java.sql.Date}로 저장하는 타입 핸들러({@code setDate}/{@code getDate}).
	 * 읽은 값은 {@link java.sql.Date}가 아니라 같은 시각의 {@link Date java.util.Date}이다. 타입 이름은 {@code "Date"}.
	 * <p>
	 * {@link Date java.util.Date}는 시각까지 표현하므로, 날짜만 다루는 값은 {@link #LOCAL_DATE}를 쓰는 편이 낫다.
	 * {@link #fromSqlType(int)}은 {@code DATE} 컬럼에 대해 이 핸들러를 돌려준다.
	 */
	public static final SQLDataType<Date> DATE
		= new Handler<>("Date", Date.class, Types.DATE,
						(rs, i) -> {
							java.sql.Date d = rs.getDate(i);
							return (d != null) ? new Date(d.getTime()) : null;
						},
						(ps, i, v) -> ps.setDate(i, new java.sql.Date(v.getTime())));
	/**
	 * {@link LocalDate} 타입 핸들러. {@code DATE} 컬럼을 다룬다. 타입 이름은 {@code "LocalDate"}.
	 * <p>
	 * JDBC 4.2의 {@code setObject}/{@code getObject(index, LocalDate.class)}로 주고받으므로 JVM 기본 시간대와
	 * 상관없이 컬럼의 날짜가 그대로 오간다. {@link #fromSqlType(int)}은 {@code DATE} 컬럼에 대해 이 핸들러가 아니라
	 * {@link #DATE}를 돌려준다.
	 */
	public static final SQLDataType<LocalDate> LOCAL_DATE
		= new Handler<>("LocalDate", LocalDate.class, Types.DATE,
						(rs, i) -> rs.getObject(i, LocalDate.class), PreparedStatement::setObject);
	/**
	 * {@link LocalTime} 타입 핸들러. {@code TIME} 컬럼을 다룬다. 타입 이름은 {@code "Time"}.
	 * <p>
	 * JDBC 4.2의 {@code setObject}/{@code getObject(index, LocalTime.class)}로 주고받으므로 JVM 기본 시간대와 상관없이
	 * 컬럼의 시각이 그대로 오간다.
	 */
	public static final SQLDataType<LocalTime> TIME
		= new Handler<>("Time", LocalTime.class, Types.TIME,
						(rs, i) -> rs.getObject(i, LocalTime.class), PreparedStatement::setObject);
	/**
	 * {@link Duration}을 ISO-8601 문자열({@link Duration#toString()}, 예: {@code "PT1M30S"})로 문자열 컬럼에 저장하는
	 * 타입 핸들러. 빈 문자열은 {@code null}로 읽는다. 형식이 틀린 문자열은 {@link SQLException}이다.
	 * 타입 이름은 {@code "Duration"}.
	 */
	public static final SQLDataType<Duration> DURATION
		= new Handler<>("Duration", Duration.class, Types.VARCHAR,
						(rs, i) -> parseDuration(rs.getString(i)), (ps, i, v) -> ps.setString(i, v.toString()));
	/** {@code byte[]} 타입 핸들러({@code setBytes}/{@code getBytes}). 타입 이름은 {@code "Binary"}. */
	public static final SQLDataType<byte[]> BINARY
		= new Handler<>("Binary", byte[].class, Types.VARBINARY, ResultSet::getBytes, PreparedStatement::setBytes);
	
	private static final Map<String,SQLDataType<?>> NAME_TO_SQLTYPE = new HashMap<>();
	private static final Map<Class<?>,SQLDataType<?>> CLASS_TO_SQLTYPE = new HashMap<>();
	static {
		for ( SQLDataType<?> type: new SQLDataType<?>[] { BOOLEAN, STRING, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL,
														INSTANT, INSTANT_TZ, DATE, LOCAL_DATE, TIME, DURATION,
														BINARY } ) {
			NAME_TO_SQLTYPE.put(type.toString(), type);
		}
		
		for ( SQLDataType<?> type: new SQLDataType<?>[] { BOOLEAN, STRING, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL,
														INSTANT, DATE, LOCAL_DATE, TIME, DURATION,
														BINARY } ) {
			// Instant의 기본 핸들러는 시간대 없는 TIMESTAMP용 INSTANT이다.
			CLASS_TO_SQLTYPE.put(type.getJavaClass(), type);
		}
		// 별칭: 원시 타입은 래퍼 타입의 핸들러, java.sql.Date는 java.util.Date의 핸들러.
		CLASS_TO_SQLTYPE.put(boolean.class, BOOLEAN);
		CLASS_TO_SQLTYPE.put(short.class, SHORT);
		CLASS_TO_SQLTYPE.put(int.class, INTEGER);
		CLASS_TO_SQLTYPE.put(long.class, LONG);
		CLASS_TO_SQLTYPE.put(float.class, FLOAT);
		CLASS_TO_SQLTYPE.put(double.class, DOUBLE);
		CLASS_TO_SQLTYPE.put(java.sql.Date.class, DATE);
	}
	
	/**
	 * 주어진 타입 이름의 SQL 데이터 타입 핸들러를 찾는다.
	 * <p>
	 * 타입 이름은 각 상수의 설명에 있다({@code "Boolean"}, {@code "Decimal"}, {@code "Instant"} 등). 대소문자를 구분한다.
	 * 핸들러의 {@link Object#toString()}도 타입 이름이다.
	 *
	 * @param typeName 타입 이름.
	 * @return 핸들러. 해당하는 이름이 없으면 빈 값.
	 */
	public static FOption<SQLDataType<?>> findByName(String typeName) {
		Preconditions.checkNotNullArgument(typeName, "typeName is null");
		return FOption.ofNullable(NAME_TO_SQLTYPE.get(typeName));
	}
	
	/**
	 * 주어진 Java 클래스를 Java 값 타입({@link SQLDataType#getJavaClass()})으로 갖는 SQL 데이터 타입 핸들러를 찾는다.
	 * <p>
	 * 원시 타입({@code boolean}, {@code short}, {@code int}, {@code long}, {@code float}, {@code double})은
	 * 래퍼 타입의 핸들러를, {@link java.sql.Date}는 {@link #DATE}를, {@link Instant}는 {@link #INSTANT}를 돌려준다.
	 * {@link java.sql.Timestamp}와 {@link java.sql.Time}은 Java 값 타입이 아니므로 빈 값이다.
	 *
	 * @param clazz	Java 클래스
	 * @return	핸들러. 해당하는 핸들러가 없으면 빈 값.
	 */
	public static FOption<SQLDataType<?>> findByClass(Class<?> clazz) {
		Preconditions.checkNotNullArgument(clazz, "class is null");
		return FOption.ofNullable(CLASS_TO_SQLTYPE.get(clazz));
	}

	/**
	 * 주어진 {@link java.sql.Types} 타입 코드에 해당하는 SQL 데이터 타입 핸들러를 반환한다.
	 * <p>
	 * 지원하는 타입 코드와 핸들러는 다음과 같다.
	 * <table class="striped">
	 *   <caption>SQL 타입 코드와 핸들러</caption>
	 *   <thead><tr><th>{@link java.sql.Types}</th><th>핸들러</th><th>Java 타입</th></tr></thead>
	 *   <tbody>
	 *   <tr><td>{@code BIT}, {@code BOOLEAN}</td><td>{@link #BOOLEAN}</td><td>{@link Boolean}</td></tr>
	 *   <tr><td>{@code TINYINT}, {@code SMALLINT}, {@code INTEGER}</td><td>{@link #INTEGER}</td><td>{@link Integer}</td></tr>
	 *   <tr><td>{@code BIGINT}</td><td>{@link #LONG}</td><td>{@link Long}</td></tr>
	 *   <tr><td>{@code REAL}</td><td>{@link #FLOAT}</td><td>{@link Float}</td></tr>
	 *   <tr><td>{@code FLOAT}, {@code DOUBLE}</td><td>{@link #DOUBLE}</td><td>{@link Double}</td></tr>
	 *   <tr><td>{@code NUMERIC}, {@code DECIMAL}</td><td>{@link #DECIMAL}</td><td>{@link BigDecimal}</td></tr>
	 *   <tr><td>{@code CHAR}, {@code VARCHAR}, {@code LONGVARCHAR}, {@code NCHAR}, {@code NVARCHAR},
	 *       {@code LONGNVARCHAR}, {@code CLOB}, {@code NCLOB}</td><td>{@link #STRING}</td><td>{@link String}</td></tr>
	 *   <tr><td>{@code DATE}</td><td>{@link #DATE}</td><td>{@link Date java.util.Date}</td></tr>
	 *   <tr><td>{@code TIME}</td><td>{@link #TIME}</td><td>{@link LocalTime}</td></tr>
	 *   <tr><td>{@code TIMESTAMP}</td><td>{@link #INSTANT}</td><td>{@link Instant}</td></tr>
	 *   <tr><td>{@code TIMESTAMP_WITH_TIMEZONE}</td><td>{@link #INSTANT_TZ}</td><td>{@link Instant}</td></tr>
	 *   <tr><td>{@code BINARY}, {@code VARBINARY}, {@code LONGVARBINARY}, {@code BLOB}</td><td>{@link #BINARY}</td>
	 *       <td>{@code byte[]}</td></tr>
	 *   </tbody>
	 * </table>
	 * {@code CLOB}·{@code NCLOB}은 {@link ResultSet#getString}으로, {@code BLOB}은 {@link ResultSet#getBytes}로 값 전체를
	 * 메모리에 읽는다. {@code TIMESTAMP}와 {@code TIMESTAMP_WITH_TIMEZONE}은 Java 타입은 같지만 쓰는 방법이 달라
	 * 핸들러가 다르다({@link #INSTANT}, {@link #INSTANT_TZ} 참고).
	 * <p>
	 * 그 밖의 타입({@code TIME_WITH_TIMEZONE}, {@code ARRAY}, {@code STRUCT}, {@code OTHER}(json·geometry 등),
	 * {@code NULL} 등)은 문자열로 읽어도 의미 있는 값이 된다는 보장이 없으므로 {@link IllegalArgumentException}을
	 * 던진다. 호출자가 문자열로 다루려면 예외를 잡아 {@link #STRING}을 쓴다.
	 *
	 * @param sqlType	{@link java.sql.Types}에 정의된 SQL 타입 코드
	 * @return	SQL 데이터 타입 핸들러
	 * @throws IllegalArgumentException	지원하지 않는 SQL 타입 코드인 경우. 메시지에 타입 코드와 {@link JDBCType} 이름이 들어간다.
	 */
	public static SQLDataType<?> fromSqlType(int sqlType) {
		return switch ( sqlType ) {
			case Types.BIT, Types.BOOLEAN -> BOOLEAN;
			case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> INTEGER;
			case Types.BIGINT -> LONG;
			case Types.REAL -> FLOAT;
			case Types.FLOAT, Types.DOUBLE -> DOUBLE;
			case Types.NUMERIC, Types.DECIMAL -> DECIMAL;
			case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
					Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
					Types.CLOB, Types.NCLOB -> STRING;
			case Types.DATE -> DATE;
			case Types.TIME -> TIME;
			case Types.TIMESTAMP -> INSTANT;
			case Types.TIMESTAMP_WITH_TIMEZONE -> INSTANT_TZ;
			case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> BINARY;
			default -> throw new IllegalArgumentException("Unsupported SQL type code: " + sqlType
															+ " (" + jdbcTypeName(sqlType) + ")");
		};
	}
	
	private static String jdbcTypeName(int sqlType) {
		try {
			return JDBCType.valueOf(sqlType).getName();
		}
		catch ( IllegalArgumentException e ) {
			return "unknown";		// 드라이버 고유 코드
		}
	}
	
	// 숫자·불리언 getter는 SQL NULL을 0·false로 돌려주므로 wasNull()로 판단한다.
	private static <T> T wasNull(ResultSet rs, T value) throws SQLException {
		return rs.wasNull() ? null : value;
	}
	
	// 드라이버가 넘겨받은 Calendar를 고칠 수 있으므로 공유하지 않고 호출마다 새로 만든다.
	private static Calendar utcCalendar() {
		return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	}
	
	private static Duration parseDuration(String str) throws SQLException {
		if ( str == null || str.isEmpty() ) {
			return null;
		}
		try {
			return Duration.parse(str);
		}
		catch ( DateTimeParseException e ) {
			throw new SQLException("invalid ISO-8601 duration: " + str, e);
		}
	}
	
	@FunctionalInterface
	private interface Reader<T> {
		T read(ResultSet rs, int index) throws SQLException;
	}
	
	@FunctionalInterface
	private interface Writer<T> {
		void write(PreparedStatement pstmt, int index, T nonNullValue) throws SQLException;
	}
	
	private static final class Handler<T> implements SQLDataType<T> {
		private final String m_name;
		private final Class<T> m_javaClass;
		private final int m_sqlNullType;
		private final Reader<T> m_reader;
		private final Writer<T> m_writer;
		
		Handler(String name, Class<T> javaClass, int sqlNullType, Reader<T> reader, Writer<T> writer) {
			m_name = name;
			m_javaClass = javaClass;
			m_sqlNullType = sqlNullType;
			m_reader = reader;
			m_writer = writer;
		}
		
		@Override
		public Class<T> getJavaClass() {
			return m_javaClass;
		}
		
		@Override
		public T read(ResultSet rs, int index) throws SQLException {
			return m_reader.read(rs, index);
		}
		
		@Override
		public void write(PreparedStatement pstmt, int index, T value) throws SQLException {
			if ( value == null ) {
				pstmt.setNull(index, m_sqlNullType);
			}
			else {
				m_writer.write(pstmt, index, value);
			}
		}
		
		@Override
		public String toString() {
			return m_name;
		}
	}
}
