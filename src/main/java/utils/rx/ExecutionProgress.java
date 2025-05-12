package utils.rx;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ExecutionProgress<T> {
	public static <T> Started<T> started() {
		return new Started<>();
	}
	public static <T> Completed<T> completed(T result) {
		return new Completed<>(result);
	}
	public static <T> Cancelled<T> cancelled() {
		return new Cancelled<>();
	}
	public static <T> Failed<T> failed(Throwable cause) {
		return new Failed<>(cause);
	}
	
	public static class Started<T> extends ExecutionProgress<T> {
		@Override
		public boolean equals(Object obj) {
			if ( obj == this ) {
				return true;
			}
			else if ( obj == null || obj.getClass() != Started.class ) {
				return false;
			}
			
			return true;
		}
	}
	
	public static class Completed<T> extends ExecutionProgress<T> {
		private final T m_result;
		
		public Completed(T result) {
			m_result = result;
		}
		
		public T getResult() {
			return m_result;
		}
		
		@Override
		public boolean equals(Object obj) {
			if ( obj == this ) {
				return true;
			}
			else if ( obj == null || obj.getClass() != Completed.class ) {
				return false;
			}
			
			return true;
		}
	}
	
	public static class Cancelled<T> extends ExecutionProgress<T> {
		@Override
		public boolean equals(Object obj) {
			if ( obj == this ) {
				return true;
			}
			else if ( obj == null || obj.getClass() != Cancelled.class ) {
				return false;
			}
			
			return true;
		}
	}
	
	public static class Failed<T> extends ExecutionProgress<T> {
		private final Throwable m_cause;
		
		public Failed(Throwable cause) {
			m_cause = cause;
		}
		
		public Throwable getCause() {
			return m_cause;
		}
		
		@Override
		public boolean equals(Object obj) {
			if ( obj == this ) {
				return true;
			}
			else if ( obj == null || obj.getClass() != Failed.class ) {
				return false;
			}
			
			return true;
		}
	}
}
