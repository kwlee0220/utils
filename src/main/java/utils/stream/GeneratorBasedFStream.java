package utils.stream;

import static utils.Utilities.checkArgument;
import static utils.Utilities.checkNotNullArgument;

import java.util.concurrent.CancellationException;

import utils.Suppliable;
import utils.func.FOption;
import utils.stream.FStreams.AbstractFStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
class GeneratorBasedFStream<T> extends AbstractFStream<T> {
	private final Generator<T> m_dataGenerator;
	private final SuppliableFStream<T> m_channel;
	private Thread m_generatorThread;
	
	GeneratorBasedFStream(Generator<T> dataGenerator, int length) {
		checkNotNullArgument(dataGenerator, "generator is null");
		checkArgument(length > 0, "Buffer length should be larger than zero.");
		
		m_dataGenerator = dataGenerator;
		m_channel = FStream.pipe(length);
	}

	@Override
	protected void closeInGuard() throws Exception {
		m_generatorThread.interrupt();
		m_channel.close();
	}

	@Override
	public FOption<T> nextInGuard() {
		if ( m_generatorThread == null ) {
			m_generatorThread = new Thread(new DataGeneratingWorker<>(m_dataGenerator, m_channel), "generator");
			m_generatorThread.start();
		}
		
		return m_channel.next();
	}
	
	private static class DataGeneratingWorker<T> implements Runnable {
		private final Generator<T> m_dataGenerator;
		private final Suppliable<T> m_outChannel;
		
		DataGeneratingWorker(Generator<T> dataGenerator, Suppliable<T> channel) {
			m_dataGenerator = dataGenerator;
			m_outChannel = channel;
		}
		
		@Override
		public void run() {
			try {
				m_dataGenerator.generate(m_outChannel);
				m_outChannel.endOfSupply();
			}
			catch ( InterruptedException | CancellationException expected ) {
				m_outChannel.endOfSupply();
			}
			catch ( Throwable e ) {
				m_outChannel.endOfSupply(e);
			}
		}
	}
}
