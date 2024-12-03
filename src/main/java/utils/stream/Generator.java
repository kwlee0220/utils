package utils.stream;

import static utils.Utilities.checkArgument;

import java.util.concurrent.Executor;

import javax.annotation.Nonnull;

import com.google.common.base.Preconditions;

import utils.func.CheckedRunnable;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class Generator<T> extends SuppliableFStream<T> implements CheckedRunnable {
	/**
	 * Generator 객체를 생성한다.
	 * 
	 * Generator 객체가 생성되면 내부적으로 데이터를 생성하는 thread가 시작되어
	 * abstract method인 {@link #run()}을 수행시킨다.
	 * 생성된 generator는 내부적으로 길이 {@code length}의 버퍼를 통해 스트림에 데이터를 제공한다.
	 *
	 * @param length	버퍼 길이. 버퍼 길이는 0보다 커야한다.
	 */
	public Generator(int length) {
		super(length);
		checkArgument(length > 0, "Buffer length should be larger than zero.");
		
		Thread generator = new Thread(m_wrapper, "generator");
		generator.start();
	}

	/**
	 * Generator 객체를 생성한다.
	 * 
	 * Generator 객체가 생성되면 내부적으로 주어진 thread pool에서 thread를 할당받아
	 * abstract methoddls {@link #run()}을 수행시킨다.
	 * 생성된 generator는 내부적으로 길이 {@code length}의 버퍼를 갖는다.
	 *
	 * @param length	버퍼 길이. 버퍼 길이는 0보다 커야한다.
	 * @param executor	내부 쓰레드가 사용한 {@link Executor} 객체.
	 */
	public Generator(int length, @Nonnull Executor executor) {
		super(length);
		Preconditions.checkArgument(executor != null);
		Preconditions.checkArgument(length > 0, "Buffer length should be larger than zero.");
		
		executor.execute(m_wrapper);
	}
	
	/**
	 * Generator에 주어진 데이터를 추가시킨다.
	 * 
	 * @param value		생성한 데이터.
	 */
	public final void yield(T value) {
		Preconditions.checkArgument(value != null);
		
		supply(value);
	}
	
	private final Runnable m_wrapper = new Runnable() {
		@Override
		public void run() {
			try {
				Generator.this.run();
				Generator.this.endOfSupply();
			}
			catch ( Throwable e ) {
				Generator.this.endOfSupply(e);
			}
		}
	};
}
