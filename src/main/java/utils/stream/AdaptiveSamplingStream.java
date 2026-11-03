package utils.stream;

import java.util.Random;

import utils.Preconditions;
import utils.func.FOption;


/**
 * 총 원소 수 {@code total}을 알고 있는 입력에서 목표한 개수 {@code nsamples}만큼을 무작위로 고르는 스트림.
 * <p>
 * 남은 원소 수와 남은 표본 수의 비로 선택 확률을 매번 다시 정하므로, 입력의 실제 개수가 {@code total}과 같으면
 * 정확히 {@code nsamples}개를 고른다. 목표 개수를 모두 고르면 남은 입력을 읽지 않고 끝난다.
 *
 * @param <T>	원소 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class AdaptiveSamplingStream<T> implements FStream<T> {
	private final FStream<T> m_input;
	private long m_denominator;
	private long m_numerator;
	private final Random m_rand;

	/**
	 * 목표 개수를 {@code round(total * ratio)}로 정하는 스트림을 생성한다.
	 *
	 * @param input	입력 스트림.
	 * @param total	입력의 총 원소 수. 0 이상이어야 한다.
	 * @param ratio	표본 비율. 0 이상 1 이하여야 한다. 0이면 아무것도 고르지 않는다.
	 * @throws IllegalArgumentException	인자가 범위를 벗어난 경우.
	 */
	public AdaptiveSamplingStream(FStream<T> input, long total, double ratio) {
		this(input, total, toSampleCount(total, ratio));
	}

	/**
	 * 목표 개수를 직접 지정한 스트림을 생성한다.
	 *
	 * @param input		입력 스트림.
	 * @param total		입력의 총 원소 수. 0 이상이어야 한다.
	 * @param nsamples	고를 원소 수. 0 이상 {@code total} 이하여야 한다.
	 * @throws IllegalArgumentException	인자가 범위를 벗어난 경우.
	 */
	public AdaptiveSamplingStream(FStream<T> input, long total, long nsamples) {
		Preconditions.checkNotNullArgument(input, "input is null");
		Preconditions.checkArgument(total >= 0, "total >= 0, but: %s", total);
		Preconditions.checkArgument(nsamples >= 0 && nsamples <= total,
									"0 <= nsamples <= total, but: nsamples=%s, total=%s", nsamples, total);

		m_input = input;
		m_denominator = total;
		m_numerator = nsamples;

		m_rand = new Random(System.currentTimeMillis());
	}

	static long toSampleCount(long total, double ratio) {
		Preconditions.checkArgument(total >= 0, "total >= 0, but: %s", total);
		Preconditions.checkArgument(ratio >= 0 && ratio <= 1, "0 <= ratio <= 1, but: %s", ratio);
		return Math.round(total * ratio);
	}

	@Override
	public void close() throws Exception {
		m_input.close();
	}

	@Override
	public FOption<T> next() {
		FOption<T> data;
		while ( m_numerator > 0 && (data = m_input.next()).isPresent() ) {
			// 입력이 total보다 많으면 분모가 0 이하가 되므로, 그때부터는 남은 표본을 모두 고른다.
			double ratio = (m_denominator > 0) ? (double)m_numerator / m_denominator : 1.0;

			--m_denominator;
			if ( m_rand.nextDouble() < ratio ) {
				--m_numerator;
				return data;
			}
		}

		return FOption.empty();
	}
}
