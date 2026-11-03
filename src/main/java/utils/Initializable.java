package utils;

import java.util.Arrays;

/**
 * {@literal Component} defines the interface of component instances managed by CAMUS.
 * <p>
 * Every component instance may have more than one property.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Initializable {
	/**
	 * Initialize this component instance.
	 * <p>
	 * This method is called after all its properties
	 * are successfully set except the optional ones.
	 * Validation of the properties is totally up to this initialization, therefore
	 * it finds any missing mandatory properties during the initialization,
	 * it is supposed to raise the {@link UninitializedException}.
	 * For any reason, if this component instance wants to hold off its initialization,
	 * this method is supposed to raise the exception {@link NotReadyException}.
	 * Then, the component manager will retry to invoke this method later.
	 * <p>
	 * Returned without any exception, its ComponentManager considers that
	 * this component instance gets ready to serve.
	 * <p>
	 * Since the component manager will try this method several times,
	 * this method should be implemented idempotent until it completes
	 * its initialization.
	 * 
	 * @throws UninitializedException	if some mandatory properties are not set.
	 * @throws NotReadyException	if this component instance is not ready to complete
	 * 									its initialization.
	 * @throws Exception	if initialization is failed due to some reason.
	 */
	public void initialize() throws Exception;
	
	/**
	 * Shutdown this component instance and release, if any, all the resources allocated.
	 * <p>
	 * This is called when it decides to destroy this component instance.
	 * 
	 * @throws Exception	if any failure occurs during the shutdown.
	 */
	public void destroy() throws Exception;
	
	/**
	 * {@link #destroy()}를 호출하되, 발생하는 모든 {@link Throwable}({@link Error} 포함)을 무시한다.
	 */
	public default void destroyQuietly() {
		try {
			destroy();
		}
		catch ( Throwable ignored ) {}
	}
	
	/**
	 * 주어진 객체들 중 {@link Initializable}인 것에 대해 {@link #destroyQuietly()}를 차례로 호출한다.
	 * {@link Initializable}이 아니거나 {@code null}인 원소는 건너뛴다.
	 *
	 * @param objs	정리할 객체들. 배열 자체가 {@code null}이면 {@link NullPointerException}이 발생한다.
	 */
	public static void destroyQuietly(Object... objs) {
		Arrays.stream(objs)
				.filter(obj -> obj instanceof Initializable)
				.forEach(obj -> ((Initializable)obj).destroyQuietly());
	}
}
