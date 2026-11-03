package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;


/**
 * utils.func의 예외 처리 규칙을 확인한다.
 * <ul>
 *   <li>{@link Exception}은 잡아 {@link Try#failure(Throwable)}나 실패 처리기로 넘기고, {@link Error}는 그대로 전파한다.</li>
 *   <li>{@code *OrRTE}·{@code getUnchecked}는 원인을 한 번만 감싼다(검사 예외만 {@link RuntimeException}으로).</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ExceptionPolicyTest {
	private static final IOException IO = new IOException("io");
	private static final IllegalStateException ISE = new IllegalStateException("ise");

	@Test
	public void uncheckedOrRteWrapsCheckedOnceAndRethrowsError() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> Unchecked.getOrRTE(() -> { throw IO; }));
		assertSame(IO, e.getCause());
		assertSame(ISE, assertThrows(IllegalStateException.class, () -> Unchecked.runOrRTE(() -> { throw ISE; })));
		StackOverflowError err = new StackOverflowError();
		assertSame(err, assertThrows(StackOverflowError.class, () -> Unchecked.applyOrRTE(1, i -> { throw err; })));
	}

	@Test
	public void resultGetUncheckedThrowsCauseDirectly() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> Result.failure(IO).getUnchecked());
		assertSame(IO, e.getCause());
		assertSame(ISE, assertThrows(IllegalStateException.class, () -> Result.failure(ISE).getUnchecked()));
		assertThrows(CancellationException.class, () -> Result.cancelled().getUnchecked());
		assertEquals("a", Result.success("a").getUnchecked());
	}

	@Test
	public void tryMapAndFlatMapCaptureMapperExceptions() {
		Try<Integer> mapped = Try.success(1).map(i -> { throw ISE; });
		assertTrue(mapped.isFailed());
		assertSame(ISE, mapped.getCause());

		Try<Integer> flat = Try.success(1).flatMap(i -> { throw ISE; });
		assertTrue(flat.isFailed());
		assertSame(ISE, flat.getCause());

		StackOverflowError err = new StackOverflowError();
		assertSame(err, assertThrows(StackOverflowError.class, () -> Try.success(1).map(i -> { throw err; })));
	}

	@Test
	public void lazyRejectsNullSupplierResult() {
		Lazy<String> lazy = Lazy.of(() -> null);
		assertThrows(IllegalStateException.class, lazy::get);
	}

	@Test
	public void checkedTryMethodsLetErrorsPropagate() {
		StackOverflowError err = new StackOverflowError();
		CheckedSupplier<String> s = () -> { throw err; };
		assertSame(err, assertThrows(StackOverflowError.class, s::tryGet));
		CheckedSupplierX<String,IOException> sx = () -> { throw err; };
		assertSame(err, assertThrows(StackOverflowError.class, sx::tryGet));

		CheckedSupplierX<String,IOException> io = () -> { throw IO; };
		assertSame(IO, io.tryGet().getCause());
		CheckedFunction<String,String> f = v -> { throw IO; };
		assertSame(IO, f.tryApply("a").getCause());
	}

	@Test
	public void uncheckedWrappersDoNotPassErrorsToHandler() {
		StackOverflowError err = new StackOverflowError();
		FailureHandlers.CountingFailureHandler<Object> count = FailureHandlers.count();
		assertSame(err, assertThrows(StackOverflowError.class,
									() -> UncheckedConsumer.<String>lift(v -> { throw err; }, count).accept("a")));
		assertEquals(0, count.getErrorCount());
		UncheckedConsumer.<String>lift(v -> { throw IO; }, count).accept("a");
		assertEquals(1, count.getErrorCount());
	}
}
