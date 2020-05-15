package utils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface PicocliCommand<T> extends Runnable {
	public T getInitialContext();
}