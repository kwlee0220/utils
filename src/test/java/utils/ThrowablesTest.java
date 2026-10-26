package utils;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

class ThrowablesTest {
	@Test
	void preservesDomainExceptionAndItsCause() {
		IllegalStateException domain = new IllegalStateException("domain details", new IOException("transport"));
		assertSame(domain, Throwables.unwrapThrowable(domain));
		assertSame(domain, Throwables.unwrapThrowable(new ExecutionException(new CompletionException(domain))));
	}

	@Test
	void unwrapsExplicitWrappersAndPlainRuntimeWrapper() {
		IOException cause = new IOException("original");
		assertSame(cause, Throwables.unwrapThrowable(new InvocationTargetException(
				new ExecutionException(new RuntimeException(cause)))));
	}

	@Test
	void preservesWrapperWithoutCause() {
		RuntimeInterruptedException wrapper = new RuntimeInterruptedException((InterruptedException)null);
		assertSame(wrapper, Throwables.unwrapThrowable(wrapper));
	}
}
