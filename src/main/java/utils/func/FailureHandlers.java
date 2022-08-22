package utils.func;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import utils.Throwables;
import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FailureHandlers {
	public static <T> FailureHandler<T> ignoreHandler() {
		return new FailureHandler<T>() {
			@Override
			public void handle(FailureCase<T> fcase) { }
		};
	}
	
	public static <T> FailureHandler<T> sneakyThrowHandler() {
		return new FailureHandler<T>() {
			@Override
			public void handle(FailureCase<T> fcase) {
				Throwables.sneakyThrow(fcase.getCause());
			}
		};
	}
	
	public static class CountingErrorHandler<T> implements FailureHandler<T> {
		private long m_count = 0;

		@Override
		public void handle(FailureCase<T> fcase) {
			++m_count;
		}
		
		public long getErrorCount() {
			return m_count;
		}
	}
	public static <T> CountingErrorHandler<T> countHandler() {
		return new CountingErrorHandler<>();
	}

	public static class CollectingErrorHandler<T> implements FailureHandler<T> {
		private final List<FailureCase<T>> m_fcases;
		
		public CollectingErrorHandler() {
			m_fcases = new ArrayList<>();
		}
		
		public CollectingErrorHandler(List<FailureCase<T>> store) {
			Utilities.checkNotNullArgument(store, "store is null");
			m_fcases = store;
		}

		@Override
		public void handle(FailureCase<T> fcase) {
			Utilities.checkNotNullArgument(fcase, "FailureCase is null");
			m_fcases.add(fcase);
		}
		
		public List<FailureCase<T>> getFailureCases() {
			return Collections.unmodifiableList(m_fcases);
		}
	}
	public static <T> CollectingErrorHandler<T> collectHandler() {
		return new CollectingErrorHandler<>();
	}
	
	public static <T> CollectingErrorHandler<T> collectHandler(List<FailureCase<T>> store) {
		return new CollectingErrorHandler<>(store);
	}
}
