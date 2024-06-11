package utils.jdbc.crud;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;

import com.google.common.base.Preconditions;

import utils.InternalException;
import utils.jdbc.SQLDataType;
import utils.jdbc.crud.TableBinding.ColumnBinding;
import utils.stream.FStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TableBindingSQLCRUDOperation<T> implements JdbcSQLCRUDOperation<T> {
	private final TableBinding m_binding;
	private final JdbcCRUDOperation<T> m_daoCrud;
	
	private TableBindingSQLCRUDOperation(TableBinding binding, JdbcCRUDOperation<T> daoCrud) {
		Preconditions.checkArgument(binding != null, "TableBinding was null");
		Preconditions.checkArgument(daoCrud != null, "JdbcCRUDOperation was null");
		
		m_binding = binding;
		m_daoCrud = daoCrud;
	}
	
	public static <T> TableBindingSQLCRUDOperation<T> from(TableBinding binding) {
		return new TableBindingSQLCRUDOperation<>(binding, new TableBindingCRUDOperation<>(binding));
	}
	
	public static <T, L extends DaoList<T>>
	TableBindingSQLCRUDOperation<L> newListCRUDOperation(TableBinding binding, List<String> keyColumns) {
		TableBinding daoListBinding = new TableBinding();
		daoListBinding.setTableName(binding.getTableName());
		daoListBinding.setColumnBindings(binding.getColumnBindings());
		daoListBinding.setKeyColumns(keyColumns);
		
		TableBindingCRUDOperation<T> daoOp = new TableBindingCRUDOperation<>(binding);
		JdbcDaoListCRUDOperation<T,L> daoListOp = new JdbcDaoListCRUDOperation<>(daoOp);
		
		return new TableBindingSQLCRUDOperation<>(daoListBinding, daoListOp);
	}
	
	private static final String SQL_SELECT = "select %s from %s%s";
	@Override
	public int read(Connection conn, T key) throws SQLException {
		Preconditions.checkArgument(conn != null, "Connection was null");
		Preconditions.checkArgument(key != null, "Key was null");
		
		String columnAllCsv = FStream.from(m_binding.getColumnNames()).join(',');
		String whereClause = "";
		if ( m_binding.getKeyColumns() != null && m_binding.getKeyColumns().size() > 0 ) {
			whereClause = FStream.from(m_binding.getKeyColumns())
								.map(n -> String.format("%s = ?", n))
								.join(" and ", " where ", "");
		}
		String sql = String.format(SQL_SELECT, columnAllCsv, m_binding.getTableName(), whereClause);
		try ( PreparedStatement pstmt = conn.prepareStatement(sql) ) {
			fillPreparedStatement(pstmt, key, 1, m_binding.getKeyColumnBindings());
			
			try ( ResultSet rset = pstmt.executeQuery() ) {
				return m_daoCrud.read(key, rset);
			}
		}
	}
	
	static final String SQL_INSERT_FORMAT = "insert into %s(%s) values %s";
	@Override
	public int insert(Connection conn, T dao) throws SQLException {
		Preconditions.checkArgument(conn != null, "Connection was null");
		Preconditions.checkArgument(dao != null, "Dao was null");
		
		String valuesStr = FStream.from(m_binding.getColumnBindings()).map(v -> "?").join(",", "(", ")");
		String sql = String.format(SQL_INSERT_FORMAT, m_binding.getTableName(),
									m_binding.getColumnNamesCsv(), valuesStr);
		try ( PreparedStatement pstmt = conn.prepareStatement(sql) ) {
			return m_daoCrud.insert(dao, pstmt);
		}
	}
	
	static final String SQL_UPDATE_FORMAT = "update %s set %s where %s";
	@Override
	public int update(Connection conn, T dao) throws SQLException {
		Preconditions.checkArgument(conn != null, "Connection was null");
		Preconditions.checkArgument(dao != null, "Parameter was null");
		
		String whereClause = FStream.from(m_binding.getKeyColumns())
									.map(n -> String.format("%s = ?", n))
									.join(" and ");
		String setColumnListStr = FStream.from(m_binding.getNonKeyColumnBindings())
												.map(b -> String.format("%s = ?", b.getColumnName()))
												.join(',');
		String sql = String.format(SQL_UPDATE_FORMAT, m_binding.getTableName(), setColumnListStr, whereClause);
		
		try ( PreparedStatement pstmt = conn.prepareStatement(sql) ) {
			int cnt = m_daoCrud.update(dao, pstmt);
			if ( cnt == 0 ) {
				return insert(conn, dao);
			}
			else {
				return cnt;
			}
		}
	}
	
	static final String SQL_DELETE = "delete from %s where %s";
	@Override
	public int delete(Connection conn, T key) throws SQLException {
		Preconditions.checkArgument(conn != null, "Connection was null");
		Preconditions.checkArgument(key != null, "Key was null");
		
		String whereClause = FStream.from(m_binding.getKeyColumns())
									.map(n -> String.format("%s = ?", n))
									.join(" and ");
		String sql = String.format(SQL_DELETE, m_binding.getTableName(), whereClause);
		try ( PreparedStatement pstmt = conn.prepareStatement(sql) ) {
			return m_daoCrud.delete(key, pstmt);
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void fillPreparedStatement(PreparedStatement pstmt, Object dao, int startColIdx,
										List<ColumnBinding> columnBindings) throws SQLException {
		for ( int colIdx = 1; colIdx <= columnBindings.size(); ++colIdx ) {
			ColumnBinding colBinding = columnBindings.get(colIdx-1);
			
			try {
				Object value = PropertyUtils.getSimpleProperty(dao, colBinding.getDaoFieldName());
				
				SQLDataType sqlType = colBinding.getSqlType();
				sqlType.fillPreparedStatementWithJavaValue(pstmt, colIdx, value);
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
