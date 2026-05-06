package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import utils.Throwables;

/**
 * 별도 스레드(또는 외부 주입된 {@link Executor})를 이용하여 비동기 연산을 수행하는
 * {@link Execution} 구현체의 추상 베이스 클래스.
 * <p>
 * Concrete 서브클래스는 추상 메소드 {@link #executeWork()}만 구현하면 된다.
 * 본 클래스가 다음을 처리한다.
 * <ul>
 *   <li>{@link #start()} 호출 시 {@link AbstractAsyncExecution#getExecutor()}가 설정되어 있으면
 *       해당 {@code Executor}에 작업을 제출하고, 그렇지 않으면 새 {@link Thread}를 생성하여 수행
 *       (스레드의 daemon 여부는 {@link #setDaemonThread(boolean)}으로 제어).</li>
 *   <li>{@code executeWork()}의 정상 반환 → {@code COMPLETED} 전이.</li>
 *   <li>{@code InterruptedException}/{@code CancellationException} → {@code CANCELLED} 전이.</li>
 *   <li>그 외 {@code Exception} → {@code FAILED} 전이 (원인은 {@link Throwables#unwrapThrowable}).</li>
 *   <li>{@code Error} 는 상태 전이 없이 호출자에게 그대로 전파된다.</li>
 * </ul>
 * 별도 스레드 생성 없이 호출 스레드에서 동기적으로 수행하려면 {@link #run()}을 사용한다.
 *
 * @param <T>	연산 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractThreadedExecution<T> extends AbstractAsyncExecution<T> {
	private volatile boolean m_isDaemonThread = true;
	
	/**
	 * 연산이 시작되어 새로운 쓰레드를 생성하고, 작업을 호출하기 전에
	 * 초기화가 필요한 경우 서브클래스에서 구현한다.
	 * <p>
	 * 본 메소드는 {@link #notifyStarting()}이 호출된 상태에서 호출되고,
	 * 예외가 발생하지 않고 정상 반환되면 {@link #notifyStarted()} 메소드가 호출되어
	 * {@code STARTING → RUNNING}으로 전이한다.
	 * 예외가 발생하는 경우의 전이는 다음과 같다.
	 * <ul>
	 *   <li>{@code InterruptedException} 또는 {@code CancellationException} → {@code CANCELLED}.</li>
	 *   <li>그 외 {@code Exception} → {@code FAILED} (원인은 {@link Throwables#unwrapThrowable}).</li>
	 * </ul>
	 * <p>
	 * 별도의 초기화 작업이 필요하지 않으면 구현하지 않아도 된다.
	 *
	 * @throws Exception	초기화 중 오류가 발생한 경우.
	 */
	 protected void initializeThread() throws Exception { }
	
	/**
	 * 서브클래스가 구현해야 할 실제 작업 본문.
	 * <p>
	 * 호출 시점은 {@link #initializeThread}가 호출되고 난 후이며, {@code RUNNING} 상태에서 호출된다.
	 * <p>
	 * 진입점에 따라 {@link #start()}로 시작하면 별도 스레드(또는 외부 주입된
	 * {@link Executor})에서, {@link #run()}으로 시작하면 호출 스레드에서 호출된다.
	 * 메소드가 예외 없이 반환되면 작업이 완료된 것으로 간주된다.
	 * <p>
	 * 메소드 수행 중 발생하는 예외에 따라 작업의 종료 상태가 결정된다.
	 * <dl>
	 * 	<dt>InterruptedException 또는 CancellationException</dt>
	 * 	<dd>작업이 중단된 것으로 간주된다.
	 * 		{@link Execution#isCancelled()} 가 {@code true} 가 됨.</dd>
	 * 	<dt>그 외 Exception</dt>
	 * 	<dd>작업이 수행 중 오류로 실패한 것으로 간주된다.
	 * 		{@link Execution#isFailed()} 가 {@code true} 가 됨.</dd>
	 * </dl>
	 *
	 * @return	수행 작업의 결과
	 * @throws InterruptedException		작업 수행 중 수행 스레드가 인터럽트된 경우.
	 * @throws CancellationException	작업 수행이 취소된 경우.
	 * @throws Exception				작업 수행 중 오류 발생으로 작업이 실패한 경우.
	 */
	protected abstract T executeWork() throws InterruptedException, CancellationException, Exception;
	
	/**
	 * 호출 스레드에서 작업을 동기적으로 수행한다.
	 * <p>
	 * {@link #start()}와 달리 새 스레드나 {@link Executor}를 사용하지 않고 호출 스레드에서 직접
	 * {@link #executeWork()}를 실행한다. 호출 시점에는 {@code NOT_STARTED} 상태여야 하며,
	 * 메소드 진입 시 {@code STARTING → RUNNING}으로 전이한 후 작업을 수행한다.
	 * <p>
	 * 작업 결과에 따른 종료 상태는 다음과 같다.
	 * <ul>
	 *   <li>{@code executeWork()} 정상 반환 → {@code COMPLETED}, 결과 값 반환.</li>
	 *   <li>{@code InterruptedException} 또는 {@code CancellationException} 발생 → {@code CANCELLED},
	 *       발생한 예외를 그대로 throw.</li>
	 *   <li>작업 도중 외부에서 cancel 호출 → {@code CANCELLED}, {@link CancellationException} throw.</li>
	 *   <li>그 외 예외 → {@code FAILED}, 원인을 감싼 {@link ExecutionException} throw
	 *       (이미 {@code ExecutionException}이면 그대로 throw).</li>
	 * </ul>
	 *
	 * @return	작업 수행 결과.
	 * @throws CancellationException	작업이 취소된 경우.
	 * @throws InterruptedException	    작업 수행 중 호출 스레드가 인터럽트된 경우.
	 * @throws ExecutionException	    작업 수행 중 예외가 발생하여 실패한 경우.
	 * @throws IllegalStateException	이미 시작된 상태에서 호출된 경우({@link #start()} 또는 {@link #run()}이
	 *                                  이미 호출됨).
	 */
	public final T run() throws CancellationException, InterruptedException, ExecutionException {
		if ( !notifyStarting() ) {
			throw new IllegalStateException("already started: " + this);
		}
		
		try {
			initializeThread();
		}
		catch ( CancellationException | InterruptedException e ) {
			notifyCancelled();
			throw e;
		}
		catch ( Exception e ) {
			Throwables.throwIfInstanceOf(e, Error.class);
			Throwable cause = Throwables.unwrapThrowable(e);
			notifyFailed(cause);
			Throwables.throwIfInstanceOf(e, ExecutionException.class);

			throw new ExecutionException(cause);
		}
		
		if ( !notifyStarted() ) {
			return get();
		}

		T result;
		try {
			result = executeWork();
		}
		catch ( InterruptedException | CancellationException e ) {
			// executeWork()에서 InterruptedException/CancellationException이 발생한 경우는
			// 작업이 취소된 것으로 간주한다.
			notifyCancelled();
			throw e;
		}
		catch ( Throwable e ) {
			Throwables.throwIfInstanceOf(e, Error.class);
			Throwable cause = Throwables.unwrapThrowable(e);
			notifyFailed(cause);
			Throwables.throwIfInstanceOf(e, ExecutionException.class);

			throw new ExecutionException(cause);
		}

		// 정상 종료 경로
		if ( notifyCompleted(result) ) {
			return result;
		}
		// executeWork()가 정상 반환되었으나 notifyCompleted()가 false를 반환한 경우는
		// 외부에서 cancel을 호출하거나, notifyFailed()가 호출된 경우이기 때문에 체크한다.
		if ( notifyCancelled() ) {
			throw new CancellationException("cancelled externally: " + this);
		}
		// 남은 상태는 FAILED뿐이므로 원인 예외를 얻기 위한 poll().get() 호출.
		try {
			return poll().get();
		}
		catch ( TimeoutException e ) {
			throw new IllegalStateException("unexpected timeout while waiting for completion: " + this, e);
		}
	}
	
	/**
	 * 별도 스레드(또는 외부 주입된 {@link Executor})에서 작업 수행을 시작한다.
	 * <p>
	 * {@link AbstractAsyncExecution#getExecutor()}가 설정되어 있으면 해당 {@code Executor}에 작업을 제출하고,
	 * 그렇지 않으면 새 {@link Thread}를 생성해 수행한다 (daemon 여부는
	 * {@link #setDaemonThread(boolean)}으로 제어).
	 * {@code NOT_STARTED → STARTING}으로 전이한 후 작업 제출/스레드 시작이 완료되면 즉시 반환한다.
	 * <p>
	 * 작업 제출 자체가 실패하는 경우({@link RejectedExecutionException} 등) 본 객체는
	 * {@code FAILED}로 전이된 후 원인 예외가 호출자에게 그대로 전달된다.
	 *
	 * @throws IllegalStateException	이미 시작된 상태에서 호출된 경우({@link #start()} 또는 {@link #run()}이
	 *                                  이미 호출됨).
	 * @throws RejectedExecutionException	{@link Executor}가 작업 제출을 거부한 경우.
	 */
	@Override
	public final void start() {
		if ( !notifyStarting() ) {
			throw new IllegalStateException("already started: " + this);
		}

		try {
			Executor executor = getExecutor();
			if ( executor != null ) {
				executor.execute(this::runInThread);
			}
			else {
				Thread thread = new Thread(this::runInThread, getClass().getSimpleName());
				thread.setDaemon(m_isDaemonThread);
				thread.start();
			}
		}
		catch ( Throwable e ) {
			Throwables.throwIfInstanceOf(e, Error.class);
			// Executor 거부 또는 스레드 생성 실패 시 STARTING 상태에 머무르지 않도록 FAILED로 전이.
			notifyFailed(Throwables.unwrapThrowable(e));
			Throwables.sneakyThrow(e);   // 원인 예외를 호출자에게 그대로 전파
		}
	}

	private void runInThread() {
		try {
			initializeThread();
		}
		catch ( CancellationException | InterruptedException e ) {
			notifyCancelled();
			return;
		}
		catch ( Throwable e ) {
			Throwables.throwIfInstanceOf(e, Error.class);
			Throwable cause = Throwables.unwrapThrowable(e);
			notifyFailed(cause);
			return;
		}
		
		if ( !notifyStarted() ) {
			// 비동기 컨텍스트(Executor/Thread)이므로 throw 대신 로깅 후 종료한다.
			// 시작 실패는 주로 외부에서 이미 cancel된 경우 발생.
			getLogger().debug("failed to start work: {}", this);
			return;
		}

		T result;
		try {
			result = executeWork();
		}
		catch ( InterruptedException | CancellationException e ) {
			notifyCancelled();
			return;
		}
		catch ( Throwable e ) {
			Throwables.throwIfInstanceOf(e, Error.class);
			Throwable cause = Throwables.unwrapThrowable(e);
			notifyFailed(cause);
			return;
		}

		if ( notifyCompleted(result) ) {
			return;
		}
		if ( notifyCancelled() ) {
			return;
		}
		// executeWork()가 정상 반환되었으나 두 전이 모두 실패 — 외부에서 이미 종료(주로 notifyFailed) 처리된 경우.
		getLogger().debug("execution finished by external party: {}", this);
	}
	
	public boolean isDaemonThread() {
		return m_isDaemonThread;
	}
	public void setDaemonThread(boolean isDaemon) {
		m_isDaemonThread = isDaemon;
	}
}
