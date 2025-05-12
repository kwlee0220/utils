package utils.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

import com.google.common.base.Preconditions;

import utils.func.CheckedFunctionX;
import utils.io.IOUtils;

/**
 * JdbcObjectIterator는 ResultSet에서 객체를 읽어오는 Iterator이다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class JdbcObjectIterator<T> implements Iterator<T>, AutoCloseable {
	private ResultSet m_rs;
	private final CheckedFunctionX<ResultSet,T,SQLException> m_functor;
	private boolean m_hasNext;
	
	JdbcObjectIterator(ResultSet rs, CheckedFunctionX<ResultSet,T,SQLException> functor) throws SQLException {
		Preconditions.checkNotNull(rs, "ResultSet is null");
		Preconditions.checkNotNull(functor, "Functor is null");
		
		m_rs = rs;
		m_functor = functor;
		
		m_hasNext = m_rs.next();
	}

	@Override
	public void close() throws Exception {
		// 이미 close()된 경우에는 아무것도 하지 않기 위해 m_rs가 null인가 확인한다.
		if ( m_rs != null ) {
			IOUtils.closeQuietly(m_rs);
			
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
			
			return result;
		}
		catch ( SQLException e ) {
			throw new RuntimeException(e);
		}
	}
}
