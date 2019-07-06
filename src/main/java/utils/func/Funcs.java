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
	
	public static <T> T getIf(boolean flag, T trueCase, T falseCase) {
		return (flag) ? trueCase : falseCase;
	}
	
	public static <T> T getIfNotNull(Object obj, T trueCase, T falseCase) {
		return (obj != null) ? trueCase : falseCase;
	}
}
