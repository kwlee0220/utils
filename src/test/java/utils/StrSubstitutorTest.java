package utils;

import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class StrSubstitutorTest {
	@Before
	public void setup() {
	}
	
	@Test
	public void testBasic() throws Exception {
		Map<String,String> facts = Map.of("A", "1", "B", "2", "C", "3");
		
		StrSubstitutor subst = new StrSubstitutor(facts).failOnUndefinedVariable(false);
		
		Assert.assertEquals("ABC", subst.replace("ABC"));
		Assert.assertEquals("1", subst.replace("${A}"));
		Assert.assertEquals("2", subst.replace("${B}"));
		Assert.assertEquals("3", subst.replace("${C}"));
		Assert.assertEquals("1-2-3", subst.replace("${A}-${B}-${C}"));
		Assert.assertEquals("${D}", subst.replace("${D}"));
	}
	
	@Test
	public void testNested() throws Exception {
		Map<String,String> facts = Map.of("A", "1", "B", "${A}a", "C", "B");
		
		StrSubstitutor subst = new StrSubstitutor(facts);
		subst.enableNestedSubstitution(false);
		Assert.assertEquals("${A}a", subst.replace("${B}"));

		subst.enableNestedSubstitution(true);
		Assert.assertEquals("1a", subst.replace("${B}"));
	}
	
	@Test
	public void testFallback() throws Exception {
		Map<String,String> facts = Map.of("A", "1", "B", "2", "C", "${A:-K}");
		
		StrSubstitutor subst = new StrSubstitutor(facts);
		Assert.assertEquals("1", subst.replace("${C}"));
		
		facts = Map.of("B", "2", "C", "${A:-K}");
		subst = new StrSubstitutor(facts);
		Assert.assertEquals("K", subst.replace("${C}"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSymbolNotFound() throws Exception {
		Map<String,String> facts = Map.of("A", "1", "B", "2", "C", "3");
		
		StrSubstitutor subst = new StrSubstitutor(facts);
		subst.failOnUndefinedVariable(true);
		subst.replace("${D}");
	}
}
