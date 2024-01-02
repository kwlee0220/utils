package utils;

import java.util.concurrent.TimeUnit;

import utils.async.ThreadInterruptedException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Suppliable<T> {
	public boolean isEndOfSupply();
	
	/**
	 * 데이터를 제공한다.
	 * <p>
	 * {@link #endOfSupply()} 또는 {@link #endOfSupply(Throwable)} 호출되어
	 * 데이터 제공이 종료된 경우는 데이터 제공이 실패하여 {@code false}가 반환된다.
	 * <p>
	 * 메소드 호출은 제공 받는 객체의 상황에 따라 호출 쓰레드가 일시 대기할 수 있음.
	 * 만일 쓰레드 대기 중에 강제로 종료된 경우는 {@link ThreadInterruptedException} 예외가
	 * 발생된다.
	 * 
	 * @param data	제공할 데이터
	 * @return	데이터 제공 성공 여부.
	 * @throws ThreadInterruptedException	 쓰레드 대기 중 강제로 중단된 경우.
	 * @throws IllegalStateException	데이터를 받을 수 있는 상태가 아니어서, 앞으로도
	 * 									데이터를 제공할 수 있을 수 없는 경우.
	 * 									예를들어 Suppliable이 이미 종료된 경우를 생각할 수 있다.
	 */
	public boolean supply(T data) throws IllegalStateException, ThreadInterruptedException;
	
	/**
	 * 데이터를 일정시간 내로 제공한다.
	 * <p>
	 * {@link #endOfSupply()} 또는 {@link #endOfSupply(Throwable)} 호출되어
	 * 데이터 제공이 종료된 경우는 데이터 제공이 실패하여 {@code false}가 반환된다.
	 * <p>
	 * 메소드 호출은 제공 받는 객체의 상황에 따라 호출 쓰레드가 일시 대기할 수 있음.
	 * 쓰레드 대기가 주어진 대기시간보다 길어지는 경우는 {@code false}를 반환한다.
	 * 만일 쓰레드 대기 중에 강제로 종료된 경우는 {@link ThreadInterruptedException} 예외가
	 * 발생된다.
	 * 
	 * @param data		제공할 데이터
	 * @param timeout	제한시간
	 * @param tu		제한시간 단위
	 * @return	제한시간 내 데이터 제공 성공 여부.
	 * @throws	ThreadInterruptedException	 쓰레드 대기 중 강제로 중단된 경우.
	 * @throws IllegalStateException	데이터를 받을 수 있는 상태가 아니어서, 앞으로도
	 * 									데이터를 제공할 수 있을 수 없는 경우.
	 * 									예를들어 Suppliable이 이미 종료된 경우를 생각할 수 있다.
	 */
	public boolean supply(T data, long timeout, TimeUnit tu)
		throws IllegalStateException, ThreadInterruptedException;
	
	/**
	 * 데이터 제공을 종료시킨다.
	 * <p>
	 * 종료 이후에 발생되는 데이터 공급은  {@link IllegalStateException} 예외를 발생시킨다.
	 */
	public void endOfSupply();
	
	/**
	 * 오류 발생으로 데이터 제공을 종료시킨다.
	 * <p>
	 * 종료 이후에 발생되는 데이터 공급은  {@link IllegalStateException} 예외를 발생시킨다.
	 * 
	 * @param error		데이터 공급을 중단시킨 예외 객체.
	 */
	public void endOfSupply(Throwable error);
}
