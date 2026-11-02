package utils.stream;

import java.util.function.Function;

import com.google.common.primitives.Doubles;

import utils.Preconditions;
import utils.Tuple;
import utils.func.FOption;


/**
 * 원소가 {@link Double}인 {@link FStream}에 {@code double} 수치 종단 연산을 더한 스트림.
 * <p>
 * 원소는 박싱된 {@link Double}이며({@code java.util.stream}의 기본형 스트림과 달리 박싱 비용이 있다), 합계
 * ({@link #sum()}), 평균({@link #average()}), 최댓값·최솟값({@link #maxValue()}·{@link #minValue()}), 배열 변환
 * ({@link #toArray()}) 같은 연산을 제공한다. 원소에 {@code null}이 있으면 이 수치 연산들은
 * {@link NullPointerException}을 던진다.
 * <p>
 * 생성: {@link #of(double...)}(기본형 값), {@link #from(Double[])}(박싱 배열), {@link #from(Iterable)},
 * {@link #downcast(FStream)}(기존 스트림 감싸기).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface DoubleFStream extends FStream<Double> {
	/**
	 * 기본형 {@code double} 값들로부터 {@link DoubleFStream}을 생성한다.
	 *
	 * @param values	스트림에 포함될 원소들.
	 * @return	{@link DoubleFStream} 객체.
	 */
	public static DoubleFStream of(double... values) {
		Preconditions.checkNotNullArgument(values, "values is null");

		return new FStreamAdaptor(FStream.from(Doubles.asList(values)));
	}

	/**
	 * 박싱된 {@link Double} 배열로부터 {@link DoubleFStream}을 생성한다.
	 *
	 * @param values	스트림에 포함될 원소들.
	 * @return	{@link DoubleFStream} 객체.
	 */
	public static DoubleFStream from(Double[] values) {
		Preconditions.checkNotNullArgument(values, "values is null");

		return new FStreamAdaptor(FStream.of(values));
	}

	/**
	 * {@link Iterable}의 원소들로부터 {@link DoubleFStream}을 생성한다.
	 *
	 * @param values	스트림에 포함될 원소들.
	 * @return	{@link DoubleFStream} 객체.
	 */
	public static DoubleFStream from(Iterable<? extends Double> values) {
		Preconditions.checkNotNullArgument(values, "values is null");

		return new FStreamAdaptor(FStream.from(values));
	}

	/**
	 * 기존 {@link FStream}{@code <Double>}을 {@link DoubleFStream}으로 감싼다. 결과를 닫으면 원본도 닫힌다.
	 *
	 * @param strm	원본 스트림.
	 * @return	{@link DoubleFStream}으로 노출된 wrapper.
	 */
	public static DoubleFStream downcast(FStream<Double> strm) {
		Preconditions.checkNotNullArgument(strm, "strm is null");

		return new FStreamAdaptor(strm);
	}

	/**
	 * 각 원소를 {@code mapper}로 변환한 객체 스트림을 생성한다.
	 *
	 * @param <T>		매핑 결과 타입.
	 * @param mapper	매핑 함수.
	 * @return	매핑된 객체 스트림.
	 */
	public default <T> FStream<T> mapToObj(Function<Double,? extends T> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper must not be null");

		return map(mapper);
	}

	/**
	 * 스트림의 앞 {@code count}개 원소로 구성된 {@link DoubleFStream}을 생성한다.
	 *
	 * @param count	취할 원소 수. 0 이상.
	 * @return	최대 {@code count}개의 원소를 포함한 스트림.
	 * @throws IllegalArgumentException	{@code count}가 음수인 경우.
	 */
	@Override
	public default DoubleFStream take(long count) {
		Preconditions.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new FStreamAdaptor(FStream.super.take(count));
	}

	/**
	 * 스트림 원소들 중 최댓값을 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	최댓값을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default FOption<Double> maxValue() {
		return reduce(Double::max);
	}

	/**
	 * 스트림 원소들 중 최솟값을 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	최솟값을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default FOption<Double> minValue() {
		return reduce(Double::min);
	}

	/**
	 * 스트림 원소들의 합을 반환한다. 스트림을 끝까지 소비한다.
	 * <p>
	 * 빈 스트림이면 {@code 0}을 반환한다. {@link java.util.stream.DoubleStream#sum()}과 달리 보정 합산(Kahan)은 하지 않는다.
	 *
	 * @return	모든 원소의 합. 빈 스트림이면 {@code 0}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default double sum() {
		return fold(0d, (s,v) -> s + v);
	}

	/**
	 * 스트림 원소들의 평균을 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	평균을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default FOption<Double> average() {
		Tuple<Double,Long> state = fold(Tuple.of(0d,0L), (a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? FOption.of(state._1 / state._2) : FOption.empty();
	}

	/**
	 * 스트림의 모든 원소를 {@code double[]} 배열로 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	모든 원소를 담은 {@code double[]} 배열.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default double[] toArray() {
		return Doubles.toArray(toList());
	}

	static class FStreamAdaptor implements DoubleFStream {
		private final FStream<Double> m_src;

		FStreamAdaptor(FStream<Double> src) {
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
