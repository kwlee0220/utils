package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;


/**
 * {@link FailureHandlers}와 {@link FailureCase} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FailureHandlersTest {
	@Test
	public void countingHandlerIsThreadSafe() throws Exception {
		FailureHandlers.CountingFailureHandler<Object> count = FailureHandlers.count();
		ExecutorService exector = Executors.newFixedThreadPool(4);
		try {
			for ( int i = 0; i < 4; ++i ) {
				exector.submit(() -> {
					for ( int j = 0; j < 10_000; ++j ) {
						count.handle(new FailureCase<>(null, new IOException()));
					}
				});
			}
		}
		finally {
			exector.shutdown();
			assertTrue(exector.awaitTermination(30, TimeUnit.SECONDS));
		}
		assertEquals(40_000, count.getErrorCount());
	}

	@Test
	public void failureCaseToStringShowsDataAndCause() {
		String str = new FailureCase<>("in", new IOException("x")).toString();
		assertTrue(str.contains("in") && str.contains("IOException"), str);
	}
}
