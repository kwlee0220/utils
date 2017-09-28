package utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 *
 * @author Kang-Woo Lee
 */
public class Throwables {
	private Throwables() {
		throw new AssertionError("Should not be invoked!!: class=" + Throwables.class.getName());
	}

	public static Throwable unwrapThrowable(Throwable e) {
		while ( true ) {
			if ( e instanceof InvocationTargetException ) {
				e = ((InvocationTargetException)e).getTargetException();
			}
			else if ( e instanceof UndeclaredThrowableException ) {
				e = ((UndeclaredThrowableException)e).getUndeclaredThrowable();
			}
			else if ( e instanceof ExecutionException ) {
				e = ((ExecutionException)e).getCause();
			}
			else if ( e instanceof CompletionException ) {
				e = ((CompletionException)e).getCause();
			}
			else if ( e instanceof RuntimeException ) {
				Throwable cause = ((RuntimeException)e).getCause();
				if ( cause != null ) {
					e = cause;
				}
				else {
					return e;
				}
			}
			else {
				return e;
			}
		}
	}
	
	public static RuntimeException toRuntimeException(Throwable e) {
		if ( e instanceof RuntimeException ) {
			return (RuntimeException)e;
		}
		else {
			return new RuntimeException(e);
		}
	}
	
	public static <T extends Throwable> void throwIfInstanceOf(Throwable e,
															Class<T> thrCls) throws T {
		if ( thrCls.isInstance(e) ) {
			throw thrCls.cast(e);
		}
	}
}
