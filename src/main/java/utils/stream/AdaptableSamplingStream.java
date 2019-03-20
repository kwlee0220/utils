package utils.stream;

import java.util.Random;

import utils.Utilities;
import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AdaptableSamplingStream<T> implements FStream<T> {
	private final FStream<T> m_input;
	private long m_denominator;
	private long m_numerator;
	private final Random m_rand;
	
	private int m_idx = 0;
	
	public AdaptableSamplingStream(FStream<T> input, long total, double ratio) {
		this(input, total, Math.max(1, Math.round(total * ratio)));
	}
	
	public AdaptableSamplingStream(FStream<T> input, long total, long nsamples) {
		Utilities.checkNotNullArgument(input, "input RecordSet is null");
		Utilities.checkArgument(total >= nsamples, "total >= nsamples");
		Utilities.checkArgument(nsamples > 0, "nsamples > 0");
		
		m_input = input;
		m_denominator = total;
		m_numerator = nsamples;
		
		m_rand = new Random(System.currentTimeMillis());
	}

	@Override
	public void close() throws Exception {
		m_input.close();
	}

	@Override
	public FOption<T> next() {
		FOption<T> data;
		while ( (data = m_input.next()).isPresent() ) {
			++m_idx;
			double ratio = (double)m_numerator / m_denominator;
			
			--m_denominator;
			if ( m_rand.nextDouble() <= ratio ) {
				--m_numerator;
				return data;
			}
		}
		
		return data;
	}
}