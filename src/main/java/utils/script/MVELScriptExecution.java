package utils.script;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;

import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Utilities;
import utils.script.MVELScript.ImportClass;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MVELScriptExecution {
	private static final Logger s_logger = LoggerFactory.getLogger(MVELScriptExecution.class);
	
	private final ParserContext m_pc;
	private final MVELScript m_script;
	private volatile Serializable m_compiled;
	
	public static MVELScriptExecution of(MVELScript script) {
		Utilities.checkNotNullArgument(script, "script is null");
		
		return new MVELScriptExecution(script);
	}
	
	private MVELScriptExecution(MVELScript script) {
		m_pc = createParserContext();
		
		m_script = script;
		for ( ImportClass ic: m_script.getImportedClassAll() ) {
			ic.getImportName()
				.ifPresent(n -> m_pc.addImport(n, ic.getImportClass()))
				.ifAbsent(() -> m_pc.addImport(ic.getImportClass()));
		}
	}
	
	public final ParserContext getParserContext() {
		return m_pc;
	}
	
	public MVELScript getScript() {
		return m_script;
	}
	
	public void importFunctionAll(Class<?> funcCls) {
		importFunctions(m_pc, funcCls);
		m_compiled = null;
	}

	public Object run(Map<String, Object> vars) {
		Utilities.checkNotNullArgument(vars, "variables_map is null");
		
		if ( m_compiled == null ) {
			m_compiled = MVEL.compileExpression(m_script.getScript(), m_pc);
		}
		return MVEL.executeExpression(m_compiled, vars);
	}

	public Object run(VariableResolverFactory resolverFact) {
		Utilities.checkNotNullArgument(resolverFact, "VariableResolverFactory is null");
		
		if ( m_compiled == null ) {
			m_compiled = MVEL.compileExpression(m_script.getScript(), m_pc);
		}
		return MVEL.executeExpression(m_compiled, resolverFact);
	}
	
	@Override
	public String toString() {
		return m_script.toString();
	}
	
	protected ParserContext createParserContext() {
		ParserContext pc = ParserContext.create();
		pc.addPackageImport("java.util");
		pc.addPackageImport("com.google.common.collect");
		
		return pc;
	}
	
	private static void importFunctions(ParserContext pc, Class<?> cls) {
		for ( Method method: cls.getDeclaredMethods() ) {
			MVELFunction func = method.getAnnotation(MVELFunction.class);
			if ( func != null ) {
				s_logger.debug("importing MVEL function: name={} method={}", func.name(), method);
				pc.addImport(func.name(), method);
			}
		}
	}
}