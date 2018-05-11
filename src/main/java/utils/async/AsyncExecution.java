package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import utils.func.Result;


/**
 * @param <V>	비동기 연산의 결과 타입
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncExecution<V> extends Future<V>, AutoCloseable {
	public enum State { NOT_STARTED, RUNNING, COMPLETED, FAILED, CANCELLED };
	
	/**
	 * 비동기 작업 시작을 요청한다.
	 * 함수는 비동기 작업이 실제로 시작되기 전에 반환될 수 있기 때문에, 본 메소드의
	 * 반환이 작업을 시작되었음을 의미하지 않는다.
	 * 비동기 작업이 성공적으로 시작될 때까지 대기하려는 경우는 명시적으로
	 * {@link #waitForStarted()} 또는 {@link #waitForStarted(long, TimeUnit)}를
	 * 호출하여야 한다.
	 */
	public void start() throws IllegalStateException;
	public void cancel() throws IllegalStateException;
	
	public State getState();
	
	public default boolean isStarted() {
		return getState() != State.NOT_STARTED;
	}
	
	public default boolean isCompleted() {
		return getState() == State.COMPLETED;
	}
	
	public default boolean isFailed() {
		return getState() == State.FAILED;
	}
	
	public default boolean isCancelled() {
		return getState() == State.CANCELLED;
	}
	
	public default boolean isDone() {
		State state = getState();
		return state == State.COMPLETED || state == State.FAILED
			|| getState() == State.CANCELLED;
	}
	
    public V get() throws InterruptedException, ExecutionException, CancellationException;
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
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
    public default Result<V> getResult() throws InterruptedException {
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
	 * @param timeout	제한시간
	 * @param unit		제한시간 단위
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Result#isSuccess()}가 {@code true},
	 * 			오류가 발생되어 종료된 경우는 {@link Result#isFailure()}가 {@code true},
	 * 			또는 작업이 취소되어 종료된 경우거나 시간제한으로 반환되는 경우
	 * 			{@link Result#isEmpty()}가 {@code true}가 됨.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 * @throws TimeoutException	작업 종료 대기 중 시간제한이 걸린 경우.
	 */
    public default Result<V> getResult(long timeout, TimeUnit unit)
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

	public void whenStarted(Runnable listener);
	public void whenDone(Consumer<Result<V>> resultConsumer);
}
