package utils.stream;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.func.FOption;
import utils.func.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class TryMapTest {
	@Before
	public void setUp() { }
	
	@Test
	public void test0() throws Exception {
		FStream<Try<Integer>> stream = FStream.range(0, 5)
												.tryMap(v -> {
													if ( v % 2 == 0 ) {
														throw new Exception();
		                                            }
		                                            else {
		                                                return -v;
													}
												});
		
		FOption<Try<Integer>> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertTrue(r.get().isFailed());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(-1), r.get().get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertTrue(r.get().isFailed());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(-3), r.get().get());
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertTrue(r.get().isFailed());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
	
	@Test
	public void test1() throws Exception {
		FStream<Integer> ostream = FStream.empty();
		FStream<Try<Integer>> stream = ostream.tryMap(v -> {
													if ( v % 2 == 0 ) {
														throw new Exception();
										            }
										            else {
										                return -v;
													}
												});
		
		FOption<Try<Integer>> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}
}
