package utils.jdbc.crud;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcDaoListCRUDOperation<T, L extends DaoList<T>> implements JdbcCRUDOperation<L> {
	private final JdbcCRUDOperation<T> m_daoHandler;
	
	public JdbcDaoListCRUDOperation(JdbcCRUDOperation<T> daoHandler) {
		m_daoHandler = daoHandler;
	}

	@Override
	public int read(L daoList, ResultSet rset) throws SQLException {
		int sum = 0;
		while ( true ) {
			T elm = daoList.newElementInstance();
			int count = m_daoHandler.read(elm, rset);
			if ( count == 0 ) {
				break;
			}

			daoList.add(elm);
			sum += count;
		}
		
		return sum;
	}

	@Override
	public int update(L daoList, PreparedStatement pstmt) throws SQLException {
		int updateCnt = 0;
		for ( T elm: daoList ) {
			updateCnt += m_daoHandler.update(elm, pstmt);
		}
		
		return updateCnt;
	}

	@Override
	public int insert(L daoList, PreparedStatement pstmt) throws SQLException {
		int count = 0;
		for ( T elm: daoList ) {
			count += m_daoHandler.insert(elm, pstmt);
		}
		
		return count;
	}

	@Override
	public int delete(L daoList, PreparedStatement pstmt) throws SQLException {
		int count = 0;
		for ( T elm: daoList ) {
			count += m_daoHandler.delete(elm, pstmt);
		}
		
		return count;
	}
}
