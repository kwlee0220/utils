package utils.stream;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import utils.Tuple;
import utils.func.FOption;
import utils.stream.FStreams.AbstractFStream;

/**
 * {@code int} primitive에 특화된 {@link FStream} 변형.
 * <p>
 * {@link FStream}{@code <Integer>}을 확장하지만 합계({@link #sum()})/최댓값/최솟값/평균 등의
 * 종결 연산을 box 비용 없이 제공한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface IntFStream extends FStream<Integer> {
	/**
	 * 가변인자 {@code int} 값들로부터 {@link IntFStream}을 생성한다.
	 *
	 * @param values 스트림에 포함될 원소들.
	 * @return {@link IntFStream} 객체.
	 */
	public static IntFStream of(int... values) {
		return new FStreamAdaptor(FStream.from(Arrays.stream(values).iterator()));
	}

	/**
	 * 각 {@code int} 원소를 {@code mapper}로 변환한 객체 스트림을 생성한다.
	 *
	 * @param <T>    매핑 결과 타입.
	 * @param mapper 매핑 함수.
	 * @return 매핑된 객체 스트림.
	 */
	public default <T> FStream<T> mapToObj(Function<Integer,? extends T> mapper) {
		Objects.requireNonNull(mapper);

		return map(mapper);
	}

	/**
	 * 스트림의 앞 {@code count}개 원소로 구성된 {@link IntFStream}을 생성한다.
	 *
	 * @param count 취할 원소 수. 0 이상.
	 * @return 최대 {@code count}개의 원소를 포함한 스트림.
	 * @throws IllegalArgumentException {@code count}가 음수인 경우.
	 */
	@Override
	public default IntFStream take(long count) {
		utils.Preconditions.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new FStreamAdaptor(FStream.super.take(count));
	}
	
	/**
	 * 스트림 원소들 중 최댓값을 반환한다.
	 * <p>
	 * 빈 스트림이면 {@link FOption#empty()}를 반환한다.
	 *
	 * @return 최댓값을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 */
	public default FOption<Integer> maxValue() {
		return reduce(Integer::max);
	}

	/**
	 * 스트림 원소들 중 최솟값을 반환한다.
	 * <p>
	 * 빈 스트림이면 {@link FOption#empty()}를 반환한다.
	 *
	 * @return 최솟값을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 */
	public default FOption<Integer> minValue() {
		return reduce(Integer::min);
	}
	
	/**
	 * 스트림 원소들의 합을 반환한다.
	 * <p>
	 * 빈 스트림이면 항등원인 {@code 0}을 반환한다. 누적은 {@code long} 정밀도로 수행되어 overflow를
	 * 줄인다.
	 *
	 * @return 모든 원소의 합 (long). 빈 스트림이면 {@code 0}.
	 */
	public default long sum() {
		return fold(0L, (s,v) -> s+v);
	}

	/**
	 * 스트림 원소들의 평균을 반환한다.
	 * <p>
	 * 빈 스트림이면 {@link FOption#empty()}를 반환한다.
	 *
	 * @return 평균을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 */
	public default FOption<Double> average() {
		Tuple<Long,Long> state = fold(Tuple.of(0L,0L),
											(a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? FOption.of(state._1 / (double)state._2)
								: FOption.empty();
	}

	/**
	 * 스트림의 모든 원소를 {@code int[]} 배열로 반환한다.
	 *
	 * @return 모든 원소를 담은 {@code int[]} 배열.
	 */
	public default int[] toArray() {
		return Ints.toArray(toList());
	}
	
	static class RangedStream extends AbstractFStream<Integer> implements IntFStream {
		private int m_next;
		private final int m_end;
		
		RangedStream(int start, int end) {
			Preconditions.checkArgument(start <= end,
										String.format("invalid range: start=%d end=%s", start, end));	
			
			m_next = start;
			m_end = end;
		}

		@Override
		protected void closeInGuard() throws Exception { }

		@Override
		protected FOption<Integer> nextInGuard() {
			if ( m_next < m_end ) {
				return FOption.of(m_next++);
			}
			else {
				return FOption.empty();
			}
		}
	}
	
	static class FStreamAdaptor implements IntFStream {
		private final FStream<Integer> m_src;
		
		FStreamAdaptor(FStream<Integer> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Integer> next() {
			return m_src.next();
		}
	}
}
