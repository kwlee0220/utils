package utils.async;

import utils.stream.FStream;
import utils.stream.SuppliableFStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
// FIXME: 삭제 대상
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
		// 'exec' 작업을 수행시키는 과정은
		// 먼저 이 작업이 종료될 때 수행할 작업을 설정하고
		// 그 다음에 작업을 시작한다.
		exec.whenFinishedAsync(ret -> {
			if ( ret.isSuccessful() ) {
				// 작업 결과로 생성된 후속 {@link StartableExecution} 결과 FStream에 추가한다.
				output.supply(ret.getUnchecked());
			}
			else if ( ret.isNone() ) {
				// 작업 결과가 없으면 후속 작업을 결과 FStream에 추가하지 않는다.
				return;
			}
			
			// 입력 스트림에 남아있는 작업을 수행한다.
			m_executions.next()
						.ifPresent(n -> run(n, output));
		});
		exec.start();
	}
}