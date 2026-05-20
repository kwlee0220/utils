package utils.stream;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ForEachTest {
	private int m_total = 0;
	
	@BeforeEach
	public void setUp() {
		m_total = 0;
	}
	
	@Test
	public void test0() throws Exception {
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4));
		
		stream.forEach(v -> m_total += v);
		Assertions.assertEquals(7, m_total);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> stream = FStream.empty();
		
		stream.forEach(v -> m_total += v);
		Assertions.assertEquals(0, m_total);
	}
}
