package utils.jdbc.crud;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface JdbcCRUDOperation<T> {
	public int read(T obj, ResultSet rset) throws SQLException;
	public int insert(T obj, PreparedStatement pstmt) throws SQLException;
	public int update(T obj, PreparedStatement pstmt) throws SQLException;
	public int delete(T obj, PreparedStatement pstmt) throws SQLException;
}
