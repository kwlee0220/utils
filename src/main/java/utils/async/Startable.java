package utils.async;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Startable {
	/**
	 * 작업을 시작시킨다.
	 * <p>
	 * 본 메소드은 시작된 작업이 종료되기 전에 반환될 수 있다.
	 */
	public void start();
	
	public static Startable startIfStartable(Object obj) {
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
