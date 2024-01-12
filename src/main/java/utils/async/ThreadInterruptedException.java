package utils.async;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ThreadInterruptedException extends RuntimeException {
	private static final long serialVersionUID = -5990109629307702405L;
	
	public ThreadInterruptedException() {
		super();
	}
	
	public ThreadInterruptedException(String details) {
		super(details);
	}
}
