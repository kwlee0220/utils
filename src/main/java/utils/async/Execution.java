package utils.async;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import utils.Throwables;
import utils.async.AsyncResult.Cancelled;
import utils.async.AsyncResult.Completed;
import utils.async.AsyncResult.Failed;
import utils.async.AsyncResult.Running;


/**
 * 연산 수행 인터페이스를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Execution<T> extends Future<T> {
	public interface FinishListener<T> {
		public void onCompleted(T result);
		public void onFailed(Throwable cause);
		public void onCancelled();
	}
	
	/**
	 * 연산 수행을 중단시킨다.
	 * <p>
	 * 메소드 호출은 연산이 완전히 중단되기 전에 반환될 수 있기 때문에, 본 메소드 호출한 결과로
	 * 바로 종료되는 것을 의미하지 않는다.
	 * 또한 메소드 호출 당시 작업 상태에 따라 중단 요청을 무시되기도 한다.
	 * 이는 본 메소드의 반환값이 {@code false}인 경우는 요청이 명시적으로 무시된 것을 의미한다.
	 * 반환 값이 {@code true}인 경우는 중단 요청이 접수되어 중단 작업이 시작된 것을 의미한다.
	 * 물론, 이때도 중단이 반드시 성공하는 것을 의미하지 않는다.
	 * 
	 * 작업 중단을 확인하기 위해서는 {@link #pollInfinite()}이나 {@link #waitForDone(long, TimeUnit)}
	 * 메소드를 사용하여 최종적으로 확인할 수 있다.
	 * 
	 * @param mayInterruptIfRunning	 이미 동적 중인 경우에도 cancel할지 여부
	 * @return	중단 요청의 접수 여부.
	 */
	public boolean cancel(boolean mayInterruptIfRunning);
	
	/**
	 * 연산 수행 상태를 반환한다.
	 * 
	 * @return	연산 수행 상태.
	 */
	public AsyncState getState();
	
	/**
	 * 연산 수행이 시작되었는지를 반환한다.
	 * <p>
	 * 연산이 시작되어 이미 종료된 경우에도 {@code true}가 반환된다.
	 * 
	 * @return	연산의 시작 여부.
	 */
	public default boolean isStarted() {
		return getState().getCode() >= AsyncState.STARTING.getCode();
	}
	
	/**
	 * 연산이 수행 중인지 여부를 반환한다.
	 * 
	 * @return	수행 중인 경우는 {@code true} 그렇지 않은 경우는 {@code false}를 반환한다.
	 */
	public default boolean isRunning() {
		return getState() == AsyncState.RUNNING;
	}

	/**
	 * 연산 수행이 성공적으로 마치고 종료되었는지를 반환한다.
	 * 
	 * @return	연산의 성공 종료 여부.
	 */
	public default boolean isCompleted() {
		return getState() == AsyncState.COMPLETED;
	}

	/**
	 * 연산 수행 중 예외 발생으로 종료되었는지를 반환한다.
	 * 
	 * @return	연산의 실패 종료 여부.
	 */
	public default boolean isFailed() {
		return getState() == AsyncState.FAILED;
	}

	/**
	 * 연산 수행 중 강제로 중단되었는지를 반환한다.
	 * 
	 * @return	연산의 중단 종료 여부.
	 */
	public default boolean isCancelled() {
		return getState() == AsyncState.CANCELLED;
	}
	
	/**
	 * 연산 수행의 종료 여부를 반환한다.
	 * <p>
	 * 연산 수행은 성공적 종료, 예외 발생으로 인한 실패 종료, 그리고
	 * 강제 중단에 따른 종료를 포함한다.
	 * 
	 * @return	연산 종료 여부.
	 */
	public default boolean isDone() {
		AsyncState state = getState();
		return state == AsyncState.COMPLETED || state == AsyncState.FAILED
			|| state == AsyncState.CANCELLED;
	}
	
	/**
	 * 연산 수행 결과를 반환한다.
	 * <p>
	 * 메소드 호출 당시 연산이 아직 종료되지 않은 경우는 연산이 종료될 때까지 대기한다.
	 * 만일 대기 중에 중단 경우에는 {@code InterruptedException} 예외가 발생된다.
	 * <p>
	 * 연산이 성공적으로 종료된 경우에는 수행 결과를 반환하지만, 그렇지 않은 경우는
	 * 종료 방식에 따라 해당하는 예외를 발생시킨다.
	 * <ul>
	 * 	<li> 예외 발생으로 종료된 경우는 {@code ExecutionException} 예외를 발생시킨다.
	 * 		이때 발생된 예외 객체는 {@code ExecutionException#getCause()}를 통해 얻을 수 있다.
	 * 	<li> 수행 중 중단 경우에는 {@code CancellationException} 예외를 발생시킨다.
	 * </ul>
	 * 
	 * @return 수행 결과
	 * @throws InterruptedException	수행 도중 쓰레드 인터럽트로 중단된 경우.
	 * @throws CancellationException	수행 도중 중단된 경우.
	 * @throws ExecutionException	수행 도중 예외가 발생된 경우.
	 */
    public T get() throws InterruptedException, ExecutionException, CancellationException;
    public default T get(long timeout, TimeUnit unit)
    	throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
    	Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
    	return get(due);
    }
    public T get(Date due) throws InterruptedException, ExecutionException,
									TimeoutException, CancellationException;
    
    public default T getUnchecked() {
    	try {
    		return get();
    	}
    	catch ( Throwable e ) {
    		throw Throwables.toRuntimeException(Throwables.unwrapThrowable(e));
    	}
    }
	
	/**
	 * 본 작업이 제한시간 동안 종료될 때까지 기다려 그 결과를 반환한다.
	 * 
	 * @param due	대기 제한 시간.
	 * 				주어진 시간을 경과하면 {@link Running}을 반환한다.
	 * @return	작업 수행 결과.
	 * 			성공적으로 종료된 경우는 {@link Completed}가 반환되고,
	 * 			오류가 발생되어 종료된 경우는 {@link Failed}가 반환되고,
	 * 			작업이 취소되어 종료된 경우는 {@link Cancelled}가 반환되며,
	 *			시간제한으로 반환되는 경우에는 {@link Running}가 반환된다.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
    public AsyncResult<T> poll(Date due) throws InterruptedException;
	
	/**
	 * 본 작업이 제한시간 동안 종료될 때까지 기다려 그 결과를 반환한다.
	 * 
	 * @param timeout	제한시간
	 * @param unit		제한시간 단위
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Completed}가 반환되고,
	 * 			오류가 발생되어 종료된 경우는 {@link Failed}가 반환되고,
	 * 			작업이 취소되어 종료된 경우는 {@link Cancelled}가 반환되며,
	 *			시간제한으로 반환되는 경우에는 {@link Running}가 반환된다.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
    public default AsyncResult<T> poll(long timeout, TimeUnit unit) throws InterruptedException {
    	Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
    	return poll(due);
    }
    
    /**
     * 본 작업의 수행결과를 반환한다.
     * 
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Completed}가 반환되고,
	 * 			오류가 발생되어 종료된 경우는 {@link Failed}가 반환되고,
	 * 			작업이 취소되어 종료된 경우는 {@link Cancelled}가 반환되며,
	 *			시간제한으로 반환되는 경우에는 {@link Running}가 반환된다.
     */
	public AsyncResult<T> poll();

	/**
	 * 비동기 작업이 종료될 때까지 기다려 그 결과를 반환한다.
	 * 
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Completed}가 반환되고,
	 * 			오류가 발생되어 종료된 경우는 {@link Failed}가 반환되고,
	 * 			작업이 취소되어 종료된 경우는 {@link Cancelled}가 반환된다.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
    public AsyncResult<T> pollInfinite() throws InterruptedException;
    
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
	public default boolean waitForStarted(long timeout, TimeUnit unit)
		throws InterruptedException {
    	Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
    	return waitForStarted(due);
	}
    public boolean waitForStarted(Date due) throws InterruptedException;
	
	public default <S> EventDrivenExecution<S> map(Function<AsyncResult<? extends T>,? extends S> mapper) {
		return new Executions.MapChainExecution<>(this, mapper);
	}
    
    public default <S> Execution<S> mapOnCompleted(Function<? super T,? extends S> mapper) {
    	return new Executions.MapCompleteChainExecution<>(this, mapper);
    }

	public Execution<T> whenStarted(Runnable listener);
	public Execution<T> whenFinished(Consumer<AsyncResult<T>> resultConsumer);
	public default void whenFinished(FinishListener<T> listener) {
		whenFinished(r -> r.ifCompleted(listener::onCompleted)
							.ifCancelled(listener::onCancelled)
							.ifFailed(listener::onFailed));
	}
	
	public default void whenCompleted(Consumer<T> handler) {
		Objects.requireNonNull(handler, "handler is null");
		
		whenFinished(new SimpleFinishListener<T>() {
			@Override
			public void onCompleted(T result) {
				handler.accept(result);
			}
		});
	}
	
	public default void whenFailed(Consumer<Throwable> handler) {
		Objects.requireNonNull(handler, "handler is null");
		
		whenFinished(new SimpleFinishListener<T>() {
			@Override
			public void onFailed(Throwable cause) {
				handler.accept(cause);
			}
		});
	}
	
	public default void whenCancelled(Runnable handler) {
		Objects.requireNonNull(handler, "handler is null");
		
		whenFinished(new SimpleFinishListener<T>() {
			@Override
			public void onCancelled() {
				handler.run();
			}
		});
	}
	
//	public default Observable<ExecutionProgress<T>> observe(boolean cancelOnDispose) {
//		return Observable.create(new ExecutionProgressReport<T>(this, cancelOnDispose));
//	}
	
	@SuppressWarnings("unchecked")
	public static <T> Execution<T> narrow(Execution<? extends T> exec) {
		return (Execution<T>)exec;
	}
}
