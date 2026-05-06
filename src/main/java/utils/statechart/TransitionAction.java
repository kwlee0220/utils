package utils.statechart;

import java.util.function.BiConsumer;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface TransitionAction<C extends StateContext<C>> extends BiConsumer<C, Signal> {

}
