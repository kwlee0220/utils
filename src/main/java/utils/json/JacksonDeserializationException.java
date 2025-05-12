package utils.json;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@SuppressWarnings("serial")
public class JacksonDeserializationException extends RuntimeException {
	public JacksonDeserializationException(String details) {
		super(details);
	}
	
	public JacksonDeserializationException(Throwable cause) {
		super(cause);
	}
	
	public JacksonDeserializationException(String details, Throwable cause) {
		super(details, cause);
	}
	
	@Override
	public String getMessage() {
		String causeStr = FOption.mapOrElse(getCause(), c -> ", cause=" + c, "");
		return String.format("%s%s", super.getMessage(), causeStr);
	}
}
