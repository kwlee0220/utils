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
	
	public default void destroyQuietly() {
		try {
			destroy();
		}
		catch ( Throwable ignored ) {}
	}
	
	public static void destroyQuietly(Object... objs) {
		Arrays.stream(objs)
				.filter(obj -> obj instanceof Initializable)
				.forEach(obj -> ((Initializable)obj).destroyQuietly());
	}
}
