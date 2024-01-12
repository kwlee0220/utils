package utils.func;

import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class TailCall<T> {
	public abstract boolean isSuspend();
	public abstract T evaluate();
	public abstract TailCall<T> resume();
	
	private TailCall() { }
	
	public static <T> TailCall<T> returns(T result) {
		return new Return<>(result);
	}
	
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
