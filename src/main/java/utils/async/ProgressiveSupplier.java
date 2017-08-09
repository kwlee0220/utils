package utils.async;

import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ProgressiveSupplier<P,T> extends Supplier<T>, ProgressReporter<P> {
}
