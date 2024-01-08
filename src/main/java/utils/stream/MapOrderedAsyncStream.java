package utils.stream;

import static utils.Utilities.checkNotNullArgument;

import java.util.function.Function;

import javax.annotation.concurrent.GuardedBy;

import utils.async.Executions;
import utils.async.Guard;
import utils.async.StartableExecution;
import utils.func.FOption;
import utils.func.Try;
import utils.func.Unchecked;
import utils.stream.FStreams.AbstractFStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class MapOrderedAsyncStream<S,T> extends AbstractFStream<Try<T>> {
	private final FStream<StartableExecution<T>> m_execStream;
	
	private final AsyncExecutionOptions m_options;
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private final SuppliableFStream<StartableExecution<T>> m_outChannel;
	@GuardedBy("m_guard") int m_runningWorkerCount = 0;
	
	MapOrderedAsyncStream(FStream<S> src, Function<? super S, ? extends T> mapper,
							AsyncExecutionOptions options) {
		checkNotNullArgument(mapper, "mapper is null");
		
		m_options = options;
		m_execStream = src.map((S input) -> Executions.supplyAsync(() -> mapper.apply(input),
																	options.getExecutor()));
		m_outChannel = new SuppliableFStream<>(options.getWorkerCount());
	}
	
	@Override
	protected void closeInGuard() throws Exception {
		m_guard.run(() -> Unchecked.runOrIgnore(m_outChannel::close));
		Unchecked.runOrIgnore(m_execStream::close);
	}
	
	@Override
	protected void initialize() {
		for ( int i =0; i < m_options.getWorkerCount(); ++i ) {
			++m_runningWorkerCount;
			startNext();
		}
	}

	@Override
	public FOption<Try<T>> nextInGuard() {
		return m_outChannel.next()
							.map(exec -> Try.get(() -> exec.waitForFinished().get()))
							.ifPresent(trial -> startNext());
	}
	
	private void startNext() {
		m_guard.lock();
		try {
			m_execStream.next()
						.ifPresent(exec -> {
							m_outChannel.supply(exec);
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