package utils.stream;

import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import utils.Suppliable;
import utils.func.Unchecked;



/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FlatMapAsyncFStreamTest {
	@BeforeEach
	public void setup() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<FStream<String>> fact = FStream.empty();
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assertions.assertEquals("", ret);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<FStream<String>> fact = FStream.of(FStream.generate(this::generateStreamA, 2));
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assertions.assertEquals("a1a2a3", ret);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2)
										);
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assertions.assertEquals("a1b1a2a3b3", ret);
	}
	
	@Test
	public void test3() throws Exception {
		@SuppressWarnings("unchecked")
		FStream<String>[] strms = new FStream[] {
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
									};
		
		String ret = FStream.range(0, 3)
							.flatMapCheckedAsync(i -> strms[i], AsyncExecutionOptions.create())
							.join("");
		Assertions.assertEquals("a1b1c1a2c2a3b3c3c4", ret);
	}
	
	@Test
	public void test4() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		String ret = FStream.mergeParallel(fact, 2, null).join("");
		Assertions.assertEquals("a1b1a2a3b3c1c2c3c4", ret);
	}
	
	@Test
	public void test5() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		String ret = FStream.mergeParallel(fact, 1, null).join("");
		Assertions.assertEquals("a1a2a3b1b3c1c2c3c4", ret);
	}
	
	@Test
	public void test10() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamAX, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assertions.assertEquals("a1b1c1a2c2b3c3c4", ret);
	}
	
	@Test
	public void test90() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream.mergeParallel(null, 8, null).join("");
			});
	}

	@Test
	public void test91() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<FStream<String>> fact = FStream.of(
												FStream.generate(this::generateStreamA, 2),
												FStream.generate(this::generateStreamB, 2),
												FStream.generate(this::generateStreamC, 2)
											);
			FStream.mergeParallel(fact, 0, null).join("");
			});
	}
	
	private void generateStreamA(Suppliable<String> channel) throws ExecutionException, InterruptedException {
		channel.supply("a1");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("a2");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("a3");
		channel.endOfSupply();
	}
	private void generateStreamAX(Suppliable<String> channel) throws Exception {
		channel.supply("a1");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("a2");
		throw new Exception();
	}
	
	private void generateStreamB(Suppliable<String> channel) throws ExecutionException, InterruptedException {
		Unchecked.runOrIgnore(() -> Thread.sleep(35));
		channel.supply("b1");
		Unchecked.runOrIgnore(() -> Thread.sleep(200));
		channel.supply("b3");
		channel.endOfSupply();
	}
	
	private void generateStreamC(Suppliable<String> channel) throws ExecutionException, InterruptedException {
		Unchecked.runOrIgnore(() -> Thread.sleep(70));
		channel.supply("c1");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("c2");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("c3");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("c4");
		channel.endOfSupply();
	}
	
	@Test
	public void test30() throws Exception {
		m_count = 0;
		AsyncExecutionOptions options = AsyncExecutionOptions.KEEP_ORDER();
		String ret = FStream.of("a", "b", "c", "d")
							.flatMapCheckedAsync(v -> FStream.generate(new SimpleGenerator(v), 2), options)
							.join("");
		
		Assertions.assertEquals("a0a1a2b0b1b2c0c1c2d0d1d2", ret);
		Assertions.assertEquals(4, m_count);
	}
	
	@Test
	public void test31() throws Exception {
		m_count = 0;
		AsyncExecutionOptions options = AsyncExecutionOptions.KEEP_ORDER();
		String ret = FStream.of("a", "b", "c", "d")
							.flatMapCheckedAsync(v -> FStream.generate(new SimpleGenerator(v), 2), options)
							.take(5)
							.join("");
		
		Assertions.assertEquals("a0a1a2b0b1", ret);
		Assertions.assertEquals(1, m_count);
	}
	
	@Test
	public void test32() throws Exception {
		m_count = 0;
		AsyncExecutionOptions options = AsyncExecutionOptions.KEEP_ORDER();
		String ret = FStream.of("a", "b", "c", "d")
							.flatMapCheckedAsync(v -> FStream.generate(new SimpleGenerator(v), 2), options)
							.take(6)
							.join("");
		
		Assertions.assertEquals("a0a1a2b0b1b2", ret);
		Assertions.assertEquals(2, m_count);
	}
	
	private int m_count = 0;
	private class SimpleGenerator implements Generator<String> {
		private final String m_tag;
		
		SimpleGenerator(String tag) {
			m_tag = tag;
		}
		
		@Override
		public void generate(Suppliable<String> outChannel) throws Exception {
			for ( int i =0; i < 3; ++i ) {
				Unchecked.runOrIgnore(() -> Thread.sleep(70));
				outChannel.supply(String.format("%s%d", m_tag, i));
			}
			++m_count;
		}
	};
}
