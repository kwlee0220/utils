package utils.stream;

import java.util.function.Function;

import com.google.common.primitives.Floats;

import utils.Preconditions;
import utils.Tuple;
import utils.func.FOption;


/**
 * 원소가 {@link Float}인 {@link FStream}에 {@code float} 수치 종단 연산을 더한 스트림.
 * <p>
 * 원소는 박싱된 {@link Float}이며({@code java.util.stream}의 기본형 스트림과 달리 박싱 비용이 있다), 합계
 * ({@link #sum()}), 평균({@link #average()}), 최댓값·최솟값({@link #maxValue()}·{@link #minValue()}), 배열 변환
 * ({@link #toArray()}) 같은 연산을 제공한다. 원소에 {@code null}이 있으면 이 수치 연산들은
 * {@link NullPointerException}을 던진다.
 * <p>
 * 생성: {@link #of(float...)}(기본형 값), {@link #from(Float[])}(박싱 배열), {@link #from(Iterable)},
 * {@link #downcast(FStream)}(기존 스트림 감싸기).
 * 2026-11-02 이전에는 박싱 배열용 생성 메소드도 {@code of(Float...)}여서, {@code of(1, 2)} 같은 리터럴 호출이
 * "ambiguous" 컴파일 오류가 났다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface FloatFStream extends FStream<Float> {
	/**
	 * 기본형 {@code float} 값들로부터 {@link FloatFStream}을 생성한다.
	 *
	 * @param values	스트림에 포함될 원소들.
	 * @return	{@link FloatFStream} 객체.
	 */
	public static FloatFStream of(float... values) {
		Preconditions.checkNotNullArgument(values, "values is null");

		return new FStreamAdaptor(FStream.from(Floats.asList(values)));
	}

	/**
	 * 박싱된 {@link Float} 배열로부터 {@link FloatFStream}을 생성한다.
	 * <p>
	 * 2026-11-02 이전의 이름은 {@code of(Float...)}였다(기본형 {@link #of(float...)}와 모호해 리터럴로 호출할 수
	 * 없었으므로 이름을 바꿨다, 별칭 없음).
	 *
	 * @param values	스트림에 포함될 원소들.
	 * @return	{@link FloatFStream} 객체.
	 */
	public static FloatFStream from(Float[] values) {
		Preconditions.checkNotNullArgument(values, "values is null");

		return new FStreamAdaptor(FStream.of(values));
	}

	/**
	 * {@link Iterable}의 원소들로부터 {@link FloatFStream}을 생성한다.
	 *
	 * @param values	스트림에 포함될 원소들.
	 * @return	{@link FloatFStream} 객체.
	 */
	public static FloatFStream from(Iterable<? extends Float> values) {
		Preconditions.checkNotNullArgument(values, "values is null");

		return new FStreamAdaptor(FStream.from(values));
	}

	/**
	 * 기존 {@link FStream}{@code <Float>}을 {@link FloatFStream}으로 감싼다. 결과를 닫으면 원본도 닫힌다.
	 *
	 * @param strm	원본 스트림.
	 * @return	{@link FloatFStream}으로 노출된 wrapper.
	 */
	public static FloatFStream downcast(FStream<Float> strm) {
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
	public default <T> FStream<T> mapToObj(Function<Float,? extends T> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper must not be null");

		return map(mapper);
	}

	/**
	 * 스트림의 앞 {@code count}개 원소로 구성된 {@link FloatFStream}을 생성한다.
	 *
	 * @param count	취할 원소 수. 0 이상.
	 * @return	최대 {@code count}개의 원소를 포함한 스트림.
	 * @throws IllegalArgumentException	{@code count}가 음수인 경우.
	 */
	@Override
	public default FloatFStream take(long count) {
		Preconditions.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new FStreamAdaptor(FStream.super.take(count));
	}

	/**
	 * 스트림 원소들 중 최댓값을 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	최댓값을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default FOption<Float> maxValue() {
		return reduce(Float::max);
	}

	/**
	 * 스트림 원소들 중 최솟값을 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	최솟값을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default FOption<Float> minValue() {
		return reduce(Float::min);
	}

	/**
	 * 스트림 원소들의 합을 반환한다. 스트림을 끝까지 소비한다.
	 * <p>
	 * 빈 스트림이면 {@code 0}을 반환한다. 누적은 {@code double}로 한다. 2026-11-02 이전에는 {@code float}로
	 * 누적해 정밀도를 잃었다(0.1f 10개의 합이 1.0000001192).
	 *
	 * @return	모든 원소의 합. 빈 스트림이면 {@code 0}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default double sum() {
		return fold(0d, (s,v) -> s + v);
	}

	/**
	 * 스트림 원소들의 평균을 반환한다. 스트림을 끝까지 소비한다. 누적은 {@code double}로 한 뒤 {@code float}로 바꿔 반환한다.
	 *
	 * @return	평균을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default FOption<Float> average() {
		Tuple<Double,Long> state = fold(Tuple.of(0d,0L), (a,v) -> Tuple.of(a._1 + v, a._2 + 1));
		return (state._2 > 0) ? FOption.of((float)(state._1 / state._2)) : FOption.empty();
	}

	/**
	 * 스트림의 모든 원소를 {@code float[]} 배열로 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	모든 원소를 담은 {@code float[]} 배열.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default float[] toArray() {
		return Floats.toArray(toList());
	}

	static class FStreamAdaptor implements FloatFStream {
		private final FStream<Float> m_src;

		FStreamAdaptor(FStream<Float> src) {
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
