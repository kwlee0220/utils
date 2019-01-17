package utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.CIString;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CIStringTest {
	
	@Before
	public void setup() {
	}
	
	@Test
	public void test01() throws Exception {
		CIString name1 = CIString.of("col1");
		CIString name2 = CIString.of("COL1");
		CIString name3 = CIString.of("COL2");
		
		Assert.assertEquals(name1, name2);
		Assert.assertEquals(name1.hashCode(), name2.hashCode());
		Assert.assertNotEquals(name1.get(), name2.get());
		Assert.assertEquals(0, name1.compareTo(name2));
		
		Assert.assertNotEquals(name1, name3);
	}
}
