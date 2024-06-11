package utils.jdbc.crud;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;

import com.google.common.base.Preconditions;

import utils.InternalException;
import utils.jdbc.SQLDataType;
import utils.jdbc.crud.TableBinding.ColumnBinding;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TableBindingCRUDOperation<T> implements JdbcCRUDOperation<T> {
	private final TableBinding m_tableBinding;
	
	public TableBindingCRUDOperation(TableBinding binding) {
		Preconditions.checkArgument(binding != null, "TableBinding was null");
		
		m_tableBinding = binding;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public int read(T dao, ResultSet rset) throws SQLException {
		if ( !rset.next() ) {
			return 0;
		}
		
		for ( int colIdx = 1; colIdx <= m_tableBinding.getColumnBindings().size(); ++colIdx ) {
			ColumnBinding columnBinding = m_tableBinding.getColumnBindings().get(colIdx-1);
			
			SQLDataType columnType = columnBinding.getSqlType();
			Object value = columnType.readJavaValueFromResultSet(rset, colIdx);
			try {
				PropertyUtils.setSimpleProperty(dao, columnBinding.getDaoFieldName(), value);
			}
			catch ( Exception e ) {
				String msg = String.format("Failed to set field: object=%s, field=%s, cause=%s",
										dao, columnBinding.getDaoFieldName(), e);
				throw new InternalException(msg);
			}
		}
		return 1;
	}

	@Override
	public int insert(T dao, PreparedStatement pstmt) throws SQLException {
		fillPreparedStatement(pstmt, dao, 1, m_tableBinding.getColumnBindings());
		return pstmt.executeUpdate();
	}

	@Override
	public int update(T dao, PreparedStatement pstmt) throws SQLException {
		List<ColumnBinding> valueColBindings = m_tableBinding.getNonKeyColumnBindings();
		fillPreparedStatement(pstmt, dao, 1, valueColBindings);

		List<ColumnBinding> keyColBindings = m_tableBinding.getKeyColumnBindings();
		fillPreparedStatement(pstmt, dao, valueColBindings.size() + 1, keyColBindings);
		
		return pstmt.executeUpdate();
	}

	@Override
	public int delete(T dao, PreparedStatement pstmt) throws SQLException {
		List<ColumnBinding> keyColBindings = m_tableBinding.getKeyColumnBindings();
		fillPreparedStatement(pstmt, dao, 1, keyColBindings);
		
		return pstmt.executeUpdate();
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void fillPreparedStatement(PreparedStatement pstmt, T dao, int startColIdx,
										List<ColumnBinding> columnBindings) throws SQLException {
		for ( int colIdx = 0; colIdx < columnBindings.size(); ++colIdx ) {
			ColumnBinding colBinding = columnBindings.get(colIdx);
			
			try {
				Object value = PropertyUtils.getSimpleProperty(dao, colBinding.getDaoFieldName());
				
				SQLDataType sqlType = colBinding.getSqlType();
				sqlType.fillPreparedStatementWithJavaValue(pstmt, startColIdx+colIdx, value);
			}
			catch ( SQLException e ) {
				throw e;
			}
			catch ( Exception e ) {
				String msg = String.format("Failed to set field: dao=%s, field=%s",
											dao, colBinding.getDaoFieldName());
				throw new InternalException(msg);
			}
		}
	}
}
