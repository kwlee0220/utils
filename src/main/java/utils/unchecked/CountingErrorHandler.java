package utils.unchecked;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CountingErrorHandler<T> implements ExceptionHandler<T,Long> {
	private long m_count = 0;
	
	@Override
	public Long handle(ExceptionCase<T> ecase) throws RuntimeException {
		++m_count;
		return m_count;
	}
	
	public long getErrorCount() {
		return m_count;
	}
}
