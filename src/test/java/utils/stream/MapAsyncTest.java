package utils.stream;


import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.func.Try;
import utils.func.Tuple;
import utils.func.Tuple3;
import utils.func.Unchecked;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class MapAsyncTest {
	private Random m_random;
	
	@Before
	public void setup() {
		m_random = new Random(System.nanoTime());
	}
	
	@Test
	public void test0() throws Exception {
		Function<Tuple<Integer,Integer>,Tuple3<Integer,Long,Integer>> mapper = (tup) -> {
			long now = System.currentTimeMillis();
			Unchecked.runOrRTE(() -> Thread.sleep(tup._2));
			return Tuple.of(tup._1, now, tup._2);
		};
		
		List<Try<Tuple3<Integer,Long,Integer>>> result
			= FStream.range(0, 8)
					.map(i -> generate(i))
					.mapAsync(mapper, 3)
					.toList();

		Assert.assertEquals(8, result.size());
		Assert.assertEquals(true, FStream.from(result).forAll(t -> t.isSuccess()));
		long[] endTss = FStream.from(result).flatMapTry(t -> t).mapToLong(t -> t._2+t._3).toArray();
		long[] sorteds = Arrays.copyOf(endTss, endTss.length);
		Arrays.sort(sorteds);
		Assert.assertArrayEquals(sorteds, endTss);
	}
	
	@Test
	public void test1() throws Exception {
		Function<Tuple<Integer,Integer>,Tuple3<Integer,Long,Integer>> mapper = (tup) -> {
			long now = System.currentTimeMillis();
			Unchecked.runOrRTE(() -> Thread.sleep(tup._2));
			return Tuple.of(tup._1, now, tup._2);
		};
		
		FStream<Try<Tuple3<Integer,Long,Integer>>> result
			= FStream.range(0, 16).map(i -> generate(i)).mapAsync(mapper, 3);
		result.next();
		result.next();
		result.close();
		Assert.assertEquals(true, result.next().isAbsent());
		
	}
	
	@Test
	public void test2() throws Exception {
		Function<Tuple<Integer,Integer>,Tuple3<Integer,Long,Integer>> mapper = (tup) -> {
			long now = System.currentTimeMillis();
			if ( tup._1 % 3 == 1 ) {
				long sleepTime =tup._2/3;
				Unchecked.runOrRTE(() -> Thread.sleep(sleepTime));
				throw new RuntimeException(""+tup._1);
			}
			else {
				Unchecked.runOrRTE(() -> Thread.sleep(tup._2));
				return Tuple.of(tup._1, now, tup._2);
			}
		};
		
		List<Try<Tuple3<Integer,Long,Integer>>> result
			= FStream.range(0, 16)
					.map(i -> generate(i))
					.mapAsync(mapper, 3)
					.toList();

		Assert.assertEquals(16, result.size());
		
		Assert.assertEquals(11, FStream.from(result).flatMapTry(t -> t).count());
		FStream.from(result)
				.filter(t -> t.isFailure())
				.map(t -> t.getCause().getLocalizedMessage())
				.map(msg -> Integer.parseInt(msg))
				.forEach(m -> Assert.assertTrue(m%3 == 1));
	}
	
	private Tuple<Integer,Integer> generate(int idx) {
		return Tuple.of(idx, 70 + m_random.nextInt(25) * 10);
	}
}
