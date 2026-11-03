package utils.rx;

/**
 * 비동기 수행({@link utils.async.Execution})의 진행 상황을 나타내는 이벤트의 최상위 클래스.
 * <p>
 * 수행 시작({@link Started}), 성공 완료({@link Completed}), 취소({@link Cancelled}),
 * 실패({@link Failed}) 네 가지 하위 클래스가 있으며, {@link Observables#observeExecution}과
 * {@link Flowables#observeExecution}이 발행하는 이벤트로 사용된다.
 * <p>
 * <b>주의:</b> 각 하위 클래스의 {@code equals()}는 클래스만 비교하고 결과 값이나 실패 원인은
 * 비교하지 않으며, {@code hashCode()}는 재정의되어 있지 않다.
 * 
 * @param <T>	수행 결과 값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ExecutionProgress<T> {
	/**
	 * 하위 클래스에서 사용하는 기본 생성자.
	 */
	public ExecutionProgress() { }
	
	/**
	 * 수행 시작 이벤트 객체를 새로 생성한다.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 * @return	{@link Started} 객체.
	 */
	public static <T> Started<T> started() {
		return new Started<>();
	}
	/**
	 * 주어진 결과 값을 갖는 성공 완료 이벤트 객체를 생성한다.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 * @param result	수행 결과 값 ({@code null} 허용).
	 * @return	{@link Completed} 객체.
	 */
	public static <T> Completed<T> completed(T result) {
		return new Completed<>(result);
	}
	/**
	 * 수행 취소 이벤트 객체를 새로 생성한다.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 * @return	{@link Cancelled} 객체.
	 */
	public static <T> Cancelled<T> cancelled() {
		return new Cancelled<>();
	}
	/**
	 * 주어진 실패 원인을 갖는 수행 실패 이벤트 객체를 생성한다.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 * @param cause	실패 원인 예외 ({@code null} 여부는 검사하지 않는다).
	 * @return	{@link Failed} 객체.
	 */
	public static <T> Failed<T> failed(Throwable cause) {
		return new Failed<>(cause);
	}
	
	/**
	 * 수행 시작 이벤트.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 */
	public static class Started<T> extends ExecutionProgress<T> {
		/**
		 * 수행 시작 이벤트 객체를 생성한다.
		 */
		public Started() { }
		
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
	
	/**
	 * 수행 성공 완료 이벤트. 수행 결과 값을 함께 갖는다.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 */
	public static class Completed<T> extends ExecutionProgress<T> {
		private final T m_result;
		
		/**
		 * 주어진 결과 값을 갖는 성공 완료 이벤트 객체를 생성한다.
		 * 
		 * @param result	수행 결과 값 ({@code null} 허용).
		 */
		public Completed(T result) {
			m_result = result;
		}
		
		/**
		 * 수행 결과 값을 반환한다.
		 * 
		 * @return	수행 결과 값 ({@code null}일 수 있음).
		 */
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
	
	/**
	 * 수행 취소 이벤트.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 */
	public static class Cancelled<T> extends ExecutionProgress<T> {
		/**
		 * 수행 취소 이벤트 객체를 생성한다.
		 */
		public Cancelled() { }
		
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
	
	/**
	 * 수행 실패 이벤트. 실패 원인 예외를 함께 갖는다.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 */
	public static class Failed<T> extends ExecutionProgress<T> {
		private final Throwable m_cause;
		
		/**
		 * 주어진 실패 원인을 갖는 수행 실패 이벤트 객체를 생성한다.
		 * 
		 * @param cause	실패 원인 예외.
		 */
		public Failed(Throwable cause) {
			m_cause = cause;
		}
		
		/**
		 * 수행 실패 원인 예외를 반환한다.
		 * 
		 * @return	실패 원인 예외.
		 */
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
