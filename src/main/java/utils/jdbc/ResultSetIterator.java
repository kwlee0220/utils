package utils.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.function.Function;

import com.google.common.base.Preconditions;

import utils.Errors;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ResultSetIterator<T> implements Iterator<T>, AutoCloseable {
	private ResultSet m_rs;
	private final Function<ResultSet,T> m_functor;
	private boolean m_hasNext;
	
	ResultSetIterator(ResultSet rs, Function<ResultSet,T> functor) throws SQLException {
		m_rs = rs;
		m_functor = functor;
		
		m_hasNext = m_rs.next();
	}

	@Override
	public void close() throws Exception {
		if ( m_rs != null ) {
			Statement stmt = m_rs.getStatement();
			Connection conn = stmt.getConnection();
			
			JdbcUtils.closeQuietly(stmt);
			JdbcUtils.closeQuietly(conn);
			
			m_rs = null;
		}
	}
	
	@Override
	public boolean hasNext() {
		Preconditions.checkState(m_rs != null, "already closed");
		
		return m_hasNext;
	}

	@Override
	public T next() {
		Preconditions.checkState(m_rs != null, "already closed");
		
		try {
			T result = m_functor.apply(m_rs);
			m_hasNext = m_rs.next();
			if ( !m_hasNext ) {
				Errors.runQuietly(() -> close());
			}
			
			return result;
		}
		catch ( SQLException e ) {
			throw new RuntimeException(e);
		}
	}
}
