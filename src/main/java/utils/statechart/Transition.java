package utils.statechart;

import java.util.Optional;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface Transition<C extends StateContext> {
	public Optional<String> getTargetStatePath();
	public void execute(C context, Signal signal);
}