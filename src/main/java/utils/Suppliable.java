package utils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Suppliable<T> {
	/**
	 * 데이터를 제공한다.
	 * <p>
	 * 메소드 호출은 제공 받는 객체의 상황에 따라 호출 쓰레드가 일시 대기할 수 있음.
	 * 만일 쓰레드 대기 중에 강제로 종료된 경우는 {@link InterruptedException} 예외가
	 * 발생된다.
	 * 이미 {@link #endOfSupply()} 또는 {@link #endOfSupply(Throwable)}가 호출되어
	 * 데이터 공급이 종료된 경우는 {@link IllegalStateException} 예외가 발생된다.
	 * <p>
	 * {@link Suppliable}의 구현 클래스에 따라 데이터 제공 과정에서 발생한 예외가 있을 수 있다.
	 * 이 경우는 {@link ExecutionException} 예외로 래핑되어 전달된다.
	 *
	 * @param data		제공할 데이터
	 * @throws InterruptedException	 	쓰레드 대기 중 강제로 중단된 경우.
	 * @throws IllegalStateException	Suppliable이 이미 종료된 경우.
	 * @throws ExecutionException		데이터 공급 과정에서 발생한 예외가 있는 경우.
	 */
	public void supply(T data) throws IllegalStateException, InterruptedException, ExecutionException;
	
	/**
	 * 데이터를 일정시간 내로 제공한다.
	 * <p>
	 * 메소드 호출은 제공 받는 객체의 상황에 따라 호출 쓰레드가 일시 대기할 수 있음.
	 * 쓰레드 대기가 주어진 제한시간보다 길어지는 경우는 {@link TimeoutException} 예외가 발생된다.
	 * 만일 timeout == 0인 경우는 non-blocking 모드로 동작하여, 데이터 제공이 즉시 이루어지지 않으면
	 * {@link TimeoutException} 예외가 발생된다.
	 * 만일 쓰레드 대기 중에 강제로 종료된 경우는 {@link InterruptedException} 예외가
	 * 발생된다.
	 * 이미 {@link #endOfSupply()} 또는 {@link #endOfSupply(Throwable)}가 호출되어
	 * 데이터 공급이 종료된 경우는 {@link IllegalStateException} 예외가 발생된다.
	 * <p>
	 * {@link Suppliable}의 구현 클래스에 따라 데이터 제공 과정에서 발생한 예외가 있을 수 있다.
	 * 이 경우는 {@link ExecutionException} 예외로 래핑되어 전달된다.
	 *
	 * @param data		제공할 데이터
	 * @param timeout	제한시간
	 * @param tu		제한시간 단위
	 * @throws InterruptedException	 	쓰레드 대기 중 강제로 중단된 경우.
	 * @throws IllegalStateException	Suppliable이 이미 종료된 경우.
	 * @throws TimeoutException		제한시간 내 데이터 제공이 이루어지지 않은 경우.
	 * @throws ExecutionException		데이터 공급 과정에서 발생한 예외가 있는 경우.
	 */
	public void supply(T data, long timeout, TimeUnit tu)
		throws IllegalStateException, InterruptedException, TimeoutException, ExecutionException;
	
	/**
	 * 데이터 제공을 종료시킨다.
	 * <p>
	 * 이미 종료된 경우는 무시된다.
	 * 종료 이후에 발생되는 데이터 공급은  {@link IllegalStateException} 예외를 발생시킨다.
	 */
	public void endOfSupply();
	
	/**
	 * 오류 발생으로 더 이상 데이터를 공급할 수 없어 데이터 제공을 종료시킨다.
	 * <p>
	 * 인자로 전달되는 {@code error} 객체는 데이터 공급을 중단시킨 예외 객체로,
	 * 데이터를 소모하는 측에서 {@code error} 객체의 내용을 참조하여 데이터 공급이
	 * 중단된 이유를 알 수 있다.
	 * <p>
	 * 이미 종료된 경우는 무시된다.
	 * 종료 이후에 발생되는 데이터 공급은  {@link IllegalStateException} 예외를 발생시킨다.
	 * 
	 * @param error		데이터 공급을 중단시킨 예외 객체.
	 * 					{@code null}인 경우는 {@link IllegalArgumentException} 예외가 발생된다.
	 * @throws IllegalArgumentException	{@code error}가 {@code null}인 경우.
	 */
	public void endOfSupply(Throwable error) throws IllegalArgumentException;
	
	/**
	 * endOfSupply가 호출되어 데이터 공급이 종료되었는지 여부를 반환한다.
	 *
	 * @return	데이터 공급이 종료된 경우 {@code true}, 그렇지 않은 경우 {@code false}.
	 */
	public boolean isEndOfSupply();
}
