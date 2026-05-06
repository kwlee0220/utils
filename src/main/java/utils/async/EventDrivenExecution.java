package utils.async;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import utils.LoggerSettable;
import utils.RuntimeInterruptedException;
import utils.Tuple;
import utils.Utilities;
import utils.func.Funcs;
import utils.func.Result;


/**
 * {@link Execution}의 이벤트 기반 기본 구현체.
 * <p>
 * 외부 트리거({@link #notifyStarting()}, {@link #notifyStarted()}, {@link #notifyCompleted(Object)},
 * {@link #notifyFailed(Throwable)}, {@link #notifyCancelling()}, {@link #notifyCancelled()})를 통해
 * 라이프사이클 상태를 전이시키는 방식으로 동작한다. 모든 상태 전이와 listener 호출은
 * 내부 {@link Guard}({@code m_aopGuard})로 직렬화되어 thread-safe하다.
 * <p>
 * 거의 모든 다른 {@link Execution} 구현체({@code AbstractAsyncExecution},
 * {@code AbstractThreadedExecution}, {@code CompletableFutureAsyncExecution},
 * {@link Executions} 내의 chain 구현 등)가 본 클래스를 상속한다.
 *
 * @param <T>	연산 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class EventDrivenExecution<T> implements Execution<T>, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(EventDrivenExecution.class);

	private long m_cancelTimeoutMillis = TimeUnit.SECONDS.toMillis(3);	// 3 seconds
	protected final Guard m_aopGuard = Guard.create();
	@GuardedBy("m_aopGuard") private AsyncState m_aopState = AsyncState.NOT_STARTED;
	@GuardedBy("m_aopGuard") private boolean m_didStart = false;
	@GuardedBy("m_aopGuard") private AsyncResult<T> m_asyncResult;		// may null
	@GuardedBy("m_aopGuard") private final List<Tuple<Runnable,Boolean>> m_startListeners
													= Lists.newArrayList();
	@GuardedBy("m_aopGuard") private final List<Tuple<Consumer<Result<T>>,Boolean>> m_finishListeners
													= Lists.newArrayList();
	
	private Logger m_logger = s_logger;

	@Override
	public AsyncState getState() {
		return m_aopGuard.get(() -> m_aopState);
	}

	@Override
	public boolean isStarted() {
		return m_aopGuard.get(() -> m_didStart);
	}

    @Override
    public void setTimeout(long timeout, TimeUnit unit) {
    	Executions.getTimer().setTimer(this, timeout, unit);
    }
	
	/**
	 * cancel 처리 시 {@code STARTING}/{@code CANCELLING} 등 전이 상태에서 대기할 최대 시간을 설정한다.
	 * <p>
	 * 기본값은 3초. {@link #cancel(boolean)} / {@link #notifyCancelling()} / {@link #notifyCancelled()}
	 * 가 다른 스레드의 상태 전이를 기다릴 때 이 시간이 경과하면 {@code false}를 반환하고 포기한다.
	 *
	 * @param timeout	대기 시간
	 * @param unit		대기 시간 단위
	 */
	public void setCancelTimeout(long timeout, TimeUnit unit) {
		m_cancelTimeoutMillis = unit.toMillis(timeout);
	}

	/**
	 * 작업이 정상 완료되었음을 알린다.
	 * <p>
	 * {@link #notifyCompleted(Object)}의 단순 위임이며, 반환 값을 무시하고 호출하기 위한 편의 메소드.
	 *
	 * @param result	연산 결과
	 */
	public void complete(T result) {
		notifyCompleted(result);
	}

	/**
	 * 작업의 취소를 시도한다.
	 * <p>
	 * 상태별 동작:
	 * <ul>
	 *   <li>{@code NOT_STARTED} → 즉시 {@code CANCELLED}로 전이하고 {@code true} 반환.</li>
	 *   <li>{@code STARTING} 또는 {@code CANCELLING} → {@link #setCancelTimeout(long, TimeUnit)}에서
	 *       정한 시간만큼 상태 변경을 기다린 뒤 다시 검사. 시간 초과 시 {@code false}.</li>
	 *   <li>{@code RUNNING} → {@code mayInterruptIfRunning}이 {@code false}이면서 본 객체가
	 *       {@link CancellableWork}를 구현하지 않은 경우 {@code false} 반환. 그 외에는
	 *       {@code CANCELLING}으로 전이한 후 {@link CancellableWork#cancelWork()}를 lock 외부에서
	 *       호출하고 {@code true} 반환.</li>
	 *   <li>{@code CANCELLED} → 이미 취소된 상태이므로 {@code true} 반환.</li>
	 *   <li>{@code COMPLETED}, {@code FAILED} → 이미 종료된 상태이므로 {@code false} 반환.</li>
	 * </ul>
	 * <p>
	 * 본 메소드는 {@code CANCELLING} 상태로의 전이만 보장하며, 종료 상태({@code CANCELLED})로의
	 * 전이는 {@link CancellableWork} 구현체나 워커 스레드의 협력에 의해 비동기적으로 일어난다.
	 * 종료까지 대기하려면 {@link #waitForFinished()}를 사용한다.
	 *
	 * @param mayInterruptIfRunning	{@code RUNNING} 상태인 작업의 강제 중단을 허용하는지 여부
	 * @return	취소 요청이 접수/처리된 경우 {@code true}, 그렇지 않은 경우 {@code false}
	 */
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		Date due = new Date(System.currentTimeMillis() + m_cancelTimeoutMillis);
		
		m_aopGuard.lock();
		try {
			boolean inTransitionState = true;
			while ( inTransitionState ) {
				switch ( m_aopState ) {
					case NOT_STARTED:
						// 시작되지도 않은 상태이면 바로 cancel된 것으로 세팅하고 바로 반환한다.
						notifyCancelled();
						return true;
					case STARTING:
					case CANCELLING:
						// 'STARTING'/'CANCELLING' 상태인 경우에는 상태가 바뀔때까지
						// 제한시간동안 대기한다.
						if ( !m_aopGuard.awaitSignal(due) ) {
							// 제한 시간이 초과된 경우
							return false;
						}
						break;
					case CANCELLED:
						return true;
					case COMPLETED:
					case FAILED:
						return false;
					default:
						inTransitionState = false;
						break;
				}
			}
			
			// 여기서 상태는 'RUNNING' 밖에 없음
			
			// mayInterruptIfRunning이 false인 경우거나 'CancellableWork'를 implement하지
			// Execution은 시작되지 않는 execution만 취소시킬 수 있기
			// 때문에 이미 RUNNING상태인 경우에는 false를 반환한다.
			if ( !mayInterruptIfRunning && !(this instanceof CancellableWork) ) {
				return false;
			}
			
			// 일단 상태를 'CANCELLING' 상태로 전이시켜 놓고 원래 작업을 중단시킬 준비를 함
			if ( !notifyCancelling() ) {
				return false;
			}
		}
		catch ( InterruptedException e ) {
			throw new RuntimeInterruptedException(e);
		}
		finally {
			m_aopGuard.unlock();
		}
		
		if ( this instanceof CancellableWork canceller ) {
			// 수행 중인 execution을 명시적으로 cancel 시켜야 함.
			try {
				canceller.cancelWork();
			}
			catch ( Exception e ) {
				getLogger().warn("fails to cancel work: {}, cause={}", this, e.toString());
				notifyFailed(e);
				
				return false;
			}
		}
		
		return true;
	}

	@Override
	public T get() throws InterruptedException, ExecutionException, CancellationException {
		try {
			AsyncResult<T> result = waitForFinished();
			
			return result.get();
		}
		catch ( TimeoutException neverHappens ) {
			throw new AssertionError("Should not be here: " + getClass().getName() + "#get()");
		}
	}

	@Override
	public T get(Date due) throws InterruptedException, ExecutionException,
													TimeoutException, CancellationException {
		return waitForFinished(due).get();
	}
	
	@Override
	public void waitForStarted() throws InterruptedException {
		m_aopGuard.awaitCondition(() -> m_didStart).andReturn();
	}

	@Override
	public boolean waitForStarted(Date due) throws InterruptedException {
		return m_aopGuard.awaitCondition(() -> m_didStart, due).andReturn();
	}

	@Override
	public AsyncResult<T> poll() {
		return m_aopGuard.get(() -> isDoneInGuard() ? m_asyncResult : AsyncResult.running());
	}

	@Override
	public AsyncResult<T> waitForFinished(Date due) throws InterruptedException {
		try {
			return m_aopGuard.awaitCondition(() -> isDoneInGuard(), due)
								.andGet(() -> m_asyncResult);
		}
		catch ( TimeoutException e ) {
			return AsyncResult.running();
		}
	}

	@Override
	public AsyncResult<T> waitForFinished() throws InterruptedException {
		return m_aopGuard.awaitCondition(() -> isDoneInGuard())
						.andGet(() -> m_asyncResult);
	}

	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	/**
	 * 작업이 시작 절차를 진행 중임을 알린다.
	 * <p>
	 * 상태별 동작:
	 * <ul>
	 *   <li>{@code NOT_STARTED} → {@code STARTING}으로 전이하고 {@code true} 반환.</li>
	 *   <li>{@code STARTING} → 이미 starting 중이므로 상태 변경 없이 {@code true} 반환 (멱등).</li>
	 *   <li>{@code RUNNING}, {@code CANCELLING}, {@code COMPLETED}, {@code CANCELLED}, {@code FAILED} →
	 *       {@code false} 반환.</li>
	 * </ul>
	 *
	 * @return	{@code STARTING} 상태에 도달했거나 이미 도달해 있는 경우 {@code true}, 그 외 {@code false}
	 */
	public boolean notifyStarting() {
		m_aopGuard.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
					m_aopState = AsyncState.STARTING;
					m_aopGuard.signalAll();
				case STARTING:
			    	return true;
				case RUNNING:
				case COMPLETED:
				case CANCELLED:
				case CANCELLING:
				case FAILED:
					return false;
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.STARTING);
					throw new IllegalStateException(msg);
			}
			
		}
		finally {
			m_aopGuard.unlock();
		}
	}
	
	/**
	 * 작업이 시작되었음을 알린다.
	 * <p>
	 * 다음과 같이 상태 전이 및 반환 값이 결정된다.
	 * <ul>
	 *  <li>{@code NOT_STARTED} 또는 {@code STARTING} 상태인 경우, {@code RUNNING}으로 전이시키고
	 *      등록된 start listener들을 호출한 뒤 true를 반환한다.</li>
	 *  <li>{@code RUNNING} 상태인 경우에는 아무 일도 하지 않고 true를 반환한다.</li>
	 *  <li>{@code CANCELLING}, {@code COMPLETED}, {@code CANCELLED}, {@code FAILED} 상태인 경우에는
	 *      상태 전이 없이 false를 반환한다.</li>
	 * </ul>
	 *
	 * @return	{@code RUNNING} 상태에 도달했거나 이미 도달해 있는 경우 true,
	 * 			그 외의 상태인 경우 false.
	 * 			현재 구현에서는 시작되지 않은 연산은 완료({@code COMPLETED})될 수 없다고 가정하기 때문에,
	 * 			오직 연산이 실패({@code FAILED})하였거나 취소 중({@code CANCELLING})이거나
	 * 			취소된 상태({@code CANCELLED})에서만 false를 반환.
	 */
	public boolean notifyStarted() {
		List<Runnable> asyncStartListeners = List.of();
		boolean started = false;
		m_aopGuard.lock();
		try {
			switch ( m_aopState ) {
				case NOT_STARTED:
				case STARTING:
					m_aopState = AsyncState.RUNNING;
					m_didStart = true;
					m_aopGuard.signalAll();
					getLogger().debug("started: {}", this);

					asyncStartListeners = notifyStartListenersInGuard();
					started = true;
					break;
				case RUNNING:
			    	started = true;
			    	break;
				case COMPLETED:
				case CANCELLED:
				case CANCELLING:
				case FAILED:
					started = false;
					break;
				default:
					// 이 상태는 존재하지 않음.
					// 추후 확장시 실수 탐지용을 사용.
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.RUNNING);
					throw new IllegalStateException(msg);
			}
		}
		finally {
			m_aopGuard.unlock();
		}

		for ( Runnable listener: asyncStartListeners ) {
			CompletableFuture.runAsync(() -> invokeStartListener(listener));
		}

		return started;
	}

	/**
	 * 작업이 정상 완료되었음을 알린다.
	 * <p>
	 * 상태별 동작:
	 * <ul>
	 *   <li>{@code RUNNING} 또는 {@code CANCELLING} → 결과를 저장하고 {@code COMPLETED}로 전이,
	 *       finish listener들을 호출한 뒤 {@code true} 반환.</li>
	 *   <li>{@code COMPLETED}, {@code CANCELLED}, {@code FAILED} → 이미 종료된 상태이므로
	 *       {@code false} 반환.</li>
	 *   <li>{@code NOT_STARTED}, {@code STARTING} → 시작되지 않은 작업은 완료될 수 없으므로
	 *       {@link IllegalStateException} 발생.</li>
	 * </ul>
	 *
	 * @param result	연산 결과
	 * @return	{@code COMPLETED}로 전이한 경우 {@code true}, 이미 종료된 경우 {@code false}
	 * @throws IllegalStateException	아직 시작 전 상태에서 호출된 경우
	 */
	public boolean notifyCompleted(T result) {
		m_aopGuard.lock();
		try {
			switch ( m_aopState ) {
				case RUNNING:
				case CANCELLING:
					m_asyncResult = AsyncResult.completed(result);
					m_aopState = AsyncState.COMPLETED;
					m_aopGuard.signalAll();
					getLogger().debug("completed: {}, result={}", this, result);
					
					notifyFinishListenersInGuard();
			    	return true;
				case CANCELLED:
				case FAILED:
				case COMPLETED:
					return false;
				case NOT_STARTED:
				case STARTING:
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.COMPLETED);
					throw new IllegalStateException(msg);
			}
			
		}
		finally {
			m_aopGuard.unlock();
		}
	}
	
	/**
	 * 작업이 실패했음을 알린다.
	 * <p>
	 * 상태별 동작:
	 * <ul>
	 *   <li>{@code STARTING}, {@code RUNNING}, {@code CANCELLING} → 원인을 저장하고 {@code FAILED}로
	 *       전이, finish listener들을 호출한 뒤 {@code true} 반환.</li>
	 *   <li>{@code COMPLETED}, {@code CANCELLED}, {@code FAILED} → 이미 종료된 상태이므로
	 *       {@code false} 반환.</li>
	 *   <li>{@code NOT_STARTED} → 시작되지 않은 작업은 실패할 수 없으므로 {@link IllegalStateException} 발생.</li>
	 * </ul>
	 *
	 * @param cause	실패 원인
	 * @return	{@code FAILED}로 전이한 경우 {@code true}, 이미 종료된 경우 {@code false}
	 * @throws IllegalStateException	아직 시작 전 상태에서 호출된 경우
	 */
	public boolean notifyFailed(Throwable cause) {
		m_aopGuard.lock();
    	try {
			switch ( m_aopState ) {
				case STARTING:		// start 과정에서 오류가 발생된 경우.
				case RUNNING:
				case CANCELLING:
					m_asyncResult = AsyncResult.failed(cause);
			    	m_aopState = AsyncState.FAILED;
			    	m_aopGuard.signalAll();
					getLogger().info("failed: {}, cause={}", this, cause.toString());
					
					notifyFinishListenersInGuard();
			    	return true;
				case FAILED:
				case COMPLETED:
				case CANCELLED:
					return false;
				case NOT_STARTED:
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.FAILED);
					throw new IllegalStateException(msg);
			}
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
    }
	
	/**
	 * 작업의 취소가 진행 중임을 알린다.
	 * <p>
	 * {@code STARTING} 상태인 경우 시작 작업이 끝날 때까지 (혹은 cancelTimeout 까지) 대기한 뒤 동작한다.
	 * 상태별 동작:
	 * <ul>
	 *   <li>{@code RUNNING} 또는 {@code NOT_STARTED} → {@code CANCELLING}으로 전이하고 {@code true} 반환.</li>
	 *   <li>{@code CANCELLING}, {@code CANCELLED} → 이미 취소 처리된 상태이므로 {@code true} 반환.</li>
	 *   <li>{@code COMPLETED}, {@code FAILED} → {@code false} 반환.</li>
	 *   <li>{@code STARTING} 상태가 cancelTimeout 안에 변경되지 않거나 대기 중 인터럽트되면 {@code false} 반환.</li>
	 * </ul>
	 *
	 * @return	{@code CANCELLING}/{@code CANCELLED} 상태에 도달했거나 이미 도달해 있는 경우 {@code true},
	 * 			그 외의 경우 {@code false}
	 */
	public boolean notifyCancelling() {
		m_aopGuard.lock();
    	try {
    		// 시작 중인 상태이면, cancel 작업이 inconsistent한 상태를 볼 수 있기 때문에,
    		// 일단 start작업이 완료될 때까지 대기한다.
    		Date due = new Date(System.currentTimeMillis() + m_cancelTimeoutMillis);
			while ( m_aopState == AsyncState.STARTING ) {
				try {
					if ( !m_aopGuard.awaitSignal(due) ) {
						return false;
					}
				}
				catch ( InterruptedException e ) {
					return false;
				}
			}
    		
			switch ( m_aopState ) {
				case RUNNING:
				case NOT_STARTED:
					m_aopState = AsyncState.CANCELLING;
					m_aopGuard.signalAll();
				case CANCELLING:
				case CANCELLED:
					return true;
				case COMPLETED:
				case FAILED:
					return false;
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.CANCELLING);
					throw new IllegalStateException(msg);
			}
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
    }
	
	/**
	 * 작업의 취소가 완료되었음을 알린다.
	 * <p>
	 * {@code STARTING} 상태인 경우 시작 작업이 끝날 때까지 (혹은 cancelTimeout 까지) 대기한 뒤 동작한다.
	 * 상태별 동작:
	 * <ul>
	 *   <li>{@code CANCELLING}, {@code RUNNING}, {@code NOT_STARTED} → {@code CANCELLED}로 전이하고
	 *       finish listener들을 호출한 뒤 {@code true} 반환.</li>
	 *   <li>{@code CANCELLED} → 이미 종료된 상태이므로 {@code true} 반환.</li>
	 *   <li>{@code COMPLETED}, {@code FAILED} → {@code false} 반환.</li>
	 *   <li>{@code STARTING} 상태가 cancelTimeout 안에 변경되지 않거나 대기 중 인터럽트되면 {@code false} 반환.</li>
	 * </ul>
	 *
	 * @return	{@code CANCELLED}로 전이한 경우 또는 이미 {@code CANCELLED} 상태인 경우 {@code true},
	 * 			그 외의 경우 {@code false}
	 */
	public boolean notifyCancelled() {
		m_aopGuard.lock();
    	try {
    		// 시작 중인 상태이면, cancel 작업이 inconsistent한 상태를 볼 수 있기 때문에,
    		// 일단 start작업이 완료될 때까지 대기한다.
    		Date due = new Date(System.currentTimeMillis() + m_cancelTimeoutMillis);
    		try {
				while ( m_aopState == AsyncState.STARTING ) {
					if ( !m_aopGuard.awaitSignal(due) ) {
						return false;
					}
				}
			}
			catch ( InterruptedException e ) {
				return false;
			}
    		
			switch ( m_aopState ) {
				case CANCELLING:
				case RUNNING:
				case NOT_STARTED:
					m_asyncResult = AsyncResult.cancelled();
					m_aopState = AsyncState.CANCELLED;
					m_aopGuard.signalAll();
					getLogger().info("cancelled: {}", this);
					
					notifyFinishListenersInGuard();
				case CANCELLED:
			    	return true;
				case COMPLETED:
				case FAILED:
					return false;
				default:
					String msg = String.format("unexpected state: current[%s], event=[%s]",
												m_aopState, AsyncState.CANCELLED);
					throw new IllegalStateException(msg);
			}
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
    }
	
	/**
	 * 종료 상태에 따라 결과를 {@link Result}로 반환한다.
	 * <p>
	 * 본 메소드는 작업이 이미 종료된 상태({@code COMPLETED}/{@code FAILED}/{@code CANCELLED})인
	 * 경우에만 호출해야 한다. 종료 전에 호출하면 내부 결과가 {@code null}이라 NPE 가 발생하거나
	 * {@link IllegalStateException}이 발생할 수 있다.
	 * <p>
	 * 매핑:
	 * <ul>
	 *   <li>{@code COMPLETED} → {@link Result#success(Object)}</li>
	 *   <li>{@code FAILED} → {@link Result#failure(Throwable)}</li>
	 *   <li>{@code CANCELLED} → {@link Result#none()}</li>
	 * </ul>
	 *
	 * @return	종료 상태에 대응하는 {@link Result}
	 * @throws IllegalStateException	내부 결과가 비종료 상태인 경우
	 */
	public Result<T> getResult() {
		return m_aopGuard.get(() -> {
			switch ( m_asyncResult.getState() ) {
				case COMPLETED:
					return Result.success(m_asyncResult.getUnchecked());
				case FAILED:
					return Result.failure(m_asyncResult.getFailureCause());
				case CANCELLED:
					return Result.none();
				default:
					throw new IllegalStateException("invalid execution state: " + m_asyncResult.getState());
			}
		});
	}

	@Override
	public Execution<T> whenStarted(Runnable listener) {
		return _whenStarted(listener, false);
	}
	
	@Override
	public Execution<T> whenStartedAsync(Runnable listener) {
		return _whenStarted(listener, true);
	}

	private Execution<T> _whenStarted(Runnable listener, boolean runAsync) {
		Utilities.checkNotNullArgument(listener, "listener is null");

		List<Runnable> asyncStartListeners = List.of();
		Tuple<Runnable, Boolean> tup = Tuple.of(listener, runAsync);

		m_aopGuard.lock();
    	try {
			if ( !Funcs.exists(m_startListeners, t -> t._1 == listener) ) {
				switch ( m_aopState ) {
					case NOT_STARTED:
					case STARTING:
						// 아직 시작되지 않은 상태이므로 listener를 등록해 놓는다.
						m_startListeners.add(tup);
						break;
					default:
						// 작업이 RUNNING에 도달한 적이 있는 경우에만 listener를 즉시 구동시킨다.
						// NOT_STARTED → CANCELLED, STARTING → FAILED 등 RUNNING에 도달하지 못한
						// 채로 종료된 경우에는 listener를 호출하지 않는다.
						if ( m_didStart ) {
							m_startListeners.add(tup);
							asyncStartListeners = notifyStartListenersInGuard(List.of(tup));
						}
						break;
				}
			}
    	}
    	finally {
    		m_aopGuard.unlock();
    	}

		for ( Runnable al: asyncStartListeners ) {
			CompletableFuture.runAsync(() -> invokeStartListener(al));
		}

		return this;
	}

	@Override
	public Execution<T> whenFinished(Consumer<Result<T>> handler) {
		return _whenFinished(handler, false);
	}
	
	@Override
	public Execution<T> whenFinishedAsync(Consumer<Result<T>> handler) {
		return _whenFinished(handler, true);
	}

	private Execution<T> _whenFinished(Consumer<Result<T>> handler, boolean runAsync) {
		Utilities.checkNotNullArgument(handler, "handler is null");

		m_aopGuard.lock();
    	try {
			Tuple<Consumer<Result<T>>, Boolean> listener = Tuple.of(handler, runAsync);
			if ( !Funcs.exists(m_finishListeners, t -> t._1 == handler) ) {
				m_finishListeners.add(listener);
				
				if ( isDoneInGuard() ) {
					// 이미 종료된 경우에는 바로 수행시킨다.
					notifyFinishListenersInGuard(List.of(listener));
				}
			}

			return this;
    	}
    	finally {
    		m_aopGuard.unlock();
    	}
	}
	
	/**
	 * 다른 {@link Execution}의 라이프사이클을 본 객체로 전파시킨다.
	 * <p>
	 * 매핑:
	 * <ul>
	 *   <li>{@code exec}이 시작되면 본 객체도 {@link #notifyStarted()}.</li>
	 *   <li>{@code exec}이 정상 완료되면 본 객체는 인자로 받은 {@code result}를 결과로 하여
	 *       {@link #notifyCompleted(Object)}. 즉, {@code exec}의 결과 타입과 무관하게 본 객체의
	 *       결과는 호출 시 지정된 {@code result}로 고정된다.</li>
	 *   <li>{@code exec}이 실패하면 본 객체도 동일 원인으로 {@link #notifyFailed(Throwable)}.</li>
	 *   <li>{@code exec}이 취소되면 본 객체도 {@link #notifyCancelled()}.</li>
	 * </ul>
	 *
	 * @param exec		전파 대상 source execution
	 * @param result	source가 정상 완료될 때 본 객체의 결과로 사용할 값
	 */
	public void dependsOn(Execution<?> exec, T result) {
		exec.whenStartedAsync(this::notifyStarted);
		exec.whenFinishedAsync(ret -> {
			ret.ifSuccessful(r -> this.notifyCompleted(result))
				.ifFailed(this::notifyFailed)
				.ifNone(this::notifyCancelled);
		});
	}

	/**
	 * 현재 상태가 {@code CANCELLING}이거나 {@code CANCELLED}이면 {@link CancellationException}을 던진다.
	 * <p>
	 * 작업 본문 내에서 주기적으로 호출하여 협력적인 취소(cooperative cancellation)를 구현하기 위한
	 * 헬퍼이다.
	 *
	 * @throws CancellationException	상태가 {@code CANCELLING} 또는 {@code CANCELLED}인 경우
	 */
	public final void checkCancelled() {
		AsyncState state = getState();
		if ( state == AsyncState.CANCELLING || state == AsyncState.CANCELLED ) {
			throw new CancellationException();
		}
	}
	
	/**
	 * 현재 상태가 {@code CANCELLING}인지 검사한다.
	 * <p>
	 * 작업 본문 내에서 협력적인 취소를 구현할 때 예외를 던지지 않고 분기하기 위해 사용한다.
	 * {@code CANCELLED}는 이미 취소가 완료된 상태이므로 본 메소드는 {@code false}를 반환한다.
	 *
	 * @return	상태가 {@code CANCELLING}이면 {@code true}, 그 외의 경우 {@code false}
	 */
	public final boolean isCancelRequested() {
		return getState() == AsyncState.CANCELLING;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), m_aopState);
	}

	/**
	 * 본 객체의 내부 lock을 획득한 상태에서 주어진 작업을 실행한다.
	 * <p>
	 * 서브클래스/외부 코드가 상태 검사와 추가 동작을 원자적으로 묶어야 할 때 사용한다.
	 * 작업 종료 후 lock 은 자동으로 해제되며 {@code signalAll()}이 호출된다.
	 *
	 * @param work	실행할 작업
	 */
	public void runInAsyncExecutionGuard(Runnable work) {
		m_aopGuard.run(work);
	}

	/**
	 * 본 객체의 내부 lock을 획득한 상태에서 주어진 supplier 의 결과를 반환한다.
	 *
	 * @param <R>		반환 타입
	 * @param supplier	실행할 supplier
	 * @return	supplier 의 반환 값
	 */
	public <R> R getInAsyncExecutionGuard(Supplier<R> supplier) {
		return m_aopGuard.get(supplier);
	}

	/**
	 * 본 작업이 정상 완료되면 그 결과로 새로운 {@link Execution}을 생성하여 chain 을 구성한다.
	 * <p>
	 * 본 객체가 {@code COMPLETED}로 전이되면 {@code mapper}가 결과 값을 받아 다음 {@link Execution}을
	 * 만들고, 새로 생성된 chain execution 의 결과가 반환된 객체의 결과가 된다. 본 객체가 실패/취소
	 * 되는 경우는 동일 사유로 chain 도 실패/취소된다.
	 *
	 * @param <S>		chain 결과 타입
	 * @param mapper	본 객체의 결과를 받아 다음 {@link Execution}을 생성하는 함수
	 * @return			chain 을 표현하는 새 {@link EventDrivenExecution}
	 */
	public <S> EventDrivenExecution<S> flatMapOnCompleted(
									Function<? super T,Execution<? extends S>> mapper) {
		return new Executions.FlatMapCompleteChainExecution<>(this, mapper);
	}
	
	private List<Runnable> notifyStartListenersInGuard(List<Tuple<Runnable, Boolean>> listeners) {
		// 동기 핸들러들을 수행시킨다.
		for ( Tuple<Runnable, Boolean> tup : listeners ) {
			if ( !tup._2 ) {
				invokeStartListener(tup._1);
			}
		}

		// 비동기 핸들러들을 수집하여 반환한다.
		return listeners.stream()
						.filter(t -> t._2)
						.map(t -> t._1)
						.toList();
	}
	
	private List<Runnable> notifyStartListenersInGuard() {
		return notifyStartListenersInGuard(m_startListeners);
	}

	private void invokeStartListener(Runnable listener) {
		try {
			listener.run();
		}
		catch ( Exception e ) {
			getLogger().warn("fails to execute start handler: {}, cause={}",
							listener, e.toString());
		}
	}
	
	private void notifyFinishListenersInGuard(List<Tuple<Consumer<Result<T>>, Boolean>> listeners) {
		final Result<T> result = getResult();

		// 동기 핸들러들을 수행시킨다.
		for ( Tuple<Consumer<Result<T>>, Boolean> tup : listeners ) {
			if ( !tup._2 ) {
				invokeFinishListener(tup._1, result);
			}
		}

		// 비동기 핸들러들을 수행시킨다.
		for ( Tuple<Consumer<Result<T>>, Boolean> tup : listeners ) {
			if ( tup._2 ) {
				CompletableFuture.runAsync(() -> invokeFinishListener(tup._1, result));
			}
		}
	}
	
	private void notifyFinishListenersInGuard() {
		notifyFinishListenersInGuard(m_finishListeners);
	}

	private void invokeFinishListener(Consumer<Result<T>> handler, Result<T> result) {
		try {
			handler.accept(result);
		}
		catch ( Exception e ) {
			getLogger().warn("fails to execute finish handler: {}, cause={}",
							handler, e.toString());
		}
	}
	
	private static boolean isFinished(AsyncState state) {
		switch ( state ) {
			case COMPLETED: case FAILED: case CANCELLED:
				return true;
			default:
				return false;
		}
	}
	
	private boolean isDoneInGuard() {
		return isFinished(m_aopState);
	}
}
