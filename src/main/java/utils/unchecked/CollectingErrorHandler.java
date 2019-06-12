package utils.unchecked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CollectingErrorHandler<T> implements ExceptionHandler<T,Integer> {
	private final List<ExceptionCase<T>> m_cases = new ArrayList<>();
	
	@Override
	public Integer handle(ExceptionCase<T> ecase) throws RuntimeException {
		m_cases.add(ecase);
		return m_cases.size();
	}
	
	public List<ExceptionCase<T>> getFailureCases() {
		return Collections.unmodifiableList(m_cases);
	}
}
