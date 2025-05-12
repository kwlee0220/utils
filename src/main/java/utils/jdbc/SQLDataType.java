package utils.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface SQLDataType<T,S> {
	/**
	 * 본 SQL 데이터 타입에 해당하는 Java 클래스를 반환한다.
	 * 
	 * @return SQL 데이터 타입에 해당하는 Java 클래스
	 */
	public Class<T> getJavaClass();
	
	/**
	 * 주어진 Java 객체에 해당하는 SQL 클래스(타입)을 반환한다.
	 * 
	 * @param value	변경시킬 Java 객체.
	 * @return SQL 데이터 타입에 해당하는 SQL 클래스
	 */
	public S toSQLValue(T value);
	
	/**
	 * 주어진 SQL 객체에 해당하는 Java 클래스를 반환한다.
	 *
	 * @param sqlValue	변경시킬 SQL 객체.
	 * @return	 SQL 데이터 타입에 해당하는 Java 클래스
	 */
	public T toJavaValue(S sqlValue);
	
	/**
	 * PreparedStatement에 정의된 주어진 인덱스의 SQL 파라미터에 주어진 값을 설정한다.
	 *
	 * @param pstmt	설정할 PreparedStatement 객체.
	 * @param index	PreparedStatement의 설정 대상 파라미터의 인덱스.
	 * @param sqlValue	설정할 SQL 객체.
	 * @throws SQLException	PreparedStatement 설정 과정에서 발생한 예외.
	 */
	public void fillPreparedStatement(PreparedStatement pstmt, int index, S sqlValue) throws SQLException;

	/**
	 * PreparedStatement에 정의된 주어진 인덱스의 SQL 파라미터에 주어진 Java 객체를 설정한다.
	 * 
	 * @param pstmt		설정할 PreparedStatement 객체.
	 * @param index		PreparedStatement의 설정 대상 파라미터의 인덱스.
	 * @param javaValue	설정할 Java 객체.
	 * @throws SQLException	PreparedStatement 설정 과정에서 발생한 예외.
	 */
	public default void fillPreparedStatementWithJavaValue(PreparedStatement pstmt, int index, T javaValue)
		throws SQLException {
		fillPreparedStatement(pstmt, index, toSQLValue(javaValue));
	}
	
	/**
	 * ResultSet에서 주어진 인덱스의 SQL 값을 읽어 Java 객체로 변환한다.
	 *
	 * @param rset	값을 읽어올 ResultSet 객체.
	 * @param index	ResultSet의 읽어올 컬럼 인덱스.
	 * @return	읽어온 SQL 객체.
	 * @throws SQLException	ResultSet 읽기 과정에서 발생한 예외.
	 */
	public S readFromResultSet(ResultSet rset, int index) throws SQLException;
	
	/**
	 * ResultSet에서 주어진 컬럼 이름의 SQL 값을 읽어 Java 객체로 변환한다.
	 *
	 * @param rset	값을 읽어올 ResultSet 객체.
	 * @param columnName	읽어올 컬럼 이름.
	 * @return	읽어온 SQL 객체.
	 * @throws SQLException	ResultSet 읽기 과정에서 발생한 예외.
	 */
	public S readFromResultSet(ResultSet rset, String columnName) throws SQLException;
	
	/**
	 * ResultSet에서 주어진 인덱스의 SQL 값을 읽어 Java 객체로 변환한다.
	 *
	 * @param rset	값을 읽어올 ResultSet 객체.
	 * @param index	ResultSet의 읽어올 컬럼 인덱스.
	 * @return	읽어온 Java 객체.
	 * @throws SQLException	ResultSet 읽기 과정에서 발생한 예외.
	 */
	public default T readJavaValueFromResultSet(ResultSet rset, int index) throws SQLException {
		S sqlValue = readFromResultSet(rset, index);
		return toJavaValue(sqlValue);
	}
	
	/**
	 * ResultSet에서 주어진 컬럼 이름의 SQL 값을 읽어 Java 객체로 변환한다.
	 *
	 * @param rset			값을 읽어올 ResultSet 객체.
	 * @param columnName	읽어올 컬럼 이름.
	 * @return	읽어온 Java 객체.
	 * @throws SQLException	ResultSet 읽기 과정에서 발생한 예외.
	 */
	public default T readJavaValueFromResultSet(ResultSet rset, String columnName) throws SQLException {
		S sqlValue = readFromResultSet(rset, columnName);
		return toJavaValue(sqlValue);
	}
}
