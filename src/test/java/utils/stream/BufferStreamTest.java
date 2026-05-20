package utils.stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class BufferStreamTest {
	@Test
	public void test0() throws Exception {
		FStream<Integer> base = FStream.from(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
		FStream<String> stream = base.map(v -> ""+v).buffer(2, 1).map(b -> String.join("", b));
		
		FOption<String> r;
		
		r = stream.next();
		Assertions.assertEquals("12", r.get());
		
		r = stream.next();
		Assertions.assertEquals("24", r.get());

		r = stream.next();	// 4, 1
		Assertions.assertEquals("41", r.get());

		r = stream.next();	// 1, 3
		Assertions.assertEquals("13", r.get());
		
		r = stream.next();	// 3, 5
		r = stream.next();	// 5, 2
		r = stream.next();	// 2, 7
		r = stream.next();	// 7, 6
		Assertions.assertEquals("76", r.get());
		
		r = stream.next();	// 6
		Assertions.assertEquals("6", r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> base = FStream.from(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
		FStream<String> stream = base.map(v -> ""+v).buffer(3, 2).map(b -> String.join("", b));

		FOption<String> r;
		
		r = stream.next();	// 1, 2, 4
		Assertions.assertEquals("124", r.get());
		
		r = stream.next();	// 4, 1, 3
		Assertions.assertEquals("413", r.get());

		r = stream.next();	// 3, 5, 2
		Assertions.assertEquals("352", r.get());
		
		r = stream.next();	// 2, 7, 6
		Assertions.assertEquals("276", r.get());
		
		r = stream.next();	// 6
		Assertions.assertEquals("6", r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test2() throws Exception {
		FStream<Integer> base = FStream.from(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
		FStream<String> stream = base.map(v -> ""+v).buffer(1, 5).map(b -> String.join("", b));

		FOption<String> r;
		
		r = stream.next();	// 1
		Assertions.assertEquals("1", r.get());
		
		r = stream.next();	// 5
		Assertions.assertEquals("5", r.get());
		
		r = stream.next();
		Assertions.assertEquals(true, r.isAbsent());
	}

	@Test
	public void test3() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> base = FStream.from(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
			base.buffer(0, 3);
			});
	}

	@Test
	public void test4() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> base = FStream.from(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
			base.buffer(-1, 3);
			});
	}

	@Test
	public void test5() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<Integer> base = FStream.from(Lists.newArrayList(1, 2, 4, 1, 3, 5, 2, 7, 6));
			base.buffer(3, -1);
			});
	}
}
