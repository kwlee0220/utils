package utils.stream;

import static utils.Utilities.checkArgument;
import static utils.Utilities.checkNotNullArgument;

import java.util.concurrent.Executor;

import org.checkerframework.checker.nullness.qual.Nullable;
import javax.annotation.concurrent.GuardedBy;

import utils.async.Executions;
import utils.async.Guard;
import utils.async.StartableExecution;
import utils.func.FOption;
import utils.func.Unchecked;
import utils.stream.FStreams.AbstractFStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class MergeParallelFStream<T> extends AbstractFStream<T> {
	private final FStream<? extends FStream<? extends T>> m_fact;
	private final int m_workerCount;
	private final @Nullable Executor m_executor;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private final SuppliableFStream<T> m_outChannel;
	@GuardedBy("m_guard") int m_runningWorkerCount = 0;
	
	MergeParallelFStream(FStream<? extends FStream<? extends T>> fact,
							int workerCount, @Nullable Executor executor) {
		checkNotNullArgument(fact, "mapper is null");
		checkArgument(workerCount > 0);

		m_fact = fact;
		m_workerCount = workerCount;
		m_executor = executor;
		m_outChannel = new SuppliableFStream<>(workerCount);
	}
	
	@Override
	protected void closeInGuard() throws Exception {
		m_guard.run(() -> Unchecked.runOrIgnore(m_outChannel::close));
		Unchecked.runOrIgnore(m_fact::close);
	}

	@Override
	protected void initialize() {
		for ( int i =0; i < m_workerCount; ++i ) {
			++m_runningWorkerCount;
			startNext();
		}
	}

	@Override
	public FOption<T> nextInGuard() {
		return m_outChannel.next();
	}
	
	private void startNext() {
		m_guard.lock();
		try {
			m_fact.next()
				.ifPresent(strm -> {
					StartableExecution<Void> exec = Executions.toExecution(() -> {
						strm.forEach(m_outChannel::supply);
					}, m_executor);
					exec.whenFinished(ret -> startNext());
					exec.start();
				})
				.ifAbsent(() -> {
					if ( --m_runningWorkerCount == 0 ) {
						m_outChannel.endOfSupply();
					}
				});
		}
		finally {
			m_guard.unlock();
		}
	}
}