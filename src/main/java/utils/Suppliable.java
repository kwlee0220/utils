package utils;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Suppliable<T> {
	public boolean supply(T data) throws IllegalStateException;
	public boolean supply(T data, long timeout, TimeUnit tu) throws IllegalStateException;
	public void endOfSupply();
	public void endOfSupply(Throwable error);
}
