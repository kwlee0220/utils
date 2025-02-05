package utils.stream;

import utils.Suppliable;


/**
 * {@code DataGenerator}는 데이터를 생성하는 인터페이스를 정의한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface Generator<T> {
	/**
	 * 주어진 출력 채널로 데이터를 생성한다.
	 * <p>
	 * 데이터 생성은 {@link Suppliable#supply(Object)}를 호출하여 출력 채널로 데이터를 전달한다.
	 * 데이터 생성이 완료되면 {@link Suppliable#endOfSupply()}를 호출하여 데이터 생성을 종료한다.
	 * 만일 데이터 생성 중 오류가 발생한 경우는 {@link Suppliable#endOfSupply(Throwable)}를 호출하여
	 * 예외를 전달한다.
	 * 
	 * @param outChannel	데이터를 출력할 채널 객체.
	 * @throws Exception	데이터 생성 중 오류가 발생된 경우.
	 */
	public void generate(Suppliable<T> outChannel) throws Exception;
}
