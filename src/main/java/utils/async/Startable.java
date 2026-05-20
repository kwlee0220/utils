package utils.async;

import org.jetbrains.annotations.Nullable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Startable {
	/**
	 * 작업을 시작시킨다.
	 * <p>
	 * 본 메소드는 시작된 작업이 종료되기 전에 반환될 수 있다.
	 */
	public void start();
	
	/**
	 * 주어진 객체가 {@link Startable}이면 {@link #start()}를 호출하고 해당 객체를 반환한다.
	 *
	 * @param obj	시작할 객체. {@code null}일 수 있다.
	 * @return	{@code obj}가 {@link Startable}이면 해당 객체, 그렇지 않으면 {@code null}.
	 */
	public static @Nullable Startable startIfStartable(@Nullable Object obj) {
		if ( obj instanceof Startable ) {
			Startable startable = (Startable)obj;
			startable.start();
			return startable;
		}
		else {
			return null;
		}
	}
}
