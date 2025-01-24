package utils.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import utils.async.CompletableFutureAsyncExecution;
import utils.async.Execution;
import utils.async.Executions;
import utils.async.StartableExecution;
import utils.func.Result;
import utils.stream.FStream;
import utils.stream.SuppliableFStream;


/**
 * {@code CompletionFStream}는 수행 중인 복수의 {@link CompletableFuture} 객체들을
 * 관리하며 그 수행이 종료된 경우 그 결과를 순차적으로 얻을 수 있도록 하는
 * {@link FStream} 구현체이다.
 * <p>
 * Submit된 작업은 수행 시간이 서로 다를 수 있기 때문에 {@link FStream#next}를 통해 얻는
 * 작업 수행 결과는 submit된 순서와 다를 수 있다.
 * {@code #next()} 호출 당시 종료된 작업이 없는 경우에는 수행 중인 작업이 종료될 때까지 대기하게 된다.
 * <p>
 * 이미 close되거나 endOfSupply 상태인 경우에는 추가의 작업을 submit할 수 없으며,
 * 호출된 경우에는 {@link IllegalStateException} 예외가 발생된다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CompletionFStream<T> extends SuppliableFStream<Result<T>> {
	public CompletionFStream(int bufferLength) {
		super(bufferLength);
	}
	
	/**
	 * 주어진 작업 ({@link Execution})이 수행 종료되면 그 결과를 수집한다
	 * <p>
	 * 작업이 시작되지 않고 작업이 {@link StartableExecution} 인 경우는 시작시킨다.
	 *
	 * @param exec	작업 수행 객체
	 * @return	작업 수행 상태를 확인할 수 있는 {@link Execution} 객체
	 */
	public Execution<T> submit(Execution<T> exec) {
		if ( !exec.isStarted() ) {
			if ( exec instanceof StartableExecution ) {
				((StartableExecution<T>) exec).start();
			}
			else {
				throw new IllegalArgumentException("Execution is not started: " + exec);
			}
		}
		
		exec.whenFinished(result -> supply(result));
		return exec;
	}

	/**
	 * 주어진 작업 ({@link Callable}) 시작시키도록 submit한다.
	 * <p>
	 * 이미 close되었거나 isEndOfSupply 상태인 경우에는 {@link IllegalStateException} 예외가 발생된다.
	 * 
	 * @param supplier 수행할 작업 {@link Callable} 객체
	 * @param executor 작업을 수행할 {@link Executor} 객체.
	 * 					{@code null}인 경우는 별도의 Executor를 사용하지 않는다.
	 * @return 작업 수행 상태를 확인할 수 있는 {@link Execution} 객체
	 * @throws IllegalStateException 이미 close되었거나 isEndOfSupply 상태인 경우
	 */
	public Execution<T> submit(Callable<T> callable, Executor executor) throws IllegalStateException {
		CompletableFutureAsyncExecution<T> exec = Executions.supplyCheckedAsync(() -> callable.call(), executor);
		return submit(exec);
	}
	
//	public Execution<T> submit(CheckedSupplier<T> supplier, Executor executor) throws IllegalStateException {
//		CompletableFutureAsyncExecution<T> exec = Executions.supplyCheckedAsync(supplier, executor);
//		return submit(exec);
//	}
}
