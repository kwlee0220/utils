package utils;

import java.util.concurrent.TimeoutException;

/**
 * 
 * @author Kang-Woo Lee
 */
public class RuntimeTimeoutException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public RuntimeTimeoutException(TimeoutException e) {
		super(e);
	}
}
