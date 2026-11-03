package utils;

import java.io.IOException;

/**
 * picocli 기반 명령의 공통 인터페이스.
 * <p>
 * 명령 실행에 쓰이는 초기 컨텍스트를 만들고, 실행 전에 로그 설정을 구성한다.
 * 하위 명령은 {@link PicocliSubCommand}로 구현한다.
 *
 * @param <T>	초기 컨텍스트 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface PicocliCommand<T> extends Runnable {
	/**
	 * 명령 실행에 사용할 초기 컨텍스트를 반환한다.
	 *
	 * @return	초기 컨텍스트.
	 * @throws Exception	컨텍스트를 만들지 못한 경우.
	 */
	public T getInitialContext() throws Exception;

	/**
	 * 명령 실행 전에 로그 설정을 구성한다. 이름과 달리 사용하는 로깅 구현은 구현 클래스가 정한다.
	 *
	 * @throws IOException	로그 설정 파일을 읽지 못한 경우.
	 */
	public void configureLog4j() throws IOException;
}