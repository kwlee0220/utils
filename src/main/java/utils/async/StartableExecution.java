package utils.async;

import utils.thread.ExecutorAware;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface StartableExecution<T> extends Execution<T>, ExecutorAware {
	public void start();
}
