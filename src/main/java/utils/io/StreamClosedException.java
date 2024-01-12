package utils.io;

import java.io.IOException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class StreamClosedException extends IOException {
	private static final long serialVersionUID = 1L;

	public StreamClosedException(String deatils) {
		super(deatils);
	}
}
