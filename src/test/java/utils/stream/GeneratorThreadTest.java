package utils.stream;


import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.Holder;
import utils.Suppliable;
import utils.func.CheckedConsumer;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class GeneratorThreadTest {
	@Before
	public void setup() {
	}

	@Test
	public void test00() throws Exception {
		Consumer<Suppliable<String>> generator = new Consumer<Suppliable<String>>() {
			@Override
			public void accept(Suppliable<String> channel) {
				channel.supply("a");
				channel.supply("b");
				channel.supply("c");
				channel.endOfSupply();
			}
		};
		FStream<String> stream = FStream.generate(generator, 4);
		String ret = stream.join("");
		
		Assert.assertEquals("abc", ret);
	}

	@Test
	public void test01() throws Exception {
		Consumer<Suppliable<String>> generator = new Consumer<Suppliable<String>>() {
			@Override
			public void accept(Suppliable<String> channel) {
				channel.supply("a");
				channel.supply("b");
				channel.supply("c");
				channel.endOfSupply();
			}
		};
		FStream<String> stream = FStream.generate(generator, 1);
		String ret = stream.join("");
		
		Assert.assertEquals("abc", ret);
	}

	@Test
	public void test10() throws Exception {
		Consumer<Suppliable<String>> generator = new Consumer<Suppliable<String>>() {
			@Override
			public void accept(Suppliable<String> channel) {
				channel.supply("a");
				channel.supply("b");
				channel.supply("c");
			}
		};
		FStream<String> stream = FStream.generate(generator, 1);
		String ret = stream.join("");
		
		Assert.assertEquals("abc", ret);
	}

	@Test
	public void test11() throws Exception {
		CheckedConsumer<Suppliable<String>> generator = new CheckedConsumer<Suppliable<String>>() {
			@Override
			public void accept(Suppliable<String> channel) throws InterruptedException {
				channel.supply("a");
				channel.supply("b");
				throw new InterruptedException();
			}
		};
		FStream<String> stream = FStream.generate(generator, 1);
		String ret = stream.join("");
		
		Assert.assertEquals("ab", ret);
	}

	@Test
	public void test12() throws Exception {
		Consumer<Suppliable<String>> generator = new Consumer<Suppliable<String>>() {
			@Override
			public void accept(Suppliable<String> channel) {
				channel.supply("a");
				channel.supply("b");
				throw new CancellationException();
			}
		};
		FStream<String> stream = FStream.generate(generator, 1);
		String ret = stream.join("");
		
		Assert.assertEquals("ab", ret);
	}

	@Test(expected=RuntimeException.class)
	public void test13() throws Exception {
		Consumer<Suppliable<String>> generator = new Consumer<Suppliable<String>>() {
			@Override
			public void accept(Suppliable<String> channel) {
				channel.supply("a");
				channel.supply("b");
				throw new RuntimeException();
			}
		};
		FStream<String> stream = FStream.generate(generator, 1);
		String ret = stream.join("");
		
		Assert.assertEquals("abc", ret);
	}

	@Test
	public void test20() throws Exception {
		CheckedConsumer<Suppliable<String>> generator = new CheckedConsumer<Suppliable<String>>() {
			@Override
			public void accept(Suppliable<String> channel) throws InterruptedException {
				channel.supply("a");
				channel.supply("b");
				Thread.sleep(500);
				Assert.fail("should not be here.");
			}
		};
		FStream<String> stream = FStream.generate(generator, 5);
		
		FOption<String> r;
		
		r = stream.next();
		Assert.assertEquals(true, r.isPresent());
		Assert.assertEquals("a", r.get());
		
		stream.close();
		r = stream.next();
		Assert.assertEquals(true, r.isAbsent());
	}

	@Test
	public void test30() throws Exception {
		Holder<Integer> state = Holder.of(0);
		
		long started = System.currentTimeMillis();
		CheckedConsumer<Suppliable<String>> generator = new CheckedConsumer<Suppliable<String>>() {
			@Override
			public void accept(Suppliable<String> channel) throws InterruptedException {
				Thread.sleep(200);
				state.set(1);
				channel.supply("a");

				Thread.sleep(200);
				state.set(2);
				channel.supply("b");
				
				Thread.sleep(200);
				state.set(3);
				channel.supply("c");
			}
		};
		FStream<String> stream = FStream.generate(generator, 3);

		Assert.assertEquals(0, (int)state.get());
		stream.next();
		Assert.assertEquals(1, (int)state.get());
		Assert.assertTrue((System.currentTimeMillis()-started) >= 200);
		
		stream.next();
		Assert.assertEquals(2, (int)state.get());
		Assert.assertTrue((System.currentTimeMillis()-started) >= 400);
		
		stream.forEach(r -> {});
		Assert.assertTrue((System.currentTimeMillis()-started) >= 600);
	}

	@Test(expected=IllegalArgumentException.class)
	public void test90() throws Exception {
		Consumer<Suppliable<String>> generator = null;
		FStream.generate(generator, 1);
	}

	@Test(expected=IllegalArgumentException.class)
	public void test91() throws Exception {
		Consumer<Suppliable<String>> generator = new Consumer<Suppliable<String>>() {
			@Override
			public void accept(Suppliable<String> channel) {
				channel.supply("a");
				channel.supply("b");
				channel.supply("c");
				channel.endOfSupply();
			}
		};
		FStream.generate(generator, 0);
	}
}
