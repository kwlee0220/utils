package utils.stream;


import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.UnitUtils;
import utils.func.Try;
import utils.func.Unchecked;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MapAsyncTest {
	private Random m_random;
	
	private static class Desc {
		private int m_seqNo;
		private long m_startedMills;
		private int m_sleepMillis;
		private long m_totalElapsed;
		
		Desc(int seqNo, int sleepMillis) {
			m_seqNo = seqNo;
			m_sleepMillis = sleepMillis;
		}
		Desc(int seqNo, long started, int sleepMillis, long totalElapsed) {
			m_seqNo = seqNo;
			m_startedMills = started;
			m_sleepMillis = sleepMillis;
			m_totalElapsed = totalElapsed;
		}
		
		@Override
		public String toString() {
			return String.format("%d: sleep=%d, total=%s", m_seqNo, m_sleepMillis,
									UnitUtils.toMillisString(m_totalElapsed));
		}
	};
	
	@Before
	public void setup() {
		m_random = new Random(System.nanoTime());
	}
	
	@Test
	public void test0() throws Exception {
		long started = System.currentTimeMillis();
		
		Function<Desc,Desc> mapper = (desc) -> {
			long now = System.currentTimeMillis();
			Unchecked.runOrRTE(() -> Thread.sleep(desc.m_sleepMillis));
			return new Desc(desc.m_seqNo, now, desc.m_sleepMillis, System.currentTimeMillis()-started);
		};
		
		List<Try<Desc>> result = FStream.range(0, 8)
										.map(i -> generate(i))
										.mapAsync(mapper, AsyncExecutionOptions.WORKER_COUNT(3))
										.toList();
		Assert.assertEquals(8, result.size());
		Assert.assertEquals(true, FStream.from(result).forAll(t -> t.isSuccessful()));
		
		long[] endTss = FStream.from(result)
								.flatMapTry(t -> t)
								.mapToLong(d -> d.m_startedMills+d.m_sleepMillis)
								.toArray();
		long[] sorteds = Arrays.copyOf(endTss, endTss.length);
		Arrays.sort(sorteds);
		Assert.assertArrayEquals(sorteds, endTss);
	}
	
	@Test(expected = IllegalStateException.class)
	public void test1() throws Exception {
		long started = System.currentTimeMillis();
		
		Function<Desc,Desc> mapper = (desc) -> {
			long now = System.currentTimeMillis();
			Unchecked.runOrRTE(() -> Thread.sleep(desc.m_sleepMillis));
			return new Desc(desc.m_seqNo, now, desc.m_sleepMillis, System.currentTimeMillis()-started);
		};
		
		FStream<Try<Desc>> result
			= FStream.range(0, 16).map(i -> generate(i)).mapAsync(mapper, AsyncExecutionOptions.WORKER_COUNT(3));
		result.next();
		result.next();
		result.close();
		result.next();
		
	}
	
	@Test
	public void test2() throws Exception {
		long started = System.currentTimeMillis();
		
		Function<Desc,Desc> mapper = (desc) -> {
			long now = System.currentTimeMillis();
			if ( desc.m_seqNo % 3 == 1 ) {
				long sleepTime =desc.m_sleepMillis/3;
				Unchecked.runOrRTE(() -> Thread.sleep(sleepTime));
				throw new RuntimeException(""+desc.m_seqNo);
			}
			else {
				Unchecked.runOrRTE(() -> Thread.sleep(desc.m_sleepMillis));
				return new Desc(desc.m_seqNo, now, desc.m_sleepMillis, System.currentTimeMillis()-started);
			}
		};
		
		List<Try<Desc>> result
			= FStream.range(0, 16)
					.map(i -> generate(i))
					.mapAsync(mapper, AsyncExecutionOptions.WORKER_COUNT(3))
					.toList();

		Assert.assertEquals(16, result.size());
		
		Assert.assertEquals(11, FStream.from(result).flatMapTry(t -> t).count());
		FStream.from(result)
				.filter(t -> t.isFailed())
				.map(t -> t.getCause().getLocalizedMessage())
				.map(msg -> Integer.parseInt(msg))
				.forEach(m -> Assert.assertTrue(m%3 == 1));
	}
	
	private Desc generate(int idx) {
		return new Desc(idx, 70 + m_random.nextInt(15) * 50);
	}
	
	private int sleepAndReturn(int idx, long time) {
		Unchecked.runOrRTE(() -> Thread.sleep(time));
		return idx;
	}
	
	@Test
	public void test10() throws Exception {
		AsyncExecutionOptions options = AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(3);
		
		int[] result = FStream.of(100L, 200L, 300L, 400L, 500L, 600L, 700L)
								.zipWithIndex()
								.mapAsync(t -> sleepAndReturn(t._2, t._1), options)
								.peek(t -> Assert.assertTrue(t.isSuccessful()))
								.flatMapTry(t -> t)
								.mapToInt(v -> v)
								.toArray();
		int[] answer = FStream.range(0, 7).toArray();
		Assert.assertArrayEquals(answer, result);
	}
	
	@Test
	public void test11() throws Exception {
		AsyncExecutionOptions options = AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(3);
		
		int[] result = FStream.of(700L, 600L, 500L, 400L, 300L, 200L, 100L, 0L)
								.zipWithIndex()
								.mapAsync(t -> sleepAndReturn(t._2, t._1), options)
								.peek(t -> Assert.assertTrue(t.isSuccessful()))
								.flatMapTry(t -> t)
								.mapToInt(v -> v)
								.toArray();
		int[] answer = FStream.range(0, 8).toArray();
		Assert.assertArrayEquals(answer, result);
	}
	
	@Test
	public void test12() throws Exception {
		AsyncExecutionOptions options = AsyncExecutionOptions.KEEP_ORDER().setWorkerCount(3);
		
		int[] result = FStream.<Long>of()
								.zipWithIndex()
								.mapAsync(t -> sleepAndReturn(t._2, t._1), options)
								.peek(t -> Assert.assertTrue(t.isSuccessful()))
								.flatMapTry(t -> t)
								.mapToInt(v -> v)
								.toArray();
		Assert.assertArrayEquals(new int[0], result);
	}
}
