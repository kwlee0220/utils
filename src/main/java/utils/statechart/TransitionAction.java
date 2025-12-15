package utils.statechart;

import java.util.function.BiConsumer;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface TransitionAction<C extends StateContext> extends BiConsumer<C, Signal> {

}
