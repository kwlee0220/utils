package utils;

import java.io.IOException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface PicocliCommand<T> extends Runnable {
	public T getInitialContext() throws Exception;
	public void configureLog4j() throws IOException;
}