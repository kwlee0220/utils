package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;


/**
 * {@code Unchecked*} 래퍼가 실패를 처리기에 넘기는 동작을 확인한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UncheckedWrapperTest {
	private static final IOException CAUSE = new IOException("x");

	/**
	 * 값을 돌려주는 래퍼는 처리기가 예외를 던지지 않고 반환하면 원래 예외를 원인으로 하는
	 * IllegalStateException을 던진다.
	 */
	@Test
	public void valueReturningWrappersFailIfHandlerReturns() {
		FailureHandler<Object> count = FailureHandlers.count();

		IllegalStateException e1 = assertThrows(IllegalStateException.class,
				() -> UncheckedFunction.<String,String>lift(s -> { throw CAUSE; }, count).apply("a"));
		assertSame(CAUSE, e1.getCause());

		IllegalStateException e2 = assertThrows(IllegalStateException.class,
				() -> UncheckedSupplier.<String>lift(() -> { throw CAUSE; }, FailureHandlers.count()).get());
		assertSame(CAUSE, e2.getCause());

		IllegalStateException e3 = assertThrows(IllegalStateException.class,
				() -> UncheckedPredicate.<String>lift(s -> { throw CAUSE; }, count).test("a"));
		assertSame(CAUSE, e3.getCause());

		IllegalStateException e4 = assertThrows(IllegalStateException.class,
				() -> UncheckedBiFunction.<String,String,String>lift((a, b) -> { throw CAUSE; },
																	FailureHandlers.count()).apply("a", "b"));
		assertSame(CAUSE, e4.getCause());
	}

	@Test
	public void sneakyThrowRethrowsOriginalException() {
		IOException e = assertThrows(IOException.class,
				() -> UncheckedFunction.<String,String>sneakyThrow(s -> { throw CAUSE; }).apply("a"));
		assertSame(CAUSE, e);
	}

	@Test
	public void voidWrappersMayIgnoreFailures() {
		UncheckedConsumer.<String>ignore(s -> { throw CAUSE; }).accept("a");
		UncheckedRunnable.ignore(() -> { throw CAUSE; }).run();

		FailureHandlers.CountingFailureHandler<Object> count = FailureHandlers.count();
		UncheckedConsumer.<String>lift(s -> { throw CAUSE; }, count).accept("a");
		assertEquals(1, count.getErrorCount());
	}
}
