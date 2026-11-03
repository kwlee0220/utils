package utils.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.jetbrains.annotations.Nullable;


/**
 * Java 값 하나를 JDBC로 읽고 쓰는 SQL 데이터 타입 핸들러 인터페이스.
 * <p>
 * {@link ResultSet} 컬럼을 Java 값으로 읽고({@link #read(ResultSet, int)}), Java 값을 {@link PreparedStatement}
 * 파라미터로 쓴다({@link #write(PreparedStatement, int, Object)}). JDBC 드라이버와 주고받는 중간 값(예:
 * {@link java.sql.Timestamp})은 구현 안에 감춘다. 기본 구현은 {@link SQLDataTypes}의 상수로만 공개된다.
 * <p>
 * <b>null 계약:</b> Java {@code null}과 SQL {@code NULL}을 서로 대응시킨다. {@code write}에 {@code null}을 넘기면
 * {@link PreparedStatement#setNull(int, int)}로 SQL {@code NULL}을 설정하고, SQL {@code NULL} 컬럼을 읽으면
 * {@code null}을 반환한다(숫자·불리언은 {@link ResultSet#wasNull()}로 판단).
 *
 * @param <T>	Java 쪽 값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface SQLDataType<T> {
	/**
	 * 이 핸들러가 다루는 Java 값의 클래스를 반환한다.
	 * 
	 * @return Java 값의 클래스.
	 */
	public Class<T> getJavaClass();
	
	/**
	 * 현재 행에서 주어진 인덱스의 컬럼을 Java 값으로 읽는다.
	 *
	 * @param rs	값을 읽을 ResultSet. 읽을 행에 위치해 있어야 한다.
	 * @param index	컬럼 인덱스(1부터).
	 * @return	읽은 Java 값. SQL {@code NULL}이면 {@code null}.
	 * @throws SQLException	값을 읽거나 변환하지 못한 경우.
	 */
	public @Nullable T read(ResultSet rs, int index) throws SQLException;
	
	/**
	 * 주어진 Java 값을 PreparedStatement의 주어진 인덱스 파라미터로 설정한다.
	 *
	 * @param pstmt	설정할 PreparedStatement.
	 * @param index	파라미터 인덱스(1부터).
	 * @param value	설정할 Java 값. {@code null}이면 SQL {@code NULL}을 설정한다.
	 * @throws SQLException	파라미터를 설정하지 못한 경우.
	 */
	public void write(PreparedStatement pstmt, int index, @Nullable T value) throws SQLException;
}
