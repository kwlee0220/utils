package utils;

import org.slf4j.Logger;

/**
 * 
 * @author Kang-Woo Lee
 */
public interface LoggerSettable {
	public Logger getLogger();
	public void setLogger(Logger logger);
}
