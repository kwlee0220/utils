package utils.jdbc.crud;

import java.util.List;

import utils.jdbc.SQLDataType;
import utils.jdbc.SQLDataTypes;
import utils.stream.FStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TableBinding {
	private String m_id;
	private String m_tableName;
	private List<String> m_keyColumns;
	private List<ColumnBinding> m_columnBindings;
	
	public String getId() {
		return m_id;
	}
	
	public void setId(String id) {
		m_id = id;
	}
	
	public String getTableName() {
		return m_tableName;
	}
	
	public void setTableName(String tableName) {
		this.m_tableName = tableName;
	}
	
	public List<String> getKeyColumns() {
		return m_keyColumns;
	}
	
	public void setKeyColumns(List<String> names) {
		m_keyColumns = names;
	}
	
	public List<ColumnBinding> getColumnBindings() {
		return m_columnBindings;
	}
	
	public void setColumnBindings(List<ColumnBinding> bindings) {
		m_columnBindings = bindings;
	}
	
	public List<String> getColumnNames() {
		return FStream.from(m_columnBindings).map(b -> b.getColumnName()).toList();
	}
	
	public String getColumnNamesCsv() {
		return FStream.from(m_columnBindings)
						.map(b -> b.getColumnName())
						.join(',');
	}
	
	public List<ColumnBinding> getKeyColumnBindings() {
		return FStream.from(m_columnBindings)
						.filter(c -> m_keyColumns.contains(c.getColumnName()))
						.toList();
	}
	
	public List<ColumnBinding> getNonKeyColumnBindings() {
		return FStream.from(m_columnBindings)
						.filter(b -> !this.m_keyColumns.contains(b.getColumnName()))
						.toList();
	}
	
	public static class ColumnBinding {
		private String m_columnName;
		@SuppressWarnings("rawtypes")
		private SQLDataType m_sqlType;
		private String m_daoFieldName;
		private int m_keyIndex = -1;
		
		public String getColumnName() {
			return m_columnName;
		}
		
		public void setColumnName(String name) {
			m_columnName = name;
		}
		
		public void setSqlType(String typeName) {
			this.m_sqlType = SQLDataTypes.fromTypeName(typeName);
		}
		
		@SuppressWarnings("rawtypes")
		public SQLDataType getSqlType() {
			return m_sqlType;
		}
		
		@SuppressWarnings("rawtypes")
		public void setSqlType(SQLDataType sqlType) {
			m_sqlType = sqlType;
		}
		
		public String getDaoFieldName() {
			return m_daoFieldName;
		}
		
		public void setDaoFieldName(String name) {
			m_daoFieldName = name;
		}
		
		public int getKeyIndex() {
			return m_keyIndex;
		}
		
		public void setKeyIndex(int index) {
			m_keyIndex = index;
		}
		
		@Override
		public String toString() {
			String keyStr = (this.m_keyIndex >= 0) ? String.format(" (KEY:%d)", this.m_keyIndex) : "";
			return String.format("%s(%s): %s%s", this.m_daoFieldName, this.m_sqlType.getJavaClass().getSimpleName(),
									this.m_columnName, keyStr);
		}
	}

}
