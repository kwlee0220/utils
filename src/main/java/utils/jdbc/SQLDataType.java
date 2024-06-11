package utils.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface SQLDataType<T,S> {
	public Class<T> getJavaClass();
	
	public S toSQLValue(T value);
	public T toJavaValue(S sqlValue);
	
	public void fillPreparedStatement(PreparedStatement pstmt, int index, S sqlValue) throws SQLException;
	public S readFromResultSet(ResultSet rset, int index) throws SQLException;
	public S readFromResultSet(ResultSet rset, String columnName) throws SQLException;

	public default void fillPreparedStatementWithJavaValue(PreparedStatement pstmt, int index, T javaValue)
		throws SQLException {
		fillPreparedStatement(pstmt, index, toSQLValue(javaValue));
	}
	public default T readJavaValueFromResultSet(ResultSet rset, int index) throws SQLException {
		S sqlValue = readFromResultSet(rset, index);
		return toJavaValue(sqlValue);
	}
	public default T readJavaValueFromResultSet(ResultSet rset, String columnName) throws SQLException {
		S sqlValue = readFromResultSet(rset, columnName);
		return toJavaValue(sqlValue);
	}
}
