package utils.jdbc.crud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.stream.FStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TableBindings  {
	private Map<String,TableBinding> tableBindingMap;
	
	public TableBinding getTableBinding(String tableName) {
		return this.tableBindingMap.get(tableName);
	}
	public List<TableBinding> getTableBindings() {
		return Lists.newArrayList(this.tableBindingMap.values());
	}

	public void setTableBindings(List<TableBinding> bindings) {
		LinkedHashMap<String, TableBinding> bindingMaps = Maps.newLinkedHashMap();
		tableBindingMap = FStream.from(bindings)
								.toMap(bindingMaps, b -> b.getId(), b -> b);
	}
}
