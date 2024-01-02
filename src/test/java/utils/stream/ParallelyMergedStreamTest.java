package utils.stream;


import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class ParallelyMergedStreamTest {
	
	@Before
	public void setup() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<FStream<String>> gen = FStream.range(0, 9).map(i -> createSource("" + i));
		FStream<String> stream = FStream.mergeParallel(gen, 5);

		FOption<String> r;
		
		int i =0;
		while ( (r = stream.next()).isPresent() ) {
			++i;
			if ( i == 10 ) {
				stream.close();
			}
		}
		
		stream.close();
	}
	
	private FStream<String> createSource(String seed) {
		Random random = new Random(System.currentTimeMillis());
		return FStream.generate(seed, i -> {
			try {
				Thread.sleep(50 + random.nextInt(40)*10);
				return i + seed;
			}
			catch ( InterruptedException e ) {
				throw new RuntimeException(e);
			}
		})
		.take(1 + random.nextInt(10))
		.concatWith("*" + seed);
	}
}
