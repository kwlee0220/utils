package utils;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@SuppressWarnings("serial")
public class InternalException extends RuntimeException {
	public InternalException(String details) {
		super(details);
	}
	
	public InternalException(Throwable cause) {
		super(cause);
	}
	
	public InternalException(String details, Throwable cause) {
		super(details, cause);
	}
	
	@Override
	public String getMessage() {
		String causeStr = FOption.mapOrElse(getCause(), c -> ", cause=" + c, "");
		return String.format("%s%s", super.getMessage(), causeStr);
	}
}
