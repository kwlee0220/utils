package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Unchecked {
	public static void runOrIgnore(CheckedRunnable checked) {
		UncheckedRunnable.ignore(checked).run();
	}
	public static void runOrThrowSneakily(CheckedRunnable checked) {
		checked.tryRun().get();
	}
	public static void runOrRTE(CheckedRunnable checked) {
		checked.tryRun().mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}

	public static <T> void acceptOrIgnore(CheckedConsumer<? super T> checked, T data) {
		UncheckedConsumer.ignore(checked).accept(data);
	}
	public static <T> void acceptOrThrowSneakily(CheckedConsumer<? super T> checked, T data) {
		checked.tryAccept(data).get();
	}
	public static <T> void acceptOrRTE(CheckedConsumer<? super T> checked, T data) {
		checked.tryAccept(data).mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}

	public static <T> T getOrIgnore(CheckedSupplier<? extends T> checked) {
		return UncheckedSupplier.ignore(checked).get();
	}
	public static <T> T getOrThrowSneakily(CheckedSupplier<? extends T> checked) {
		return checked.tryGet().get();
	}
	public static <T> T getOrRTE(CheckedSupplier<? extends T> checked) {
		return checked.tryGet().mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}

	public static <T,R> R applyOrIgnore(CheckedFunction<? super T, ? extends R> checked, T input) {
		return UncheckedFunction.ignore(checked).apply(input);
	}
	public static <T,R> R applyOrThrowSneakily(CheckedFunction<? super T, ? extends R> checked, T input) {
		return checked.tryApply(input).get();
	}
	public static <T,R> R applyOrRTE(CheckedFunction<? super T, ? extends R> checked, T input) {
		return checked.tryApply(input).mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}

	public static<T> boolean testOrIgnore(CheckedPredicate<? super T> checked, T input) {
		return UncheckedPredicate.ignore(checked).test(input);
	}
	public static <T> boolean testOrThrowSneakily(CheckedPredicate<? super T> checked, T input) {
		return checked.tryTest(input).get();
	}
	public static <T> boolean testOrRTE(CheckedPredicate<? super T> checked, T input) {
		return checked.tryTest(input).mapFailure(cause -> {
			if ( cause instanceof RuntimeException ) {
				return (RuntimeException)cause;
			}
			else {
				return new RuntimeException(cause);
			}
		}).get();
	}
}