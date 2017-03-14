package utils.thread;

import java.util.concurrent.Executor;

import utils.Initializable;


/**
 * {@literal ExecutorAware}는 컴포넌트 관리자가 사용하는 쓰레드 풀을 필요로 하는 컴포넌트의
 * 인터페이스를 정의한다.
 * <p>
 * 본 인터페이스를 구현하는 컴포넌트는 구동시 관리자에 의해 자동적으로
 * {@link #setExecutor(Executor)} 호출을 통해 해당 컴포넌트에게
 * 알린다. 메소드 호출 시점은 컴포넌트이 속성 값 설정이 모두 끝나고
 * {@link Initializable#initialize()} 메소드 호출 이전에 호출된다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ExecutorAware {
	/**
	 * 사용할 쓰레드 풀을 얻는다.
	 * 
	 * @return 스레드 풀 객체.
	 */
	public Executor getExecutor();
	
	/**
	 * 쓰레드 풀을 설정한다.
	 * <p>
	 * 본 메소드는 일반적으로 본 컴포넌트를 관리하는  컴포넌트 관리자에 의해 컴포넌트 속성 값
	 * 설정이 끝나고 {@link Initializable#initialize()} 메소드 호출 이전에 호출된다.
	 * 
	 * @param executor	설정할 쓰레드 풀 객체.
	 */
	public void setExecutor(Executor executor);
}
