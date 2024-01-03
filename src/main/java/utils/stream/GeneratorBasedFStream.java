package utils.stream;

import static utils.Utilities.checkArgument;

import java.util.concurrent.CancellationException;

import utils.Suppliable;
import utils.func.CheckedConsumer;
import utils.func.FOption;
import utils.stream.FStreams.AbstractFStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
class GeneratorBasedFStream<T> extends AbstractFStream<T> {
	private final CheckedConsumer<Suppliable<T>> m_dataGenerator;
	private final SuppliableFStream<T> m_channel;
	private Thread m_generatorThread;
	
	GeneratorBasedFStream(CheckedConsumer<Suppliable<T>> dataGenerator, int length) {
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
	public FOption<T> next() {
		if ( m_generatorThread == null ) {
			m_generatorThread = new Thread(new DataGeneratingWorker<>(m_dataGenerator, m_channel), "generator");
			m_generatorThread.start();
		}
		
		return m_channel.next();
	}
	
	private static class DataGeneratingWorker<T> implements Runnable {
		private final CheckedConsumer<Suppliable<T>> m_worker;
		private final Suppliable<T> m_outChannel;
		
		DataGeneratingWorker(CheckedConsumer<Suppliable<T>> worker, Suppliable<T> channel) {
			m_worker = worker;
			m_outChannel = channel;
		}
		
		@Override
		public void run() {
			try {
				m_worker.accept(m_outChannel);
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
