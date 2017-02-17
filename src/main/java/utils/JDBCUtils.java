package utils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JDBCUtils {
	private JDBCUtils() {
		throw new AssertionError("Should not be called: " + JDBCUtils.class);
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

	public static <T> Stream<T> stream(ResultSet rs, Function<ResultSet,T> trans) {
		return Utilities.stream(new ResultSetIterator<T>(rs, trans));
	}
	
	static class ResultSetIterator<T> implements Iterator<T> {
		private ResultSet m_rs;
		private Function<ResultSet,T> m_trans;
		private boolean m_hasNext;
		
		ResultSetIterator(ResultSet rs, Function<ResultSet,T> trans) {
			m_rs = rs;
			m_trans = trans;
			
			try {
				m_hasNext = m_rs.next();
			}
			catch ( SQLException e ) {
				throw new JDBCException(e);
			}
		}

		@Override
		public boolean hasNext() {
			return m_hasNext;
		}

		@Override
		public T next() {
			try {
				T out = m_trans.apply(m_rs);
				m_hasNext = m_rs.next();
				
				return out;
			}
			catch ( SQLException e ) {
				throw new JDBCException(e);
			}
		}
		
	}
}
