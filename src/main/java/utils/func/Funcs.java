package utils.func;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Funcs {
	private Funcs() {
		throw new AssertionError("Should not be called: class=" + Funcs.class);
	}
	
	public static <T> void when(boolean flag, Runnable work) {
		if ( flag ) {
			work.run();
		}
	}
}
