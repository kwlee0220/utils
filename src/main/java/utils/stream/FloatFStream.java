package utils.stream;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.primitives.Floats;

import utils.Preconditions;
import utils.Tuple;
import utils.func.FOption;

/**
 * {@code float} primitive에 특화된 {@link FStream} 변형.
 * <p>
 * {@link FStream}{@code <Float>}을 확장하지만 합계({@link #sum()})/평균({@link #average()}) 등의
 * 종결 연산을 box 비용 없이 제공한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface FloatFStream extends FStream<Float> {
	/**
	 * 가변인자 {@code float} 값들로부터 {@link FloatFStream}을 생성한다.
	 *
	 * @param values 스트림에 포함될 원소들.
	 * @return {@link FloatFStream} 객체.
	 */
	public static FloatFStream of(float... values) {
		return new ToFloatDowncaster(FStream.from(Floats.asList(values)));
	}

	/**
	 * 가변인자 {@link Float} 값들로부터 {@link FloatFStream}을 생성한다.
	 *
	 * @param values 스트림에 포함될 원소들.
	 * @return {@link FloatFStream} 객체.
	 */
	public static FloatFStream of(Float... values) {
		return new ToFloatDowncaster(FStream.of(values));
	}

	/**
	 * {@link List}로부터 {@link FloatFStream}을 생성한다.
	 *
	 * @param values 스트림에 포함될 원소 리스트.
	 * @return {@link FloatFStream} 객체.
	 */
	public static FloatFStream from(List<Float> values) {
		return new ToFloatDowncaster(FStream.from(values));
	}

	/**
	 * 기존 {@link FStream}{@code <Float>}을 {@link FloatFStream}으로 어댑팅한다.
	 *
	 * @param strm 원본 스트림.
	 * @return {@link FloatFStream}으로 노출된 wrapper.
	 */
	public static FloatFStream downcast(FStream<Float> strm) {
		return new ToFloatDowncaster(strm);
	}

	/**
	 * 각 {@code float} 원소를 {@code mapper}로 변환한 객체 스트림을 생성한다.
	 *
	 * @param <T>    매핑 결과 타입.
	 * @param mapper 매핑 함수.
	 * @return 매핑된 객체 스트림.
	 */
	public default <T> FStream<T> mapToObj(Function<Float,? extends T> mapper) {
		Objects.requireNonNull(mapper);

		return map(mapper);
	}

	/**
	 * 스트림의 앞 {@code count}개 원소로 구성된 {@link FloatFStream}을 생성한다.
	 *
	 * @param count 취할 원소 수. 0 이상.
	 * @return 최대 {@code count}개의 원소를 포함한 스트림.
	 * @throws IllegalArgumentException {@code count}가 음수인 경우.
	 */
	@Override
	public default FloatFStream take(long count) {
		Preconditions.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new ToFloatDowncaster(FStream.super.take(count));
	}
	
	/**
	 * 스트림 원소들의 합을 반환한다.
	 * <p>
	 * 빈 스트림이면 항등원인 {@code 0}을 반환한다. 합산은 {@code double} 정밀도로 누적되어 반환된다.
	 *
	 * @return 모든 원소의 합 (double). 빈 스트림이면 {@code 0}.
	 */
	public default double sum() {
		return reduce((v1,v2) -> v1+v2).getOrElse(0f);
	}
	
	/**
	 * 스트림 원소들의 평균을 반환한다.
	 * <p>
	 * 빈 스트림이면 {@link FOption#empty()}를 반환한다. 누적은 {@code double} 정밀도로 수행된 뒤
	 * {@code float}으로 변환되어 반환된다.
	 *
	 * @return 평균을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 */
	public default FOption<Float> average() {
		Tuple<Double,Long> state = fold(Tuple.of(0d,0L),
											(a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? FOption.of(state._1 / state._2).map(Double::floatValue)
								: FOption.empty();
	}

	/**
	 * 스트림의 모든 원소를 {@code float[]} 배열로 반환한다.
	 *
	 * @return 모든 원소를 담은 {@code float[]} 배열.
	 */
	public default float[] toArray() {
		return Floats.toArray(toList());
	}
	
	static class ToFloatDowncaster implements FloatFStream {
		private final FStream<Float> m_src;
		
		ToFloatDowncaster(FStream<Float> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Float> next() {
			return m_src.next();
		}
	}
}
