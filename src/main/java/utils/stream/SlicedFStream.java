package utils.stream;

import utils.func.FOption;
import utils.func.Slice;
import utils.stream.FStreams.SingleSourceStream;


/**
 * 입력 스트림의 원소 중 {@link Slice}로 지정된 부분 구간만 반환하는 {@link FStream} 구현체.
 * <p>
 * 입력 스트림의 처음부터 0번 인덱스로 세고, slice의 {@code start}부터 {@code end}-1까지의
 * 인덱스에 해당하는 원소들 중에서 {@code step} 간격으로 선택된 원소들을 반환한다.
 * 본 클래스는 {@link FStream#slice(Slice)} 의 결과로 사용되며, 외부에서 직접 인스턴스화하지 않고
 * {@link #from(FStream, Slice)} 정적 팩토리 메소드를 통해 생성한다.
 *
 * @param <T> 스트림의 원소 타입
 * @author Kang-Woo Lee (ETRI)
 */
class SlicedFStream<T> extends SingleSourceStream<T,T> {
	private final long m_maxRel;
	private final int m_step;
	private long m_idx = 0;

	/**
	 * 주어진 입력 스트림에 {@link Slice}를 적용하여 부분 구간 스트림을 생성한다.
	 * <p>
	 * 동작은 다음과 같다.
	 * <ul>
	 *   <li>{@link Slice#start()}이 양수이면 입력 스트림에서 해당 갯수만큼 {@link FStream#drop(long)}을 적용한다.</li>
	 *   <li>{@link Slice#end()}와 {@link Slice#step()} 가 모두 {@code null}이면 추가 wrapping 없이
	 *       drop이 적용된 스트림을 그대로 반환한다.</li>
	 *   <li>그 외의 경우는 {@code SlicedFStream} 인스턴스로 wrapping하여 반환한다.</li>
	 * </ul>
	 * 결과 스트림은 입력 인덱스가 {@code end} 직전까지 도달하거나 입력 스트림이 고갈되면 종료된다.
	 *
	 * @param <T>	스트림의 원소 타입
	 * @param src	입력 스트림 객체.
	 * @param slice	적용할 {@link Slice}.
	 * @return		부분 구간을 반환하는 {@link FStream} 객체.
	 */
	static <T> FStream<T> from(FStream<T> src, Slice slice) {
		int startVal = (slice.start() != null && slice.start() > 0) ? slice.start() : 0;
		Integer endVal = slice.end();
		Integer stepVal = slice.step();

		FStream<T> base = (startVal > 0) ? src.drop(startVal) : src;
		if ( endVal == null && stepVal == null ) {
			return base;
		}

		long maxRel = (endVal != null) ? Math.max(0L, (long)endVal - startVal) : Long.MAX_VALUE;
		int step = (stepVal != null) ? stepVal : 1;
		return new SlicedFStream<>(base, maxRel, step);
	}

	/**
	 * SlicedFStream 객체를 생성한다.
	 * <p>
	 * {@link #from(FStream, Slice)} 정적 팩토리에서만 호출되도록 {@code private} 으로 선언되어 있다.
	 *
	 * @param src		입력 스트림 객체. start만큼의 drop이 이미 적용된 상태여야 한다.
	 * @param maxRel	{@code src}에서 소비할 수 있는 최대 원소 수 ({@code end - start}).
	 * 					제한이 없으면 {@link Long#MAX_VALUE}를 사용한다.
	 * @param step		소비된 원소 중 결과로 반환할 간격. {@code 1}이면 모두 반환.
	 */
	private SlicedFStream(FStream<T> src, long maxRel, int step) {
		super(src);

		m_maxRel = maxRel;
		m_step = step;
	}

	@Override
	protected FOption<T> getNext(FStream<T> src) {
		while ( true ) {
			if ( m_idx >= m_maxRel ) {
				return FOption.empty();
			}
			FOption<T> nx = src.next();
			if ( nx.isAbsent() ) {
				return FOption.empty();
			}
			long cur = m_idx++;
			if ( cur % m_step == 0 ) {
				return nx;
			}
		}
	}
}
