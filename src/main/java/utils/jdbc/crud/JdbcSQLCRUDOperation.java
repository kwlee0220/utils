package utils.jdbc.crud;

import java.sql.Connection;
import java.sql.SQLException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface JdbcSQLCRUDOperation<T> {
	public int read(Connection conn, T key) throws SQLException;
	public int update(Connection conn, T param) throws SQLException;
	public int insert(Connection conn, T param) throws SQLException;
	public int delete(Connection conn, T key) throws SQLException;
}
