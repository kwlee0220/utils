package utils.stream;

import static utils.Utilities.checkNotNullArgument;

import java.util.function.Function;

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
class FlatMapUnorderedAsyncStream<S,T> extends AbstractFStream<T> {
	private final FStream<StartableExecution<FStream<T>>> m_execStream;
	
	private final AsyncExecutionOptions m_options;
	private final Guard m_guard = Guard.create();
	private final SuppliableFStream<T> m_outChannel;
	@GuardedBy("m_guard")  int m_runningWorkerCount = 0;
	
	FlatMapUnorderedAsyncStream(FStream<S> src, Function<? super S, ? extends FStream<T>> mapper,
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
	public FOption<T> nextInGuard() {
		return m_outChannel.next();
	}
	
	private void startNext() {
		m_guard.lock();
		try {
			StartableExecution<FStream<T>> next = m_execStream.next().getOrNull();
			if ( next != null ) {
				next.whenFinished(ret -> {
					ret.ifSuccessful(strm -> strm.forEach(m_outChannel::supply));
					startNext();
				});
				next.start();
			}
			else {
				if ( --m_runningWorkerCount == 0 ) {
					m_outChannel.endOfSupply();
				}
			}
		}
		finally {
			m_guard.unlock();
		}
	}
}