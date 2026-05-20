package utils.stream;


import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import utils.Holder;
import utils.Suppliable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class GeneratorThreadTest {
	@BeforeEach
	public void setup() {
	}

	@Test
	public void test00() throws Exception {
		Generator<String> generator = new Generator<String>() {
			@Override
			public void generate(Suppliable<String> channel) throws InterruptedException, ExecutionException {
				channel.supply("a");
				channel.supply("b");
				channel.supply("c");
				channel.endOfSupply();
			}
		};
		FStream<String> stream = FStream.generate(generator, 4);
		String ret = stream.join("");
		
		Assertions.assertEquals("abc", ret);
	}

	@Test
	public void test01() throws Exception {
		Generator<String> generator = new Generator<String>() {
			@Override
			public void generate(Suppliable<String> channel) throws InterruptedException, ExecutionException {
				channel.supply("a");
				channel.supply("b");
				channel.supply("c");
				channel.endOfSupply();
			}
		};
		FStream<String> stream = FStream.generate(generator, 1);
		String ret = stream.join("");
		
		Assertions.assertEquals("abc", ret);
	}

	@Test
	public void test10() throws Exception {
		Generator<String> generator = new Generator<String>() {
			@Override
			public void generate(Suppliable<String> channel) throws InterruptedException, ExecutionException {
				channel.supply("a");
				channel.supply("b");
				channel.supply("c");
			}
		};
		FStream<String> stream = FStream.generate(generator, 1);
		String ret = stream.join("");
		
		Assertions.assertEquals("abc", ret);
	}

	@Test
	public void test11() throws Exception {
		Generator<String> generator = new Generator<String>() {
			@Override
			public void generate(Suppliable<String> channel) throws InterruptedException, ExecutionException {
				channel.supply("a");
				channel.supply("b");
				throw new InterruptedException();
			}
		};
		FStream<String> stream = FStream.generate(generator, 1);
		String ret = stream.join("");
		
		Assertions.assertEquals("ab", ret);
	}

	@Test
	public void test12() throws Exception {
		Generator<String> generator = new Generator<String>() {
			@Override
			public void generate(Suppliable<String> channel) throws InterruptedException, ExecutionException {
				channel.supply("a");
				channel.supply("b");
				throw new CancellationException();
			}
		};
		FStream<String> stream = FStream.generate(generator, 1);
		String ret = stream.join("");
		
		Assertions.assertEquals("ab", ret);
	}

	@Test
	public void test13() throws Exception {
		Assertions.assertThrows(RuntimeException.class, () -> {
			Generator<String> generator = new Generator<String>() {
				@Override
				public void generate(Suppliable<String> channel) throws InterruptedException, ExecutionException {
					channel.supply("a");
					channel.supply("b");
					throw new RuntimeException();
				}
			};
			FStream<String> stream = FStream.generate(generator, 1);
			String ret = stream.join("");
		
			Assertions.assertEquals("abc", ret);
			});
	}

	@Test
	public void test30() throws Exception {
		Holder<Integer> state = Holder.of(0);
		
		long started = System.currentTimeMillis();
		Generator<String> generator = new Generator<String>() {
			@Override
			public void generate(Suppliable<String> channel) throws InterruptedException, ExecutionException {
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

		Assertions.assertEquals(0, (int)state.get());
		stream.next();
		Assertions.assertEquals(1, (int)state.get());
		Assertions.assertTrue((System.currentTimeMillis()-started) >= 200);
		
		stream.next();
		Assertions.assertEquals(2, (int)state.get());
		Assertions.assertTrue((System.currentTimeMillis()-started) >= 400);
		
		stream.forEach(r -> {});
		Assertions.assertTrue((System.currentTimeMillis()-started) >= 600);
	}

	@Test
	public void test90() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Generator<String> generator = null;
			FStream.generate(generator, 1);
			});
	}

	@Test
	public void test91() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Generator<String> generator = new Generator<String>() {
				@Override
				public void generate(Suppliable<String> channel) throws InterruptedException, ExecutionException {
					channel.supply("a");
					channel.supply("b");
					channel.supply("c");
					channel.endOfSupply();
				}
			};
			FStream.generate(generator, 0);
			});
	}
}
