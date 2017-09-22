package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import utils.func.Result;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncExecution<T> extends Future<T>, AutoCloseable {
	public boolean start();
	public boolean cancel();
	
	public boolean isStarted();
	public boolean isCompleted();
	public boolean isFailed();
	
	public default boolean isDone() {
		return isCompleted() || isFailed() || isCancelled();
	}
	
    public T get() throws InterruptedException, ExecutionException, CancellationException;
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
    												TimeoutException, CancellationException;

	/**
	 * 비동기 작업이 종료될 때까지 기다려 그 결과를 반환한다.
	 * 
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Result#isSuccess()}가 {@code true},
	 * 			오류가 발생되어 종료된 경우는 {@link Result#isFailure()}가 {@code true},
	 * 			또는 작업이 취소되어 종료된 경우는 {@link Result#isEmpty()}가
	 * 			{@code true}가 됨.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
    public default Result<T> getResult() throws InterruptedException {
    	try {
    		return Result.some(get());
    	}
    	catch ( ExecutionException e ) {
    		return Result.failure(e.getCause());
    	}
    	catch ( CancellationException e ) {
    		return Result.none();
    	}
    }
	
	/**
	 * 본 작업이 제한시간 동안 종료될 때까지 기다려 그 결과를 반환한다.
	 * 
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Result#isSuccess()}가 {@code true},
	 * 			오류가 발생되어 종료된 경우는 {@link Result#isFailure()}가 {@code true},
	 * 			또는 작업이 취소되어 종료된 경우거나 시간제한으로 반환되는 경우
	 * 			{@link Result#isEmpty()}가 {@code true}가 됨.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 * @throws TimeoutException	작업 종료 대기 중 시간제한이 걸린 경우.
	 */
    public default Result<T> getResult(long timeout, TimeUnit unit)
    	throws InterruptedException, TimeoutException {
    	try {
    		return Result.some(get(timeout, unit));
    	}
    	catch ( ExecutionException e ) {
    		return Result.failure(e.getCause());
    	}
    	catch ( CancellationException e ) {
    		return Result.none();
    	}
    }
    
	/**
	 * 비동기 작업이 시작될 때까지 대기한다.
	 * 
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public void waitForStarted() throws InterruptedException;

	/**
	 * 비동기 작업이 시작될 때까지 제한된 시간 동안만 대기한다.
	 * 
	 * @param timeout	대기시간
	 * @param unit		대기시간 단위
	 * @return	제한시간 전에 성공적으로 반환하는 경우는 {@code true},
	 * 			대기 중에 제한시간 경과로 반환되는 경우는 {@code false}.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public boolean waitForStarted(long timeout, TimeUnit unit) throws InterruptedException;

	/**
	 * 비동기 작업이 종료될 때까지 대기한다.
	 * 
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public void waitForDone() throws InterruptedException;

	/**
	 * 본 작업이 종료될 때까지 제한된 시간 동안만 대기한다.
	 * 
	 * @param timeout	대기시간
	 * @param unit		대기시간 단위
	 * @return	제한시간 전에 성공적으로 반환하는 경우는 {@code true},
	 * 			대기 중에 제한시간 경과로 반환되는 경우는 {@code false}.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public boolean waitForDone(long timeout, TimeUnit unit) throws InterruptedException;

	public void whenStarted(Consumer<AsyncExecution<T>> listener);
	public void whenDone(Consumer<AsyncExecution<T>> result);
}
