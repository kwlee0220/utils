package utils.stream;

import static utils.Utilities.checkNotNullArgument;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import utils.async.Guard;
import utils.func.FOption;
import utils.func.Try;
import utils.func.Unchecked;
import utils.stream.FStreams.AbstractFStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class MapAsyncStream<S,T> extends AbstractFStream<Try<T>> {
	private final FStream<S> m_src;
	private final Function<? super S,? extends T> m_mapper;
	private final int m_workerCount;
	private final Executor m_executor;
	private final Guard m_guard = Guard.create();
	private final SuppliableFStream<Try<T>> m_outChannel;
	private boolean m_started = false;
	private int m_workerRemains;
	
	MapAsyncStream(FStream<S> src, Function<? super S,? extends T> mapper,
					FOption<Integer> workerCount,
					FOption<Executor> executor) {
		checkNotNullArgument(mapper, "mapper is null");
		
		m_src = src;
		m_mapper = mapper;
		m_executor = executor.getOrNull();
		
		m_workerCount = workerCount.getOrElse(() -> Math.max(1, Runtime.getRuntime().availableProcessors()-2));
		m_outChannel = new SuppliableFStream<>(m_workerCount);
	}
	
	@Override
	protected void closeInGuard() throws Exception {
		m_guard.run(() -> Unchecked.runOrIgnore(m_outChannel::close));
		Unchecked.runOrIgnore(m_src::close);
	}
	
	private void start() {
		m_workerRemains = m_workerCount;
		if ( m_executor != null ) {
			for ( int i =0; i < m_workerCount; ++i ) {
				CompletableFuture.runAsync(m_worker, m_executor);
			}
		}
		else {
			for ( int i =0; i < m_workerCount; ++i ) {
				CompletableFuture.runAsync(m_worker);
			}
		}
	}
	
	private final Runnable m_worker = new Runnable() {
		@Override
		public void run() {
			while ( true ) {
				FOption<S> input = m_guard.get(() -> m_src.next());
				if ( input.isAbsent() ) {
					break;
				}
				
				try {
					T output = m_mapper.apply(input.get());
					m_guard.consume(m_outChannel::supply, Try.success(output));
				}
				catch ( Throwable e ) {
					m_guard.consume(m_outChannel::supply, Try.<T>failure(e));
				}
			}
			
			boolean idle = m_guard.get(() ->  (--m_workerRemains) == 0);
			if ( idle ) {
				m_outChannel.endOfSupply();
			}
		}
	};

	@Override
	public FOption<Try<T>> next() {
		if ( !m_started ) {
			m_started = true;
			start();
		}
		return m_outChannel.next();
	}
}