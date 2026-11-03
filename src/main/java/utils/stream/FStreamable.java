package utils.stream;


/**
 * 자신의 원소들을 {@link FStream}으로 제공할 수 있는 객체의 인터페이스.
 *
 * @param <T>	원소 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface FStreamable<T> {
	/**
	 * 이 객체의 원소들을 순회하는 {@link FStream}을 반환한다.
	 *
	 * @return	원소 스트림.
	 */
	public FStream<T> fstream();
}
