package utils.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import com.google.common.collect.Maps;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SQLDataTypes {
	private SQLDataTypes() {
		throw new AssertionError("This should not be called: class=" + getClass());
	}
	
	public static final BooleanSQLConverter BOOLEAN = new BooleanSQLConverter();
	public static final StringSQLConverter STRING = new StringSQLConverter();
	public static final ShortSQLConverter SHORT = new ShortSQLConverter();
	public static final IntegerSQLConverter INTEGER = new IntegerSQLConverter();
	public static final LongSQLConverter LONG = new LongSQLConverter();
	public static final FloatSQLConverter FLOAT = new FloatSQLConverter();
	public static final DoubleSQLConverter DOUBLE = new DoubleSQLConverter();
	public static final DateTimeSQLConverter DATE_TIME = new DateTimeSQLConverter();
	public static final DateSQLConverter DATE = new DateSQLConverter();
	public static final TimeSQLConverter TIME = new TimeSQLConverter();
	public static final DurationSQLConverter DURATION = new DurationSQLConverter();
	
	private static final Map<String,SQLDataType<?,?>> NAME_TO_SQLTYPE = Maps.newHashMap();
	private static final Map<Class<?>,SQLDataType<?,?>> CLASS_TO_SQLTYPE = Maps.newHashMap();
	static {
		NAME_TO_SQLTYPE.put("Boolean", BOOLEAN);
		NAME_TO_SQLTYPE.put("String", STRING);
		NAME_TO_SQLTYPE.put("Short", SHORT);
		NAME_TO_SQLTYPE.put("Integer", INTEGER);
		NAME_TO_SQLTYPE.put("Long", LONG);
		NAME_TO_SQLTYPE.put("Float", FLOAT);
		NAME_TO_SQLTYPE.put("Double", DOUBLE);
		NAME_TO_SQLTYPE.put("DateTime", DATE_TIME);
		NAME_TO_SQLTYPE.put("Date", DATE);
		NAME_TO_SQLTYPE.put("Time", TIME);
		NAME_TO_SQLTYPE.put("Duration", DURATION);
		
		CLASS_TO_SQLTYPE.put(Boolean.class, BOOLEAN);
		CLASS_TO_SQLTYPE.put(String.class, STRING);
		CLASS_TO_SQLTYPE.put(Short.class, SHORT);
		CLASS_TO_SQLTYPE.put(Integer.class, INTEGER);
		CLASS_TO_SQLTYPE.put(Long.class, LONG);
		CLASS_TO_SQLTYPE.put(Float.class, FLOAT);
		CLASS_TO_SQLTYPE.put(Double.class, DOUBLE);
		CLASS_TO_SQLTYPE.put(Instant.class, DATE_TIME);
		CLASS_TO_SQLTYPE.put(Date.class, DATE);
		CLASS_TO_SQLTYPE.put(Time.class, TIME);
		CLASS_TO_SQLTYPE.put(Duration.class, DURATION);
	}
	
	public static SQLDataType<?,?> fromTypeName(String typeName) {
		return NAME_TO_SQLTYPE.get(typeName);
	}
	
	public static SQLDataType<?,?> fromClass(Class<?> clazz) {
		return CLASS_TO_SQLTYPE.get(clazz);
	}
	
	public static class BooleanSQLConverter implements SQLDataType<Boolean,Boolean> {
		@Override
		public Class<Boolean> getJavaClass() {
			return Boolean.class;
		}
		
		@Override
		public Boolean toSQLValue(Boolean value) {
			return value;
		}
	
		@Override
		public Boolean toJavaValue(Boolean sqlValue) {
			return sqlValue;
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, Boolean sqlValue)
			throws SQLException {
			pstmt.setBoolean(index, toSQLValue(sqlValue));
		}
	
		@Override
		public Boolean readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getBoolean(index);
		}
	
		@Override
		public Boolean readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getBoolean(columnName);
		}
	}
	
	public static class StringSQLConverter implements SQLDataType<String,String> {
		@Override
		public Class<String> getJavaClass() {
			return String.class;
		}
		
		@Override
		public String toSQLValue(String value) {
			return value;
		}
	
		@Override
		public String toJavaValue(String sqlValue) {
			return sqlValue;
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, String sqlValue) throws SQLException {
			pstmt.setString(index, toSQLValue(sqlValue));
		}
	
		@Override
		public String readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getString(index);
		}
	
		@Override
		public String readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getString(columnName);
		}
	}
	
	public static class ShortSQLConverter implements SQLDataType<Short,Short> {
		@Override
		public Class<Short> getJavaClass() {
			return Short.class;
		}
		
		@Override
		public Short toSQLValue(Short value) {
			return value;
		}
	
		@Override
		public Short toJavaValue(Short sqlValue) {
			return sqlValue;
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, Short sqlValue)
			throws SQLException {
			pstmt.setShort(index, toSQLValue(sqlValue));
		}
	
		@Override
		public Short readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getShort(index);
		}
	
		@Override
		public Short readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getShort(columnName);
		}
	}
	
	public static class IntegerSQLConverter implements SQLDataType<Integer,Integer> {
		@Override
		public Class<Integer> getJavaClass() {
			return Integer.class;
		}
		
		@Override
		public Integer toSQLValue(Integer value) {
			return value;
		}
	
		@Override
		public Integer toJavaValue(Integer sqlValue) {
			return sqlValue;
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, Integer sqlValue)
			throws SQLException {
			pstmt.setInt(index, toSQLValue(sqlValue));
		}
	
		@Override
		public Integer readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getInt(index);
		}
	
		@Override
		public Integer readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getInt(columnName);
		}
	}
	
	public static class LongSQLConverter implements SQLDataType<Long,Long> {
		@Override
		public Class<Long> getJavaClass() {
			return Long.class;
		}
		
		@Override
		public Long toSQLValue(Long value) {
			return value;
		}
	
		@Override
		public Long toJavaValue(Long sqlValue) {
			return sqlValue;
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, Long sqlValue)
			throws SQLException {
			pstmt.setLong(index, toSQLValue(sqlValue));
		}
	
		@Override
		public Long readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getLong(index);
		}
	
		@Override
		public Long readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getLong(columnName);
		}
	}
	
	public static class FloatSQLConverter implements SQLDataType<Float,Float> {
		@Override
		public Class<Float> getJavaClass() {
			return Float.class;
		}
		
		@Override
		public Float toSQLValue(Float value) {
			return value;
		}
	
		@Override
		public Float toJavaValue(Float sqlValue) {
			return sqlValue;
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, Float sqlValue)
			throws SQLException {
			pstmt.setFloat(index, toSQLValue(sqlValue));
		}
	
		@Override
		public Float readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getFloat(index);
		}
	
		@Override
		public Float readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getFloat(columnName);
		}
	}
	
	public static class DoubleSQLConverter implements SQLDataType<Double,Double> {
		@Override
		public Class<Double> getJavaClass() {
			return Double.class;
		}
		
		@Override
		public Double toSQLValue(Double value) {
			return value;
		}
	
		@Override
		public Double toJavaValue(Double sqlValue) {
			return sqlValue;
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, Double sqlValue)
			throws SQLException {
			pstmt.setDouble(index, toSQLValue(sqlValue));
		}
	
		@Override
		public Double readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getDouble(index);
		}
	
		@Override
		public Double readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getDouble(columnName);
		}
	}
	
	public static class DateTimeSQLConverter implements SQLDataType<Instant,Timestamp> {
		@Override
		public Class<Instant> getJavaClass() {
			return Instant.class;
		}
		
		@Override
		public Timestamp toSQLValue(Instant value) {
			return (value != null) ? Timestamp.from(value) : null;
		}
	
		@Override
		public Instant toJavaValue(Timestamp sqlValue) {
			return (sqlValue != null) ? sqlValue.toInstant() : null;
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, Timestamp sqlValue)
			throws SQLException {
			pstmt.setTimestamp(index, sqlValue);
		}
		public void fillPreparedStatement(PreparedStatement pstmt, int index, Instant jvalue)
			throws SQLException {
			pstmt.setTimestamp(index, toSQLValue(jvalue));
		}
	
		@Override
		public Timestamp readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getTimestamp(index);
		}
		public Instant readJavaValueFromResultSet(ResultSet rset, int index) throws SQLException {
			return toJavaValue(rset.getTimestamp(index));
		}
	
		@Override
		public Timestamp readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getTimestamp(columnName);
		}
		public Instant readJavaValueFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return toJavaValue(rset.getTimestamp(columnName));
		}
	}
	
	public static class DateSQLConverter implements SQLDataType<Date,java.sql.Date> {
		@Override
		public Class<Date> getJavaClass() {
			return Date.class;
		}
		
		@Override
		public java.sql.Date toSQLValue(Date value) {
			return (value != null) ? new java.sql.Date(value.getTime()) : null;
		}
	
		@Override
		public Date toJavaValue(java.sql.Date sqlValue) {
			return new Date(sqlValue.getTime());
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, java.sql.Date sqlValue)
			throws SQLException {
			pstmt.setDate(index, toSQLValue(sqlValue));
		}
	
		@Override
		public java.sql.Date readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getDate(index);
		}
	
		@Override
		public java.sql.Date readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getDate(columnName);
		}
	}
	
	public static class TimeSQLConverter implements SQLDataType<Time,java.sql.Time> {
		@Override
		public Class<Time> getJavaClass() {
			return Time.class;
		}
		
		@Override
		public java.sql.Time toSQLValue(Time value) {
			return (value != null) ? new java.sql.Time(value.getTime()) : null;
		}
	
		@Override
		public Time toJavaValue(java.sql.Time sqlValue) {
			return new Time(sqlValue.getTime());
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, java.sql.Time sqlValue)
			throws SQLException {
			pstmt.setTime(index, sqlValue);
		}
	
		@Override
		public java.sql.Time readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getTime(index);
		}
	
		@Override
		public java.sql.Time readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getTime(columnName);
		}
	}
	
	public static class DurationSQLConverter implements SQLDataType<Duration,String> {
		@Override
		public Class<Duration> getJavaClass() {
			return Duration.class;
		}
		
		@Override
		public String toSQLValue(Duration value) {
			return value.toString();
		}
	
		@Override
		public Duration toJavaValue(String sqlValue) {
			return (sqlValue != null && sqlValue.length() > 0) ? Duration.parse(sqlValue) : null;
		}
	
		@Override
		public void fillPreparedStatement(PreparedStatement pstmt, int index, String sqlValue)
			throws SQLException {
			pstmt.setString(index, sqlValue);
		}
		public void fillPreparedStatement(PreparedStatement pstmt, int index, Duration jvalue)
			throws SQLException {
			pstmt.setString(index, toSQLValue(jvalue));
		}
	
		@Override
		public String readFromResultSet(ResultSet rset, int index) throws SQLException {
			return rset.getString(index);
		}
		public Duration readJavaValueFromResultSet(ResultSet rset, int index) throws SQLException {
			return toJavaValue(rset.getString(index));
		}
	
		@Override
		public String readFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return rset.getString(columnName);
		}
		public Duration readJavaValueFromResultSet(ResultSet rset, String columnName) throws SQLException {
			return toJavaValue(rset.getString(columnName));
		}
	}
}
