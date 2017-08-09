package utils.async;

import java.util.function.Function;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ProgressiveFunction<P,I,O> extends Function<I,O>, ProgressReporter<P> {
}
