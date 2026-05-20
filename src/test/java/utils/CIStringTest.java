package utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CIStringTest {

	@BeforeEach
	public void setup() {
	}

	@Test
	public void test01() throws Exception {
		CIString name1 = CIString.of("col1");
		CIString name2 = CIString.of("COL1");
		CIString name3 = CIString.of("COL2");

		Assertions.assertEquals(name1, name2);
		Assertions.assertEquals(name1.hashCode(), name2.hashCode());
		Assertions.assertNotEquals(name1.get(), name2.get());
		Assertions.assertEquals(0, name1.compareTo(name2));

		Assertions.assertNotEquals(name1, name3);
	}
}
