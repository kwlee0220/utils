package utils.stream;

import utils.func.CheckedRunnable;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class Generator<T> extends SuppliableFStream<T> implements CheckedRunnable {
	private Thread m_generator;
	
	public Generator(int length) {
		super(length);
		
		m_generator = new Thread(m_wrapper, "generator");
		m_generator.start();
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
