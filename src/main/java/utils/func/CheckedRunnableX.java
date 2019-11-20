package utils.func;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@FunctionalInterface
public interface CheckedRunnableX<X extends Throwable> {
	public void run() throws X;
}
