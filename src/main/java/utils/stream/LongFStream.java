package utils.stream;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.function.Function;

import com.google.common.primitives.Longs;

import utils.Preconditions;
import utils.func.FOption;
import utils.stream.FStreams.AbstractFStream;


/**
 * 원소가 {@link Long}인 {@link FStream}에 {@code long} 수치 종단 연산을 더한 스트림.
 * <p>
 * 원소는 박싱된 {@link Long}이며({@code java.util.stream}의 기본형 스트림과 달리 박싱 비용이 있다), 합계
 * ({@link #sum()}), 평균({@link #average()}), 최댓값·최솟값({@link #maxValue()}·{@link #minValue()}), 배열 변환
 * ({@link #toArray()}) 같은 연산을 제공한다. 원소에 {@code null}이 있으면 이 수치 연산들은
 * {@link NullPointerException}을 던진다.
 * <p>
 * 생성: {@link #of(long...)}(기본형 값), {@link #from(Long[])}(박싱 배열), {@link #from(Iterable)},
 * {@link #downcast(FStream)}(기존 스트림 감싸기), {@link #range(long, long)}.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface LongFStream extends FStream<Long> {
	/**
	 * 기본형 {@code long} 값들로부터 {@link LongFStream}을 생성한다.
	 *
	 * @param values	스트림에 포함될 원소들.
	 * @return	{@link LongFStream} 객체.
	 */
	public static LongFStream of(long... values) {
		Preconditions.checkNotNullArgument(values, "values is null");

		return new FStreamAdaptor(FStream.from(Longs.asList(values)));
	}

	/**
	 * 박싱된 {@link Long} 배열로부터 {@link LongFStream}을 생성한다.
	 *
	 * @param values	스트림에 포함될 원소들.
	 * @return	{@link LongFStream} 객체.
	 */
	public static LongFStream from(Long[] values) {
		Preconditions.checkNotNullArgument(values, "values is null");

		return new FStreamAdaptor(FStream.of(values));
	}

	/**
	 * {@link Iterable}의 원소들로부터 {@link LongFStream}을 생성한다.
	 *
	 * @param values	스트림에 포함될 원소들.
	 * @return	{@link LongFStream} 객체.
	 */
	public static LongFStream from(Iterable<? extends Long> values) {
		Preconditions.checkNotNullArgument(values, "values is null");

		return new FStreamAdaptor(FStream.from(values));
	}

	/**
	 * 기존 {@link FStream}{@code <Long>}을 {@link LongFStream}으로 감싼다. 결과를 닫으면 원본도 닫힌다.
	 *
	 * @param strm	원본 스트림.
	 * @return	{@link LongFStream}으로 노출된 wrapper.
	 */
	public static LongFStream downcast(FStream<Long> strm) {
		Preconditions.checkNotNullArgument(strm, "strm is null");

		return new FStreamAdaptor(strm);
	}

	/**
	 * {@code start}부터 1씩 증가하여 {@code end - 1}까지의 값으로 구성된 {@link LongFStream}을 생성한다.
	 * <p>
	 * 스트림에는 {@code start}는 포함하지만 {@code end}는 포함하지 않는다.
	 *
	 * @param start	시작 값.
	 * @param end	종료 바로 다음 값. {@code start} 이상이어야 한다.
	 * @return	스트림 객체.
	 * @throws IllegalArgumentException	{@code start > end}인 경우.
	 */
	public static LongFStream range(long start, long end) {
		return new RangedStream(start, end);
	}

	/**
	 * 각 원소를 {@code mapper}로 변환한 객체 스트림을 생성한다.
	 *
	 * @param <T>		매핑 결과 타입.
	 * @param mapper	매핑 함수.
	 * @return	매핑된 객체 스트림.
	 */
	public default <T> FStream<T> mapToObj(Function<Long,? extends T> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper must not be null");

		return map(mapper);
	}

	/**
	 * 스트림의 앞 {@code count}개 원소로 구성된 {@link LongFStream}을 생성한다.
	 *
	 * @param count	취할 원소 수. 0 이상.
	 * @return	최대 {@code count}개의 원소를 포함한 스트림.
	 * @throws IllegalArgumentException	{@code count}가 음수인 경우.
	 */
	@Override
	public default LongFStream take(long count) {
		Preconditions.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new FStreamAdaptor(FStream.super.take(count));
	}

	/**
	 * 스트림 원소들 중 최댓값을 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	최댓값을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default FOption<Long> maxValue() {
		return reduce(Long::max);
	}

	/**
	 * 스트림 원소들 중 최솟값을 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	최솟값을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default FOption<Long> minValue() {
		return reduce(Long::min);
	}

	/**
	 * 스트림 원소들의 합을 반환한다. 스트림을 끝까지 소비한다.
	 * <p>
	 * 빈 스트림이면 {@code 0}을 반환한다. 결과가 {@code long} 범위를 넘으면 넘친다(overflow).
	 *
	 * @return	모든 원소의 합. 빈 스트림이면 {@code 0}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default long sum() {
		return fold(0L, (s,v) -> s + v);
	}

	/**
	 * 스트림 원소들의 평균을 반환한다. 스트림을 끝까지 소비한다.
	 * <p>
	 * 합은 {@code long}으로 누적하다가 {@code long} 범위를 넘으면 {@link BigInteger}로 이어 누적하므로 넘쳐서 틀리지
	 * 않는다(예: {@code Long.MAX_VALUE} 두 개의 평균은 {@code Long.MAX_VALUE}).
	 *
	 * @return	평균을 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default FOption<Double> average() {
		try {
			long sum = 0;
			BigInteger bigSum = null;	// sum이 넘친 뒤의 합
			long count = 0;

			FOption<Long> next;
			while ( (next = next()).isPresent() ) {
				long v = next.get();
				if ( bigSum == null ) {
					try {
						sum = Math.addExact(sum, v);
					}
					catch ( ArithmeticException overflow ) {
						bigSum = BigInteger.valueOf(sum).add(BigInteger.valueOf(v));
					}
				}
				else {
					bigSum = bigSum.add(BigInteger.valueOf(v));
				}
				++count;
			}

			if ( count == 0 ) {
				return FOption.empty();
			}
			if ( bigSum == null ) {
				return FOption.of(sum / (double)count);
			}
			return FOption.of(new BigDecimal(bigSum).divide(BigDecimal.valueOf(count), MathContext.DECIMAL64)
													.doubleValue());
		}
		finally {
			closeQuietly();
		}
	}

	/**
	 * 스트림의 모든 원소를 {@code long[]} 배열로 반환한다. 스트림을 끝까지 소비한다.
	 *
	 * @return	모든 원소를 담은 {@code long[]} 배열.
	 * @throws NullPointerException	원소에 {@code null}이 있는 경우.
	 */
	public default long[] toArray() {
		return Longs.toArray(toList());
	}

	static class RangedStream extends AbstractFStream<Long> implements LongFStream {
		private long m_next;
		private final long m_end;

		RangedStream(long start, long end) {
			Preconditions.checkArgument(start <= end, "invalid range: start=%s end=%s", start, end);

			m_next = start;
			m_end = end;
		}

		@Override
		protected void closeInGuard() throws Exception { }

		@Override
		protected FOption<Long> nextInGuard() {
			if ( m_next < m_end ) {
				return FOption.of(m_next++);
			}
			else {
				return FOption.empty();
			}
		}
	}

	static class FStreamAdaptor implements LongFStream {
		private final FStream<Long> m_src;

		FStreamAdaptor(FStream<Long> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Long> next() {
			return m_src.next();
		}
	}
}
