package utils.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;
import java.util.stream.Stream;

import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcUtils {
	private JdbcUtils() {
		throw new AssertionError("Should not be called: " + JdbcUtils.class);
	}
	
	public static boolean closeQuietly(Statement stmt) {
		if ( stmt != null ) {
			try {
				stmt.close();
				return true;
			}
			catch ( SQLException e ) { }
		}
		
		return false;
	}
	
	public static boolean closeQuietly(Connection conn) {
		if ( conn != null ) {
			try {
				conn.close();
				return true;
			}
			catch ( SQLException e ) { }
		}
		
		return false;
	}

	public static <T> Stream<T> stream(ResultSet rs, Function<ResultSet,T> trans)
		throws SQLException {
		return Utilities.stream(new JdbcObjectIterator<T>(rs, trans));
	}
}
