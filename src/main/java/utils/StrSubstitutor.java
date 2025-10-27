package utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;

import com.google.common.collect.Maps;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StrSubstitutor {
	private final StringSubstitutor m_substitutor;
	
	public StrSubstitutor(Map<String,String> keyValues) {
		StringLookupFactory factory = StringLookupFactory.INSTANCE;
		StringLookup lookup = factory.mapStringLookup(keyValues);
		StringLookup interpolator = factory.interpolatorStringLookup(Map.of(), lookup, true);
		m_substitutor = new StringSubstitutor(interpolator);
		m_substitutor.setDisableSubstitutionInValues(false);
		m_substitutor.setEnableUndefinedVariableException(true);
		m_substitutor.setEnableUndefinedVariableException(false);
	}
	
	public void failOnUndefinedVariable() {
		m_substitutor.setEnableUndefinedVariableException(true);
	}
	
	public void disableNestedSubstitution(boolean flag) {
		m_substitutor.setDisableSubstitutionInValues(flag);
		m_substitutor.setEnableUndefinedVariableException(!flag);
	}
	
	public String replace(String template) {
		return m_substitutor.replace(template);
	}
	
	public static LinkedHashMap<String,String> replaceIncrementally(List<KeyValue<String,String>> keyValues,
																	Map<String,String> facts) {
		Map<String,String> expandedFacts = Maps.newHashMap(facts);
		LinkedHashMap<String,String> result = Maps.newLinkedHashMap();
		for ( KeyValue<String, String> kv : keyValues ) {
			String key = kv.key();
			String value = kv.value();
			
			StrSubstitutor subst = new StrSubstitutor(expandedFacts);
			subst.failOnUndefinedVariable();
			
			String replaced = subst.replace(value);
			result.put(key, replaced);
			expandedFacts.put(key, replaced);
		}
		
		return result;
	}
}
