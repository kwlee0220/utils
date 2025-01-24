package utils.stream;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import utils.func.FOption;


/**
 * <code>TimedFStream</code>는 {@link FStream#next} 메소드 뿐만 아니라
 * 제한시간이 부가된 next() 함수인 {@link #next(long, TimeUnit)}을 제공하는 인터페이스를 제공한다.
 * <p>
 * {@link #next(long, TimeUnit)}는 주어진 제한 시간동안만 다음 번 데이터를 대기하는 기능을
 * 제공한다. 주어진 시간 내에 데이터가 반환되지 않는 경우에는 {@link FOption#empty()}가 반환된다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface TimedFStream<T> extends FStream<T> {
	/**
	 * 제한 시간 동안 다음 번 데이터을 대기하여 반환한다.
	 * <p>
	 * 주어진 시간 내에 데이터가 반환되지 않는 경우에는 {@link TimeoutException} 예외가 발생된다.
	 * 
	 * @param timeout	제한시간
	 * @param tu		제한 시간 단위
	 * @return	스트림 내 다음 데이터.
	 * 			데이터가 없는 경우에는 {@link FOption#empty()}가 반환된다.
	 * 			제한된 시간 내에 데이터가 반환되지 않는 경우에는 {@link TimeoutException} 예외가 발생된다.
	 * @throws TimeoutException	제한 시간 내에 데이터가 반환되지 않은 경우.
	 */
	public FOption<T> next(long timeout, TimeUnit tu) throws TimeoutException;
}
