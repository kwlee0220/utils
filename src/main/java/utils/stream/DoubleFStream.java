package utils.stream;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.google.common.primitives.Doubles;

import utils.Preconditions;
import utils.Tuple;
import utils.func.FOption;

/**
 * {@code double} primitive에 특화된 {@link FStream} 변형.
 * <p>
 * {@link FStream}{@code <Double>}을 확장하지만 합계({@link #sum()})/평균({@link #average()}) 등의
 * 종결 연산을 box 비용 없이 제공한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface DoubleFStream extends FStream<Double> {
	/**
	 * 가변인자 {@code double} 값들로부터 {@link DoubleFStream}을 생성한다.
	 *
	 * @param values 스트림에 포함될 원소들.
	 * @return {@link DoubleFStream} 객체.
	 */
	public static DoubleFStream of(double... values) {
		return new ToDoubleDowncaster(FStream.from(Arrays.stream(values).iterator()));
	}

	/**
	 * 가변인자 {@link Double} 값들로부터 {@link DoubleFStream}을 생성한다.
	 *
	 * @param values 스트림에 포함될 원소들.
	 * @return {@link DoubleFStream} 객체.
	 */
	public static DoubleFStream of(Double... values) {
		return new ToDoubleDowncaster(FStream.of(values));
	}

	/**
	 * {@link List}로부터 {@link DoubleFStream}을 생성한다.
	 *
	 * @param values 스트림에 포함될 원소 리스트.
	 * @return {@link DoubleFStream} 객체.
	 */
	public static DoubleFStream from(List<Double> values) {
		return new ToDoubleDowncaster(FStream.from(values));
	}

	/**
	 * 기존 {@link FStream}{@code <Double>}을 {@link DoubleFStream}으로 어댑팅한다.
	 *
	 * @param strm 원본 스트림.
	 * @return {@link DoubleFStream}으로 노출된 wrapper.
	 */
	public static DoubleFStream downcast(FStream<Double> strm) {
		return new ToDoubleDowncaster(strm);
	}

	/**
	 * 각 {@code double} 원소를 {@code mapper}로 변환한 객체 스트림을 생성한다.
	 *
	 * @param <T>    매핑 결과 타입.
	 * @param mapper 매핑 함수.
	 * @return 매핑된 객체 스트림.
	 */
	public default <T> FStream<T> mapToObj(Function<Double,? extends T> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper must not be null");

		return map(mapper);
	}

	/**
	 * 스트림의 앞 {@code count}개 원소로 구성된 {@link DoubleFStream}을 생성한다.
	 *
	 * @param count 취할 원소 수. 0 이상.
	 * @return 최대 {@code count}개의 원소를 포함한 스트림.
	 * @throws IllegalArgumentException {@code count}가 음수인 경우.
	 */
	@Override
	public default DoubleFStream take(long count) {
		Preconditions.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new ToDoubleDowncaster(FStream.super.take(count));
	}
	
	/**
	 * 스트림 원소들의 합을 반환한다.
	 * <p>
	 * 빈 스트림이면 항등원인 {@code 0.0}을 반환한다.
	 * {@link java.util.stream.DoubleStream#sum()}과 동일한 의미론을 따른다.
	 *
	 * @return 모든 원소의 합. 빈 스트림이면 {@code 0.0}.
	 */
	public default double sum() {
		return reduce((v1,v2) -> v1+v2).getOrElse(0d);
	}
	
	/**
	 * 스트림 원소들의 평균을 반환한다.
	 * <p>
	 * 빈 스트림이면 {@link FOption#empty()}를 반환한다.
	 *
	 * @return 평균을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 */
	public default FOption<Double> average() {
		Tuple<Double,Long> state = fold(Tuple.of(0d,0L), (a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? FOption.of(state._1 / state._2)
								: FOption.empty();
	}

	/**
	 * 스트림의 모든 원소를 {@code double[]} 배열로 반환한다.
	 *
	 * @return 모든 원소를 담은 {@code double[]} 배열.
	 */
	public default double[] toArray() {
		return Doubles.toArray(toList());
	}
	
	static class ToDoubleDowncaster implements DoubleFStream {
		private final FStream<Double> m_src;
		
		ToDoubleDowncaster(FStream<Double> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Double> next() {
			return m_src.next();
		}
	}
}
