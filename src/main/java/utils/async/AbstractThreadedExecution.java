package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import utils.Preconditions;
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
	private String m_threadNamePrefix = getClass().getSimpleName();
	private volatile boolean m_isDaemonThread = true;
	
	/**
	 * 연산이 시작되어 새로운 쓰레드를 생성하고, 작업을 호출하기 전에
	 * 초기화가 필요한 경우 서브클래스에서 구현한다.
	 * <p>
	 * 본 메소드는 {@link #notifyStarting()}이 호출된 상태에서 호출되고,
	 * 새로 생성된 스레드(또는 외부 주입된 {@link Executor})에서 실행된다.
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
	 * 작업 수행이 끝난 뒤 정리가 필요한 경우 서브클래스에서 구현한다.
	 * <p>
	 * {@link #executeWork()}가 호출된 경우({@code RUNNING}까지 전이한 경우)에만, 종료 사유(완료·실패·취소)와
	 * 무관하게 1회 호출된다. {@link #start()}·{@link #run()} 두 경로 모두에서 작업을 수행한 스레드가 호출하며,
	 * 호출 시점은 종료 상태로 전이한 <b>이후</b>이다. 따라서 {@link #waitForFinished()} 등으로 종료를 기다리던 쪽은
	 * 본 메소드가 끝나기 전에 깨어날 수 있다.
	 * <p>
	 * {@link #initializeThread()}가 실패했거나, 초기화 도중 외부에서 종료되어 {@code RUNNING}으로 전이하지 못한
	 * 경우에는 호출되지 않는다(후자는 {@link #abortAfterInitialize()}가 대신 호출된다).
	 * 발생한 예외는 로그만 남기고 무시하므로 연산의 결과에 영향을 주지 않는다.
	 */
	protected void finalizeThread() { }

	/**
	 * {@link #executeWork()}가 정상 반환한 결과가 최종 결과로 채택되지 못했을 때 호출된다.
	 * <p>
	 * {@link #executeWork()}가 결과를 반환했지만, 그 사이 외부에서 이미 종료 상태({@code FAILED} 등)로 확정되어
	 * {@code COMPLETED}로의 전이({@link #notifyCompleted(Object)})가 실패한 경우에 1회 호출된다.
	 * 결과가 연결 객체처럼 자원을 소유하는 경우, 그 자원을 정리하기 위한 hook이다. 완료 전이의 실패 여부로
	 * 판단하므로, 결과를 반환한 직후부터 완료 전이 사이의 어느 시점에 외부 종료가 확정되더라도 빠짐없이 호출된다.
	 * <p>
	 * 작업을 수행한 스레드에서 {@link #finalizeThread()}보다 먼저 호출되며, 발생한 예외는 경고 로그만 남기고
	 * 무시된다. 기본 구현은 아무 작업도 하지 않는다.
	 *
	 * @param result	채택되지 못한 결과.
	 */
	protected void onResultDiscarded(T result) { }

	/**
	 * {@link #initializeThread()}는 성공했지만 그 사이 외부에서 이미 종료(실패·취소)되어
	 * {@code RUNNING}으로 전이하지 못한 경우에 호출된다. 이때 {@link #executeWork()}는 호출되지 않는다.
	 * <p>
	 * {@link #initializeThread()}에서 확보한 자원을 정리해야 하는 서브클래스가 구현한다.
	 * {@link #start()}·{@link #run()} 두 경로 모두에서 초기화를 수행한 스레드가 1회 호출하며,
	 * 발생한 예외는 로그만 남기고 무시한다.
	 */
	protected void abortAfterInitialize() { }
	
	/**
	 * 서브클래스가 구현해야 할 실제 작업 본문.
	 * <p>
	 * 호출 시점은 {@link #initializeThread}가 호출되고 난 후이며, {@code RUNNING} 상태에서 호출된다.
	 * <p>
	 * 진입점에 따라 {@link #start()}로 시작하면 별도 스레드(또는 외부 주입된
	 * {@link Executor})에서, {@link #run()}으로 시작하면 호출 스레드에서 호출된다.
	 * 메소드가 예외 없이 반환되면 작업이 완료된 것으로 간주되어, 호출자가
	 * {@link #notifyCompleted(Object)}를 호출하여 {@code COMPLETED}로 전이한다.
	 * 이때 반환된 결과 값이 {@code notifyCompleted()}의 인자로 전달된다.
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
	 *   <li>작업 도중(초기화 단계인 {@link #initializeThread()} 포함) 외부에서 이미 종료 상태로 확정된 경우
	 *       ({@link #notifyFailed(Throwable)} 등) →
	 *       {@code executeWork()}의 반환·예외와 무관하게 확정된 상태가 유지되고, 그 결과를 따른다.
	 *       {@code FAILED}이면 확정된 실패 원인을 담은 {@link ExecutionException}을 throw한다.
	 *       {@code InterruptedException}으로 끝난 경우에는 호출 스레드의 인터럽트 상태를 복원한다.</li>
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
		if ( !beginExecution() ) {
			throw new IllegalStateException("already started: " + this);
		}
		
		try {
			// STARTING 상태에서 호출
			initializeThread();
			
			// 시작 준비(initializeThread) 도중 외부에서 취소/실패가 끼어들어 RUNNING으로 못 올라간 경우,
			// 작업을 수행하지 않고 이미 확정된 종료 결과를 반환한다.
			if ( !notifyStarted() ) {
				abortAfterInitializeQuietly();
				return get();
			}
		}
		catch ( CancellationException | InterruptedException e ) {
			// 시작 준비 중인 이 스레드가 STARTING을 쥐고 있으므로 기다리지 않고 취소로 전이한다.
			if ( notifyCancelledWhileStarting() ) {
				throw e;
			}
			// 시작 준비 도중 외부에서 이미 다른 종료 상태(주로 notifyFailed)로 확정된 경우는 그 결과를 따른다.
			if ( e instanceof InterruptedException ) {
				Thread.currentThread().interrupt();
			}
			return getFinishedResult();
		}
		catch ( Exception e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			if ( notifyFailed(cause) ) {
				Throwables.throwIfInstanceOf(e, ExecutionException.class);
				throw new ExecutionException(cause);
			}
			// 시작 준비 도중 외부에서 이미 종료 상태로 확정된 경우는 initializeThread()의 예외 대신 그 결과를 따른다.
			return getFinishedResult();
		}

		try {
			return runAction();
		}
		finally {
			finalizeThreadQuietly();
		}
	}
	
	/** start/run의 진입만 원자적으로 제한한다. 이벤트 통지의 멱등성은 유지한다. */
	private boolean beginExecution() {
		m_aopGuard.lock();
		try {
			return isNotStarted() && notifyStarting();
		}
		finally {
			m_aopGuard.unlock();
		}
	}

	public String getThreadNamePrefix() {
		return m_threadNamePrefix;
	}
	
	public void setThreadNamePrefix(String prefix) {
		Preconditions.checkNotNullArgument(prefix, "threadNamePrefix must not be null");
		
		m_threadNamePrefix = prefix;
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
		// STARTING 상태로 전이
		if ( !beginExecution() ) {
			throw new IllegalStateException("already started: " + this);
		}

		try {
			// STARTED 상태로의 전이는 runInThread()에서 notifyStarted() 호출 시 수행됨.
			Executor executor = getExecutor();
			if ( executor != null ) {
				executor.execute(this::runInThread);
			}
			else {
				String threadName = m_threadNamePrefix +"@"+Integer.toHexString(System.identityHashCode(this));
				Thread thread = new Thread(this::runInThread, threadName);
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
	
	public boolean isDaemonThread() {
		return m_isDaemonThread;
	}
	public void setDaemonThread(boolean isDaemon) {
		m_isDaemonThread = isDaemon;
	}
	
	@Override
	public String toString() {
		return m_threadNamePrefix +"@"+Integer.toHexString(System.identityHashCode(this));
	}

	private void runInThread() {
		try {
			// STARTING 상태에서 호출
			// 작업에 필요한 초기화 작업을 수행한다.
			initializeThread();
		}
		catch ( CancellationException | InterruptedException e ) {
			// 시작 준비 중인 이 스레드가 STARTING을 쥐고 있으므로 기다리지 않고 취소로 전이한다.
			notifyCancelledWhileStarting();
			return;
		}
		catch ( Throwable e ) {
			Throwables.throwIfInstanceOf(e, Error.class);
			Throwable cause = Throwables.unwrapThrowable(e);
			notifyFailed(cause);
			return;
		}
		
		// STARTED 상태로 전이
		if ( !notifyStarted() ) {
			// 비동기 컨텍스트(Executor/Thread)이므로 throw 대신 로깅 후 종료한다.
			// 시작 실패는 주로 외부에서 이미 cancel된 경우 발생.
			getLogger().debug("failed to start work: {}", this);
			abortAfterInitializeQuietly();
			return;
		}

		try {
			runActionInThread();
		}
		finally {
			finalizeThreadQuietly();
		}
	}

	private void runActionInThread() {
		T result;
		try {
			// executeWork() 함수는 RUNNING 상태에서 호출됨
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
		// 외부에서 이미 종료 상태로 확정되어 결과가 채택되지 못한 경우.
		discardResultQuietly(result);
		if ( notifyCancelled() ) {
			return;
		}
		// executeWork()가 정상 반환되었으나 두 전이 모두 실패 — 외부에서 이미 종료(주로 notifyFailed) 처리된 경우.
		getLogger().debug("execution finished by external party: {}", this);
	}

	private void discardResultQuietly(T result) {
		try {
			onResultDiscarded(result);
		}
		catch ( Exception e ) {
			getLogger().warn("failed to discard result: {}, result={}", this, result, e);
		}
	}

	private void finalizeThreadQuietly() {
		try {
			finalizeThread();
		}
		catch ( Exception e ) {
			getLogger().warn("failed to finalize thread: {}", this, e);
		}
	}

	private void abortAfterInitializeQuietly() {
		try {
			abortAfterInitialize();
		}
		catch ( Exception e ) {
			getLogger().warn("failed to clean up after aborted start: {}", this, e);
		}
	}

	private T runAction() throws CancellationException, InterruptedException, ExecutionException {
		T result;
		try {
			result = executeWork();
		}
		catch ( InterruptedException | CancellationException e ) {
			// executeWork()에서 InterruptedException/CancellationException이 발생한 경우는
			// 작업이 취소된 것으로 간주한다.
			if ( notifyCancelled() ) {
				throw e;
			}
			// 외부에서 이미 다른 종료 상태(주로 notifyFailed)로 확정된 경우는 그 결과를 따른다.
			if ( e instanceof InterruptedException ) {
				Thread.currentThread().interrupt();
			}
			return getFinishedResult();
		}
		catch ( Throwable e ) {
			Throwables.throwIfInstanceOf(e, Error.class);
			Throwable cause = Throwables.unwrapThrowable(e);
			if ( notifyFailed(cause) ) {
				Throwables.throwIfInstanceOf(e, ExecutionException.class);
				throw new ExecutionException(cause);
			}
			// 외부에서 이미 다른 종료 상태로 확정된 경우는 executeWork()의 예외 대신 그 결과를 따른다.
			return getFinishedResult();
		}

		// 정상 종료 경로
		if ( notifyCompleted(result) ) {
			return result;
		}
		// 외부에서 이미 종료 상태로 확정되어 결과가 채택되지 못한 경우.
		discardResultQuietly(result);
		// executeWork()가 정상 반환되었으나 notifyCompleted()가 false를 반환한 경우는
		// 외부에서 cancel을 호출하거나, notifyFailed()가 호출된 경우이기 때문에 체크한다.
		if ( notifyCancelled() ) {
			throw new CancellationException("cancelled externally: " + this);
		}
		// 남은 상태는 FAILED뿐이므로 원인 예외를 얻는다.
		return getFinishedResult();
	}

	/**
	 * 외부에서 이미 종료 상태로 확정된 실행의 결과를 {@link #run()}의 반환 형태로 돌려준다.
	 * <p>
	 * {@code COMPLETED}이면 결과 값을 반환하고, {@code FAILED}이면 원인을 담은 {@link ExecutionException}을,
	 * {@code CANCELLED}이면 {@link CancellationException}을 던진다.
	 */
	private T getFinishedResult() throws CancellationException, ExecutionException {
		try {
			return poll().get();
		}
		catch ( TimeoutException e ) {
			throw new IllegalStateException("unexpected timeout while waiting for completion: " + this, e);
		}
	}
}
