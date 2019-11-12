package utils.script;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

import utils.Utilities;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MVELScript2 {
	private final String m_scriptExpr;
	private final List<ImportClass> m_importedClasses = Lists.newArrayList();
	
	public static MVELScript2 of(String scriptStr) {
		Utilities.checkNotNullArgument(scriptStr, "script is null");
		
		return new MVELScript2(scriptStr);
	}
	
	private MVELScript2(String expr) {
		m_scriptExpr = expr;
	}
	
	public String getScript() {
		return m_scriptExpr;
	}
	
	public List<ImportClass> getImportedClassAll() {
		return Collections.unmodifiableList(m_importedClasses);
	}
	
	public MVELScript2 importClass(ImportClass ic) {
		Utilities.checkNotNullArgument(ic, "ImportedClass is null");
		
		m_importedClasses.add(ic);
		return this;
	}
	
	public MVELScript2 importClass(Class<?> cls) {
		Utilities.checkNotNullArgument(cls, "ImportedClass is null");
		
		m_importedClasses.add(new ImportClass(cls));
		return this;
	}
	
	public MVELScript2 importClass(Class<?> cls, String name) {
		Utilities.checkNotNullArgument(cls, "ImportedClass is null");
		
		m_importedClasses.add(new ImportClass(cls, name));
		return this;
	}
	
	@Override
	public String toString() {
		return m_scriptExpr;
	}
	
	public static class ImportClass {
		private final Class<?> m_class;
		private final FOption<String> m_name;
		
		public ImportClass(Class<?> cls, String name) {
			m_class = cls;
			m_name = FOption.of(name);
		}
		
		public ImportClass(Class<?> cls) {
			m_class = cls;
			m_name = FOption.empty();
		}
		
		public Class<?> getImportClass() {
			return m_class;
		}
		
		public FOption<String> getImportName() {
			return m_name;
		}
		
		public static ImportClass parse(String str) {
			String[] parts = str.split(":");
			
			try {
				Class<?> cls = Class.forName(parts[0]);
				if ( parts.length == 2 ) {
					String name = parts[1].trim();
					return new ImportClass(cls, name);
				}
				else {
					return new ImportClass(cls);
				}
			}
			catch ( ClassNotFoundException e ) {
				throw new IllegalArgumentException(""+e);
			}
		}
		
		@Override
		public String toString() {
			String clsName = m_class.getName();
			return m_name.map(name -> String.format("%s:%s", clsName, name))
						.getOrElse(() -> "" + clsName);
		}
	}
}