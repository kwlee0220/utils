package utils.func;

import java.util.function.Supplier;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface OptionSupplier<T> extends Supplier<Option<T>> {
	public static <T> OptionSupplier<T> from(Supplier<Option<? extends T>> suppl) {
		return () -> suppl.get().map(t -> t);
	}
}
