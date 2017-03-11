package utils.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Spliterators;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import utils.Errors;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ResultSetSpliterator extends Spliterators.AbstractSpliterator<ResultSet>
							implements AutoCloseable {
	private ResultSet m_rs;
	
	ResultSetSpliterator(ResultSet rs) {
		super(Long.MAX_VALUE, 0);
		
		m_rs = rs;
	}

	@Override
	public void close() throws SQLException {
		if ( m_rs != null ) {
			Statement stmt = m_rs.getStatement();
			Connection conn = stmt.getConnection();
			
			JdbcUtils.closeQuietly(stmt);
			JdbcUtils.closeQuietly(conn);
			
			m_rs = null;
		}
	}

	@Override
	public boolean tryAdvance(Consumer<? super ResultSet> consumer) {
		Preconditions.checkNotNull(consumer, "Consumer is null");
		
		if ( m_rs == null ) {
			return false;
		}
		
		try {
			if ( !m_rs.next() ) {
				close();
				m_rs = null;
				
				return false;
			}
			
			consumer.accept(m_rs);
		}
		catch ( SQLException e ) {
			Errors.runQuietly(()->close());
			m_rs = null;
			
			throw new RuntimeException(e);
		}
		
		return false;
	}
}
