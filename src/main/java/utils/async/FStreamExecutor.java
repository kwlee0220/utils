package utils.async;

import utils.stream.FStream;
import utils.stream.SuppliableFStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FStreamExecutor<T> {
	private final FStream<StartableExecution<T>> m_executions;
	private final int m_concurrency;
	
	public FStreamExecutor(FStream<StartableExecution<T>> executions) {
		m_executions = executions;
		m_concurrency = 5;
	}
	
	public FStream<T> execute() {
		SuppliableFStream<T> output = FStream.pipe(m_concurrency);
		m_executions.take(m_concurrency)
					.forEach(exec -> run(exec, output));
		return output;
	}
	
	private void run(StartableExecution<T> exec, SuppliableFStream<T> output) {
		exec.whenFinishedAsync(ret -> {
			if ( ret.isSuccessful() ) {
				output.supply(ret.getUnchecked());
			}
			else if ( ret.isNone() ) {
				return;
			}
			m_executions.next()
						.ifPresent(n -> run(n, output));
		});
		exec.start();
	}
}