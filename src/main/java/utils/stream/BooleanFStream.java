package utils.stream;

import java.util.Objects;
import java.util.function.Function;

import com.google.common.primitives.Booleans;

import utils.Preconditions;
import utils.func.FOption;

/**
 * {@code boolean} primitive에 특화된 {@link FStream} 변형.
 * <p>
 * {@link FStream}{@code <Boolean>}을 확장하며, 모든 원소의 논리 AND/OR 같은 boolean 전용 종결 연산을
 * box 비용 없이 제공한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface BooleanFStream extends FStream<Boolean> {
	/**
	 * 가변인자 {@code boolean} 값들로부터 {@link BooleanFStream}을 생성한다.
	 *
	 * @param values 스트림에 포함될 원소들.
	 * @return {@link BooleanFStream} 객체.
	 */
	public static BooleanFStream of(boolean... values) {
		return new FStreamAdaptor(FStream.from(Booleans.asList(values)));
	}

	/**
	 * 각 {@code boolean} 원소를 {@code mapper}로 변환한 객체 스트림을 생성한다.
	 *
	 * @param <T>    매핑 결과 타입.
	 * @param mapper 매핑 함수.
	 * @return 매핑된 객체 스트림.
	 */
	public default <T> FStream<T> mapToObj(Function<Boolean,? extends T> mapper) {
		Objects.requireNonNull(mapper);

		return map(mapper);
	}

	/**
	 * 스트림의 모든 원소가 {@code true}인지 여부를 반환한다.
	 * <p>
	 * 빈 스트림이면 vacuous truth로 {@code true}를 반환한다.
	 *
	 * @return 모든 원소가 {@code true}이거나 빈 스트림이면 {@code true}.
	 */
	public default boolean andAll() {
		return reduce((v1,v2) -> Boolean.logicalAnd(v1, v2)).getOrElse(true);
	}

	/**
	 * 스트림에 {@code true} 원소가 하나라도 있는지 여부를 반환한다.
	 * <p>
	 * 빈 스트림이면 {@code false}를 반환한다.
	 *
	 * @return 하나 이상의 원소가 {@code true}이면 {@code true}, 그렇지 않거나 빈 스트림이면 {@code false}.
	 */
	public default boolean orAll() {
		return reduce((v1,v2) -> Boolean.logicalOr(v1, v2)).getOrElse(false);
	}

	/**
	 * 스트림의 앞 {@code count}개 원소로 구성된 {@link BooleanFStream}을 생성한다.
	 *
	 * @param count 취할 원소 수. 0 이상.
	 * @return 최대 {@code count}개의 원소를 포함한 스트림.
	 * @throws IllegalArgumentException {@code count}가 음수인 경우.
	 */
	@Override
	public default BooleanFStream take(long count) {
		Preconditions.checkArgument(count >= 0, "count >= 0: but: " + count);

		return new FStreamAdaptor(FStream.super.take(count));
	}

	/**
	 * 스트림의 모든 원소를 {@code boolean[]} 배열로 반환한다.
	 *
	 * @return 모든 원소를 담은 {@code boolean[]} 배열.
	 */
	public default boolean[] toArray() {
		return Booleans.toArray(toList());
	}
	
	static class FStreamAdaptor implements BooleanFStream {
		private final FStream<Boolean> m_src;
		
		FStreamAdaptor(FStream<Boolean> src) {
			m_src = src;
		}

		@Override
		public void close() throws Exception {
			m_src.close();
		}

		@Override
		public FOption<Boolean> next() {
			return m_src.next();
		}
	}
}
