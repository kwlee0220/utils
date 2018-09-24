package utils.async;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.vavr.control.Option;


/**
 * @param <T>	비동기 연산의 결과 타입
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncExecution<T> extends Future<T> {
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
	
	/**
	 * 비동기 작업을 중단시킨다.
	 * 함수는 작업 중단이 실제로 마무리되기 전에 반환될 수 있기 때문에, 본 메소드 호출한 결과로
	 * 바로 종료되는 것을 의미하지 않는다.
	 * 또한 메소드 호출 당시 비동기 작업 상태에 따라 중단 요청을 무시되기도 한다.
	 * 이는 본 메소드의 반환값이 {@code false}인 경우는 요청이 무시된 것을 의미하고,
	 * 반환 값이 {@code true}인 경우는 중단 요청이 접수되어 중단 작업이 시작된 것을 의미한다.
	 * 물론, 이때도 중단이 반드시 성공하는 것을 의미하지 않는다.
	 * 작업 중단을 확인하기 위해서는 {@link #waitForDone()}이나 {@link #waitForDone(long, TimeUnit)}
	 * 메소드를 사용하여 최종적으로 확인할 수 있다.
	 * 
	 * @return	중단 요청의 접수 여부.
	 */
	public boolean cancel();
	
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
	
    public T get() throws InterruptedException, ExecutionException, CancellationException;
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
    												TimeoutException, CancellationException;
    
	public Option<Result<T>> pollResult();

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
    public Result<T> waitForResult() throws InterruptedException;
	
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
    public Result<T> waitForResult(long timeout, TimeUnit unit)
    	throws InterruptedException, TimeoutException;
    
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
	public void whenDone(Consumer<Result<T>> resultConsumer);
	
	public default void whenCompleted(Consumer<T> handler) {
		Objects.requireNonNull(handler, "handler is null");
		
		whenDone(r -> r.ifCompleted(handler));
	}
	
	public default void whenFailed(Consumer<Throwable> handler) {
		Objects.requireNonNull(handler, "handler is null");
		
		whenDone(r -> r.ifFailed(handler));
	}
	
	public default void whenCancelled(Runnable handler) {
		Objects.requireNonNull(handler, "handler is null");
		
		whenDone(r -> r.ifCancelled(handler));
	}
}
