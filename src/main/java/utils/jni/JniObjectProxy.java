package utils.jni;


/**
 *
 * @author Kang-Woo Lee
 */
public interface JniObjectProxy extends AutoCloseable {
	public int getJniPointer();
}
