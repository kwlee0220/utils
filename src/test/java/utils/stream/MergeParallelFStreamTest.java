package utils.stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.Suppliable;
import utils.func.Unchecked;



/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MergeParallelFStreamTest {
	@Before
	public void setup() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<FStream<String>> fact = FStream.empty();
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assert.assertEquals("", ret);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<FStream<String>> fact = FStream.of(FStream.generate(this::generateStreamA, 2));
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assert.assertEquals("a1a2a3", ret);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2)
										);
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assert.assertEquals("a1b1a2a3b3", ret);
	}
	
	@Test
	public void test3() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assert.assertEquals("a1b1c1a2c2a3b3c3c4", ret);
	}
	
	@Test
	public void test4() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		String ret = FStream.mergeParallel(fact, 2, null).join("");
		Assert.assertEquals("a1b1a2a3b3c1c2c3c4", ret);
	}
	
	@Test
	public void test5() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		String ret = FStream.mergeParallel(fact, 1, null).join("");
		Assert.assertEquals("a1a2a3b1b3c1c2c3c4", ret);
	}
	
	@Test
	public void test10() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamAX, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assert.assertEquals("a1b1c1a2c2b3c3c4", ret);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test90() throws Exception {
		FStream.mergeParallel(null, 8, null).join("");
	}

	@Test(expected=IllegalArgumentException.class)
	public void test91() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		FStream.mergeParallel(fact, 0, null).join("");
	}
	
	private void generateStreamA(Suppliable<String> channel) {
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
	
	private void generateStreamB(Suppliable<String> channel) {
		Unchecked.runOrIgnore(() -> Thread.sleep(35));
		channel.supply("b1");
		Unchecked.runOrIgnore(() -> Thread.sleep(200));
		channel.supply("b3");
		channel.endOfSupply();
	}
	
	private void generateStreamC(Suppliable<String> channel) {
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
}
