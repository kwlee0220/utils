package utils.async;

import java.util.function.Consumer;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ProgressiveConsumer<P,T> extends Consumer<T>, ProgressReporter<P> {
}
