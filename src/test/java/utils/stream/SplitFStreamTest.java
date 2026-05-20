package utils.stream;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SplitFStreamTest {
	@Test
	public void test00() throws Exception {
		List<String> srcList = Arrays.asList("3", "5", "0", "1", "0", "0", "7");
		
		String ret = FStream.from(srcList)
							.split(v -> v.equals("0"))
							.map(b -> String.join("", b))
							.join('|');
		Assertions.assertEquals("35|1||7", ret);
	}
	
	@Test
	public void test01() throws Exception {
		List<String> srcList = Arrays.asList("3", "5", "7");
		
		String ret = FStream.from(srcList)
							.split(v -> v.equals("0"))
							.map(b -> String.join("", b))
							.join('|');
		Assertions.assertEquals("357", ret);
	}
	
	@Test
	public void test02() throws Exception {
		List<String> srcList = Arrays.asList("0", "3", "5", "7", "0", "0");
		
		String ret = FStream.from(srcList)
							.split(v -> v.equals("0"))
							.map(b -> String.join("", b))
							.join('|');
		Assertions.assertEquals("|357||", ret);
	}
}
