package utils.stream;


import org.junit.Assert;
import org.junit.Test;

import utils.func.FOption;
import utils.func.Tuple;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class DistinctTest {
	@Test
	public void test0() throws Exception {
		FStream<Tuple<Integer,Integer>> stream
					= FStream.of(Tuple.of(1,1), Tuple.of(1,2), Tuple.of(2,2),
								Tuple.of(3,3), Tuple.of(3,2), Tuple.of(3,3));
		
		FStream<Integer> istrm = stream.distinct(t -> t._1)
										.map(t -> t._1);
		
		FOption<Integer> r;
		
		r = istrm.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(1), r.get());
		
		r = istrm.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(2), r.get());
		
		r = istrm.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals(Integer.valueOf(3), r.get());
		
		r = istrm.next();
		Assert.assertEquals(true, r.isAbsent());
		
		r = istrm.next();
		Assert.assertEquals(true, r.isAbsent());
	}
}
