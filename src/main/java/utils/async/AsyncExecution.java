package utils.async;

import utils.thread.ExecutorAware;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncExecution<T> extends Execution<T>, ExecutorAware {
	public void start();
}
