package utils.jni;


/**
 * JNI를 통해 생성된 native 객체를 Java 측에서 대리하는 proxy 객체의 인터페이스.
 * <p>
 * native 객체는 정수형 포인터 값으로 식별되며, {@link #close()}를 호출하면
 * 해당 native 객체를 해제한다.
 *
 * @author Kang-Woo Lee
 */
public interface JniObjectProxy extends AutoCloseable {
	/**
	 * 본 proxy가 대리하는 native 객체의 포인터 값을 반환한다.
	 *
	 * @return	native 객체 포인터 값. native 객체가 할당되지 않았거나 해제된 경우는 0.
	 */
	public int getJniPointer();
}
