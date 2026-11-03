package utils.func;

import java.util.function.Supplier;

/**
 * 꼬리 재귀(tail recursion)를 스택 소모 없이 반복문으로 수행하기 위한 trampoline 클래스.
 * <p>
 * 재귀 호출 대신 {@link #suspend(Supplier)}로 다음 단계를 지연된 계산으로 반환하고,
 * 종료 시에는 {@link #returns(Object)}로 결과를 반환한다. {@link #evaluate()}를 호출하면
 * 지연된 단계들을 루프로 차례로 수행하여 최종 결과를 얻는다.
 *
 * @param <T>	계산 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class TailCall<T> {
	/**
	 * 본 객체가 아직 수행되지 않은 다음 단계(suspend)인지 여부를 반환한다.
	 * 
	 * @return	{@link #suspend(Supplier)}로 생성된 경우 {@code true},
	 * 			{@link #returns(Object)}로 생성된 경우 {@code false}.
	 */
	public abstract boolean isSuspend();
	
	/**
	 * 계산을 끝까지 수행하여 최종 결과를 반환한다.
	 * <p>
	 * suspend 단계인 경우 {@link #resume()}을 결과 단계가 나올 때까지 루프로 반복 호출하므로
	 * 호출 스택이 깊어지지 않는다. 결과 단계인 경우에는 저장된 결과를 바로 반환한다.
	 * 
	 * @return	최종 계산 결과.
	 */
	public abstract T evaluate();
	
	/**
	 * 지연된 다음 단계 하나를 수행하여 그 다음 {@link TailCall}을 반환한다.
	 * 
	 * @return	다음 단계의 {@link TailCall} 객체.
	 * @throws IllegalStateException	본 객체가 결과 단계({@link #returns(Object)})인 경우.
	 */
	public abstract TailCall<T> resume();
	
	private TailCall() { }
	
	/**
	 * 주어진 값을 최종 결과로 하는 결과 단계 {@link TailCall}을 생성한다.
	 * 
	 * @param <T>	결과 타입.
	 * @param result	최종 결과 값. {@code null}일 수 있다.
	 * @return	결과 단계 {@link TailCall} 객체.
	 */
	public static <T> TailCall<T> returns(T result) {
		return new Return<>(result);
	}
	
	/**
	 * 다음 단계 계산을 지연시킨 suspend 단계 {@link TailCall}을 생성한다.
	 * <p>
	 * {@code work}는 이 시점에 호출되지 않고 {@link #resume()} 또는 {@link #evaluate()}가
	 * 호출될 때 수행된다.
	 * 
	 * @param <T>	결과 타입.
	 * @param work	다음 단계 {@link TailCall}을 생성하는 지연 계산.
	 * @return	suspend 단계 {@link TailCall} 객체.
	 */
	public static <T> TailCall<T> suspend(Supplier<TailCall<T>> work) {
		return new Suspend<>(work);
	}
	
	private static class Return<T> extends TailCall<T> {
		private final T m_result;
		
		private Return(T result) {
			m_result = result;
		}

		@Override
		public boolean isSuspend() {
			return false;
		}

		@Override
		public T evaluate() {
			return m_result;
		}

		@Override
		public TailCall<T> resume() {
			throw new IllegalStateException("Return cannot resume");
		}
	}
	
	private static class Suspend<T> extends TailCall<T> {
		private final Supplier<TailCall<T>> m_work;
		
		private Suspend(Supplier<TailCall<T>> work) {
			m_work = work;
		}

		@Override
		public boolean isSuspend() {
			return true;
		}

		@Override
		public T evaluate() {
			TailCall<T> call = this;
			while ( call.isSuspend() ) {
				call = call.resume();
			}
			
			return call.evaluate();
		}

		@Override
		public TailCall<T> resume() {
			return m_work.get();
		}
	}

}
