package utils.async;

import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import utils.Preconditions;
import utils.RuntimeExecutionException;
import utils.RuntimeInterruptedException;
import utils.async.AsyncResult.Cancelled;
import utils.async.AsyncResult.Completed;
import utils.async.AsyncResult.Failed;
import utils.async.AsyncResult.Running;
import utils.async.Executions.MapChainExecution;
import utils.func.Result;


/**
 * 비동기 연산 수행을 표현하는 인터페이스.
 * <p>
 * 표준 {@link Future}을 확장하여 명시적인 라이프사이클 상태({@link AsyncState}),
 * 시작/종료 콜백, 결과 polling, monadic 변환/chain을 제공한다.
 *
 * <h2>라이프사이클</h2>
 * 작업 상태는 다음과 같이 전이된다.
 * <pre>
 *   NOT_STARTED ──► STARTING ──► RUNNING ──┬─► COMPLETED
 *        │              │                  │
 *        │              │                  ├─► FAILED
 *        │              │                  │
 *        │              │                  └─► CANCELLING ──► CANCELLED
 *        │              │                                          ▲
 *        │              └─► FAILED                                 │
 *        │                                                         │
 *        └─────────────────────────────────────────────────────────┘
 *          (시작 전 cancel: NOT_STARTED → CANCELLED 직접 전이)
 * </pre>
 * 가능한 전이는 다음과 같다:
 * <ul>
 *   <li>{@code NOT_STARTED → STARTING} (정상 시작) 또는 {@code NOT_STARTED → CANCELLED} (시작 전 cancel).
 *   <li>{@code STARTING → RUNNING} (시작 완료) 또는 {@code STARTING → FAILED} (시작 중 오류).
 *   <li>{@code RUNNING → COMPLETED} / {@code FAILED} / {@code CANCELLING}.
 *   <li>{@code CANCELLING → CANCELLED}.
 * </ul>
 * 종료 상태는 {@code COMPLETED}/{@code FAILED}/{@code CANCELLED} 셋이며,
 * 한 번 종료된 작업은 다시 시작될 수 없다(단발성 객체).
 * {@code STARTING}/{@code RUNNING} 상태에서 cancel된 경우 {@code CANCELLING}을
 * 거쳐 {@code CANCELLED}로 전이되지만, 시작도 전에 cancel된 경우에는
 * {@code NOT_STARTED → CANCELLED}로 직접 전이된다 (자세한 내용은 {@link #isStarted()} 참조).
 *
 * <h2>주요 API 그룹</h2>
 * <ul>
 *   <li><b>상태 조회</b>: {@link #getState()}, {@link #isStarted()}, {@link #isRunning()},
 *       {@link #isCompleted()}, {@link #isFailed()}, {@link #isCancelled()}, {@link #isDone()}.
 *   <li><b>결과 대기</b>: {@link #get()}, {@link #get(long, TimeUnit)}, {@link #get(Date)},
 *       {@link #getUnchecked()} — 표준 {@link Future} 호환 API.
 *   <li><b>결과 polling/대기</b>: {@link #poll()},
 *       {@link #waitForFinished()}, {@link #waitForFinished(Date)}, {@link #waitForFinished(long, TimeUnit)},
 *       {@link #waitForStarted()}, {@link #waitForStarted(Date)}, {@link #waitForStarted(long, TimeUnit)} —
 *       체크드 예외 대신 {@link AsyncResult} 객체로 결과를 반환.
 *   <li><b>콜백 등록</b>: {@link #whenStarted(Runnable)}, {@link #whenStartedAsync(Runnable)},
 *       {@link #whenFinished(Consumer)}, {@link #whenFinishedAsync(Consumer)}, 그리고 종료 케이스별
 *       편의 메소드 {@link #whenCompleted(Consumer)}, {@link #whenFailed(Consumer)}, {@link #whenCancelled(Runnable)}.
 *   <li><b>합성/chain</b>: {@link #map(Function)}(성공 시 값 변환),
 *       {@link EventDrivenExecution#flatMap(Function)}(모든 종료 케이스에서 다음 비동기 작업 결정,
 *       {@link EventDrivenExecution} 기반에서만 제공).
 *   <li><b>중단/타임아웃</b>: {@link #cancel(boolean)}, {@link #setTimeout(long, TimeUnit)}.
 * </ul>
 *
 * <h2>표준 {@link Future}와의 차이</h2>
 * <ul>
 *   <li>{@link #cancel(boolean)}의 {@code mayInterruptIfRunning} 의미가 다름 (자세한 내용은 해당 메소드 Javadoc 참조).
 *   <li>대기 시각 비교는 시스템 벽시계 기준이며 monotonic clock이 아님 — NTP 보정 등에 영향받음.
 *   <li>결과를 예외 없이 받기 위한 {@link AsyncResult} 기반 API({@link #poll()}, {@code waitForFinished*})를 제공.
 *   <li>checked 예외를 unchecked 예외({@link RuntimeInterruptedException}, {@link RuntimeExecutionException})로
 *       변환하여 결과를 조회하는 {@link #getUnchecked()}를 제공.
 *   <li>작업에 제한 시간을 외부에서 설정해 자동 cancel을 발생시키는 {@link #setTimeout(long, TimeUnit)}을 제공.
 * </ul>
 *
 * <h2>{@link AsyncResult}와 {@link Result}의 사용 구분</h2>
 * 본 인터페이스는 두 개의 결과 wrapper를 사용한다.
 * <ul>
 *   <li>{@link AsyncResult} — polling/대기 API가 반환. 종료뿐 아니라
 *       {@code Running}(아직 수행 중) 상태도 표현 가능.
 *   <li>{@link Result} — finish 콜백 및 {@link EventDrivenExecution#flatMap(Function)}의 인자.
 *       종료된 결과(성공/실패/취소)만 표현.
 * </ul>
 *
 * <h2>스레드 안전성</h2>
 * 모든 상태 조회 및 콜백 등록 메소드는 멀티스레드 환경에서 안전하게 호출 가능하다.
 * 콜백 핸들러 본문의 실행 컨텍스트(동기/비동기, lock 보유 여부)는 각 메소드 Javadoc을 참조한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface Execution<T> extends Future<T> {
	/**
	 * 연산 수행을 중단시킨다.
	 * <p>
	 * 본 메소드는 연산이 완전히 중단되기 전에 반환될 수 있기 때문에, 메소드가 반환되어도
	 * 바로 종료되지 않을 수 있다.
	 * 메소드 호출시 대상 작업의 상태에 따라 중단 요청이 무시될 수 있고, 이 경우
	 * 반환 값이 {@code false}가 된다.
	 * 반환 값이 {@code true}인 경우는 중단 요청이 접수되어 중단 작업이 시작된 것을 의미한다.
	 * 물론, 이때도 중단이 반드시 성공하는 것을 의미하지 않는다.
	 * <p>
	 * 작업 중단을 확인하기 위해서는 {@link #waitForFinished()}이나 {@link #waitForFinished(long, TimeUnit)}
	 * 메소드를 사용하여 최종적으로 확인할 수 있다.
	 * <p>
	 * 상태별 동작:
	 * <ul>
	 * 	<li>아직 시작되지 않은 경우: {@code mayInterruptIfRunning} 값과 무관하게 중단되며 {@code true} 반환.
	 * 	<li>이미 종료된 경우(성공/실패): {@code false} 반환.
	 * 	<li>이미 중단된 경우: {@code true} 반환.
	 * 	<li>수행 중인 경우: {@code mayInterruptIfRunning}이 {@code false}이거나 작업이
	 * 		중단을 지원하지 않으면 {@code false} 반환. 그렇지 않으면 중단 절차가 시작된다.
	 * </ul>
	 * <p>
	 * 주의: 본 메소드의 {@code mayInterruptIfRunning} 의미는 표준 {@link Future#cancel(boolean)}와
	 * 다르다. 표준에서는 "이미 수행 중인 task의 스레드를 interrupt할지 여부"이지만,
	 * 본 인터페이스에서는 "이미 수행 중인 작업을 중단시킬지 여부"이다.
	 * {@code false}일 때 표준은 cancel 자체는 등록하고 {@code true}를 반환할 수 있지만,
	 * 본 인터페이스는 수행 중인 작업에 대한 중단 요청을 거부하고 {@code false}를 반환한다.
	 *
	 * @param mayInterruptIfRunning	 이미 수행 중인 작업도 중단시킬지 여부
	 * @return	중단 요청의 접수 여부.
	 */
	@Override
	public boolean cancel(boolean mayInterruptIfRunning);
	
	/**
	 * 연산 수행 상태를 반환한다.
	 * <p>
	 * 호출 즉시 현재 상태를 반환하며 블로킹되지 않는다.
	 * 멀티스레드 환경에서 안전하게 호출할 수 있으나, 반환된 값은 호출 시점의
	 * 스냅샷이므로 호출 직후에 다른 스레드에 의해 상태가 변경될 수 있다.
	 * <p>
	 * 가능한 상태 값은 {@link AsyncState}를 참조한다.
	 * {@link #isStarted()}, {@link #isRunning()}, {@link #isCompleted()},
	 * {@link #isFailed()}, {@link #isCancelled()}, {@link #isDone()} 등의 메소드는
	 * 본 메소드의 반환값을 기반으로 동작한다.
	 *
	 * @return	연산 수행 상태.
	 */
	public AsyncState getState();

	public default boolean isNotStarted() {
		return getState() == AsyncState.NOT_STARTED;
	}
	
	/**
	 * 작업이 {@link AsyncState#RUNNING} 상태에 한 번이라도 도달한 적이 있는지를 반환한다.
	 * <p>
	 * 연산이 시작되어 이미 종료된 경우(COMPLETED/FAILED/CANCELLED via RUNNING)에도
	 * {@code true}가 반환된다.
	 * <p>
	 * 다음 경우에는 {@code false}가 반환된다:
	 * <ul>
	 *   <li>{@link AsyncState#NOT_STARTED}: 아직 시작 절차가 시작되지 않음.
	 *   <li>{@link AsyncState#STARTING}: 시작 절차 진행 중이나 아직 RUNNING에 도달하지 못함.
	 *   <li>NOT_STARTED → CANCELLED 직접 전이로 종료됨 (시작 전 cancel).
	 *   <li>STARTING → FAILED 또는 STARTING → CANCELLING → CANCELLED로 종료됨 (시작 중 실패/취소).
	 * </ul>
	 * <p>
	 * 본 메소드는 "작업이 실제로 한 번이라도 수행되었는가?"를 의미한다.
	 * 현재 수행 중인지를 확인하려면 {@link #isRunning()}을, 정확한 상태가 필요하면
	 * {@link #getState()}를 사용한다.
	 *
	 * @return	작업이 RUNNING에 한 번이라도 도달했었으면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isStarted();
	
	/**
	 * 연산이 수행 중인 상태인지를 반환한다.
	 *
	 * @return	상태가 {@link AsyncState#RUNNING}이면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public default boolean isRunning() {
		return getState() == AsyncState.RUNNING;
	}

	/**
	 * 연산이 성공적으로 완료되어 종료된 상태인지를 반환한다.
	 *
	 * @return	상태가 {@link AsyncState#COMPLETED}이면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public default boolean isCompleted() {
		return getState() == AsyncState.COMPLETED;
	}

	/**
	 * 연산이 예외 발생으로 실패하여 종료된 상태인지를 반환한다.
	 *
	 * @return	상태가 {@link AsyncState#FAILED}이면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public default boolean isFailed() {
		return getState() == AsyncState.FAILED;
	}

	/**
	 * 연산이 취소되어 종료된 상태인지를 반환한다.
	 * <p>
	 * 수행 중인 작업이 취소된 경우뿐 아니라, 시작되기 전에 cancel되어 종료된 경우
	 * (NOT_STARTED → CANCELLED)에도 {@code true}가 반환된다.
	 *
	 * @return	상태가 {@link AsyncState#CANCELLED}이면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	@Override
	public default boolean isCancelled() {
		return getState() == AsyncState.CANCELLED;
	}
	
	/**
	 * 연산이 종료된 상태인지를 반환한다.
	 * <p>
	 * 종료 상태는 {@link AsyncState#COMPLETED}, {@link AsyncState#FAILED},
	 * {@link AsyncState#CANCELLED} 셋 중 하나이며, 본 메소드는
	 * {@link #isCompleted()}, {@link #isFailed()}, {@link #isCancelled()} 중
	 * 어느 하나라도 {@code true}이면 {@code true}를 반환한다.
	 * <p>
	 * {@link AsyncState#CANCELLING} 같은 전이 상태는 종료 상태가 아니므로
	 * {@code false}를 반환한다.
	 *
	 * @return	상태가 종료 상태(COMPLETED/FAILED/CANCELLED) 중 하나이면 {@code true},
	 * 			그렇지 않으면 {@code false}.
	 */
	@Override
	public default boolean isDone() {
		AsyncState state = getState();
		return state == AsyncState.COMPLETED || state == AsyncState.FAILED
			|| state == AsyncState.CANCELLED;
	}
	
	/**
	 * 연산 수행 결과를 반환한다.
	 * <p>
	 * 메소드 호출시 대상 연산이 아직 종료되지 않은 경우는 연산이 종료될 때까지 대기한다.
	 * 만일 연산 수행 대기 중에 대기 쓰레드가 중단된 경우에는 {@code InterruptedException} 예외가 발생된다.
	 * <p>
	 * 연산이 성공적으로 종료된 경우에는 수행 결과를 반환하지만, 그렇지 않은 경우는
	 * 종료 결과에 따라 해당하는 예외를 발생시킨다.
	 * <ul>
	 * 	<li> 예외 발생으로 종료된 경우는 {@code ExecutionException} 예외가 발생된다.
	 * 		이때 발생된 예외 객체는 {@code ExecutionException#getCause()}를 통해 얻을 수 있다.
	 * 	<li> 수행 중단 경우에는 {@code CancellationException} 예외가 발생된다.
	 * </ul>
	 * 
	 * @return 수행 결과
	 * @throws InterruptedException	수행 도중 쓰레드 인터럽트로 중단된 경우.
	 * @throws CancellationException	수행 도중 중단된 경우.
	 * @throws ExecutionException	수행 도중 예외가 발생된 경우.
	 */
	@Override
	public T get() throws InterruptedException, ExecutionException, CancellationException;
	
	/**
	 * 연산 수행 결과를 반환한다.
	 * <p>
	 * 메소드 호출시 대상 연산이 아직 종료되지 않은 경우는 연산이 종료될 때까지
	 * 주어진 대기 시각까지 대기한다.
	 * 만일 연산 수행 대기 중에 대기 쓰레드가 중단된 경우에는 {@code InterruptedException} 예외가 발생된다.
	 * <p>
	 * 연산이 성공적으로 종료된 경우에는 수행 결과를 반환하지만, 그렇지 않은 경우는
	 * 종료 결과에 따라 해당하는 예외를 발생시킨다.
	 * <ul>
	 * 	<li> 예외 발생으로 종료된 경우는 {@code ExecutionException} 예외가 발생된다.
	 * 		이때 발생된 예외 객체는 {@code ExecutionException#getCause()}를 통해 얻을 수 있다.
	 * 	<li> 수행 중단 경우에는 {@code CancellationException} 예외가 발생된다.
	 * </ul>
	 * <p>
	 * 대기 기간 계산은 시스템 벽시계({@link System#currentTimeMillis()}) 기준이므로,
	 * NTP 보정 등 시스템 시각 변경에 영향을 받을 수 있다.
	 * <p>
	 * {@code timeout}이 0 또는 음수이면 대기 없이 즉시 현재 상태를 확인한다 — 작업이
	 * 종료되지 않았으면 {@link TimeoutException}이 발생한다.
	 *
	 * @param timeout	대기 기간
	 * @param unit		대기 기간 단위
	 * @return 수행 결과
	 * @throws InterruptedException	수행 도중 쓰레드 인터럽트로 중단된 경우.
	 * @throws CancellationException	수행 도중 중단된 경우.
	 * @throws ExecutionException	수행 도중 예외가 발생된 경우.
	 * @throws TimeoutException	수행 대기가 제한 시각을 경과한 경우.
	 */
	@Override
	public default T get(long timeout, TimeUnit unit)
		throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
		return waitForFinished(timeout, unit).get();
	}
	
	/**
	 * 연산 수행 결과를 반환한다.
	 * <p>
	 * 메소드 호출시 대상 연산이 아직 종료되지 않은 경우는 연산이 종료될 때까지
	 * 주어진 대기 시각까지 대기한다.
	 * 만일 연산 수행 대기 중에 대기 쓰레드가 중단된 경우에는 {@code InterruptedException} 예외가 발생된다.
	 * <p>
	 * 연산이 성공적으로 종료된 경우에는 수행 결과를 반환하지만, 그렇지 않은 경우는
	 * 종료 결과에 따라 해당하는 예외를 발생시킨다.
	 * <ul>
	 * 	<li> 예외 발생으로 종료된 경우는 {@code ExecutionException} 예외가 발생된다.
	 * 		이때 발생된 예외 객체는 {@code ExecutionException#getCause()}를 통해 얻을 수 있다.
	 * 	<li> 수행 중단 경우에는 {@code CancellationException} 예외가 발생된다.
	 * </ul>
	 * <p>
	 * 시각 비교는 시스템 벽시계({@link System#currentTimeMillis()}) 기준이므로,
	 * NTP 보정 등 시스템 시각 변경에 영향을 받을 수 있다.
	 *
	 * @param due	대기 제한 시각
	 * @return 수행 결과
	 * @throws InterruptedException	수행 도중 쓰레드 인터럽트로 중단된 경우.
	 * @throws CancellationException	수행 도중 중단된 경우.
	 * @throws ExecutionException	수행 도중 예외가 발생된 경우.
	 * @throws TimeoutException	수행 대기가 제한 시각을 경과한 경우.
	 */
	public default T get(Date due) throws InterruptedException, ExecutionException,
											TimeoutException, CancellationException {
		return waitForFinished(due).get();
	}
	
	/**
	 * 연산 수행 결과를 반환한다.
	 * <p>
	 * {@link #get()}과 동일하게 동작하지만, checked 예외를 unchecked 예외로 변환하여 발생시킨다.
	 * 메소드 호출시 대상 연산이 아직 종료되지 않은 경우는 연산이 종료될 때까지 대기한다.
	 * 
	 * @return 수행 결과
	 * @throws RuntimeInterruptedException	수행 도중 쓰레드 인터럽트로 중단된 경우.
	 * 										인터럽트 플래그는 복원된다.
	 * @throws RuntimeExecutionException	수행 도중 예외가 발생된 경우.
	 * 										원인 예외는 {@code getCause()}를 통해 얻을 수 있다.
	 * @throws CancellationException		수행 도중 중단된 경우.
	 */
	public default T getUnchecked() {
		try {
			return get();
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new RuntimeInterruptedException(e);
		}
		catch ( ExecutionException e ) {
			throw new RuntimeExecutionException(e.getCause());
		}
		catch ( CancellationException e ) {
			throw e;
		}
	}
	
	/**
	 * 본 작업의 현재 수행 결과를 즉시 반환한다.
	 * <p>
	 * 대기 없이 현재 상태를 조회하며, 작업이 아직 수행 중인 경우에는 {@link Running}이 반환된다.
	 *
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Completed}가 반환되고,
	 * 			오류가 발생되어 종료된 경우는 {@link Failed}가 반환되고,
	 * 			작업이 취소되어 종료된 경우는 {@link Cancelled}가 반환되며,
	 * 			작업이 아직 수행 중인 경우에는 {@link Running}이 반환된다.
	 */
	public AsyncResult<T> poll();
	
	/**
	 * 본 작업이 종료될 때까지 주어진 제한 시간 동안 기다려 그 결과를 반환한다.
	 * <p>
	 * 시각 비교는 시스템 벽시계({@link System#currentTimeMillis()}) 기준이므로,
	 * NTP 보정 등 시스템 시각 변경에 영향을 받을 수 있다.
	 * <p>
	 * 주어진 {@code due}가 이미 과거 시각인 경우 대기 없이 즉시 현재 상태를 확인하여
	 * 반환한다 — 작업이 아직 종료되지 않았으면 {@link Running}을 반환한다.
	 *
	 * @param due	대기 제한 시각.
	 * 				주어진 시각을 경과하면 {@link Running}을 반환한다.
	 * @return	작업 수행 결과.
	 * 			성공적으로 종료된 경우는 {@link Completed}가 반환되고,
	 * 			오류가 발생되어 종료된 경우는 {@link Failed}가 반환되고,
	 * 			작업이 취소되어 종료된 경우는 {@link Cancelled}가 반환되며,
	 *			시간제한으로 반환되는 경우에는 {@link Running}가 반환된다.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public AsyncResult<T> waitForFinished(Date due) throws InterruptedException;

	/**
	 * 본 작업이 종료될 때까지 주어진 기간 동안 기다려 그 결과를 반환한다.
	 * <p>
	 * 대기 기간 계산은 시스템 벽시계({@link System#currentTimeMillis()}) 기준이므로,
	 * NTP 보정 등 시스템 시각 변경에 영향을 받을 수 있다.
	 * <p>
	 * {@code timeout}이 0 또는 음수이면 대기 없이 즉시 현재 상태를 확인하여 반환한다
	 * (작업이 종료되지 않았으면 {@link Running}을 반환).
	 *
	 * @param timeout	대기 기간
	 * @param unit		대기 기간 단위
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Completed}가 반환되고,
	 * 			오류가 발생되어 종료된 경우는 {@link Failed}가 반환되고,
	 * 			작업이 취소되어 종료된 경우는 {@link Cancelled}가 반환되며,
	 *			시간제한으로 반환되는 경우에는 {@link Running}가 반환된다.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public default AsyncResult<T> waitForFinished(long timeout, TimeUnit unit) throws InterruptedException {
		Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
		return waitForFinished(due);
	}

	/**
	 * 작업이 종료될 때까지 무제한 대기하여 그 결과를 반환한다.
	 * <p>
	 * 호출 시점에 작업이 이미 종료된 상태이면 즉시 종료 결과를 반환한다. 그렇지 않으면
	 * {@link #isDone()}이 {@code true}가 될 때까지 대기한다.
	 * <p>
	 * 작업이 영원히 종료되지 않는 경우 본 메소드는 영원히 대기할 수 있으므로,
	 * 제한 시간이 필요하면 {@link #waitForFinished(long, TimeUnit)} 또는
	 * {@link #waitForFinished(Date)}를 사용한다.
	 *
	 * @return	종료 결과.
	 * 			성공적으로 종료된 경우는 {@link Completed}가 반환되고,
	 * 			오류가 발생되어 종료된 경우는 {@link Failed}가 반환되고,
	 * 			작업이 취소되어 종료된 경우는 {@link Cancelled}가 반환된다.
	 * @throws InterruptedException	작업 종료 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public AsyncResult<T> waitForFinished() throws InterruptedException;

	/**
	 * 비동기 작업이 {@link AsyncState#RUNNING}에 도달할 때까지 무제한 대기한다.
	 * <p>
	 * 본 메소드는 {@link #isStarted()}가 {@code true}가 될 때까지 엄격하게 대기한다.
	 * 호출 시점에 이미 {@link #isStarted()}가 {@code true}이면 즉시 반환한다.
	 * <p>
	 * <b>주의 (deadlock 위험)</b>: 작업이 RUNNING에 도달하지 못한 채로 종료된 경우
	 * (NOT_STARTED → CANCELLED 직접 전이, STARTING → FAILED 등) {@link #isStarted()}는
	 * 영원히 {@code false}이므로 본 메소드는 <b>영원히 대기</b>한다. 작업이 시작 없이
	 * 종료될 가능성이 있다면 반드시 {@link #waitForStarted(long, TimeUnit)} 또는
	 * {@link #waitForStarted(Date)}를 사용한다.
	 *
	 * @throws InterruptedException	작업 시작 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public void waitForStarted() throws InterruptedException;

	/**
	 * 비동기 작업이 {@link AsyncState#RUNNING}에 도달할 때까지 제한된 시간 동안만 대기한다.
	 * <p>
	 * 대기 기간 계산은 시스템 벽시계({@link System#currentTimeMillis()}) 기준이므로,
	 * NTP 보정 등 시스템 시각 변경에 영향을 받을 수 있다.
	 * <p>
	 * {@code timeout}이 0 또는 음수이면 대기 없이 즉시 현재 {@link #isStarted()} 값을
	 * 반환한다.
	 *
	 * @param timeout	대기시간
	 * @param unit		대기시간 단위
	 * @return	제한시간 내에 {@link #isStarted()}가 {@code true}가 된 경우는 {@code true},
	 * 			그렇지 않은 경우는 {@code false}. 작업이 시작 없이 종료된 경우에는
	 * 			제한시간이 경과할 때까지 대기한 후 {@code false}를 반환한다.
	 * @throws InterruptedException	작업 시작 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public default boolean waitForStarted(long timeout, TimeUnit unit)
		throws InterruptedException {
		Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
		return waitForStarted(due);
	}

	/**
	 * 비동기 작업이 {@link AsyncState#RUNNING}에 도달할 때까지 주어진 제한 시각까지만 대기한다.
	 * <p>
	 * 시각 비교는 시스템 벽시계({@link System#currentTimeMillis()}) 기준이므로,
	 * NTP 보정 등 시스템 시각 변경에 영향을 받을 수 있다.
	 * <p>
	 * 주어진 {@code due}가 이미 과거 시각인 경우 대기 없이 즉시 현재 {@link #isStarted()}
	 * 값을 반환한다.
	 * <p>
	 * 본 메소드는 {@link #isStarted()}가 {@code true}가 되는 시점에만 즉시 반환한다.
	 * 작업이 시작 없이 종료된 경우(NOT_STARTED → CANCELLED, STARTING → FAILED 등)에는
	 * 제한 시각이 경과할 때까지 대기한 후 {@code false}를 반환하므로, 시작 가능성이
	 * 불확실한 작업에는 합리적인 짧은 {@code due}를 설정한다.
	 *
	 * @param due	대기 제한 시각
	 * @return	제한 시각 전에 작업이 RUNNING에 도달한 경우는 {@code true},
	 * 			그렇지 않으면 {@code false}.
	 * @throws InterruptedException	작업 시작 대기 중 대기 쓰레드가 interrupt된 경우.
	 */
	public boolean waitForStarted(Date due) throws InterruptedException;
	
	/**
	 * 본 작업에 제한시간을 설정한다.
	 * <p>
	 * 제한 시간이 도래할 때 작업이 아직 종료되지 않은 경우, {@link #cancel(boolean) cancel(true)}와
	 * 동등한 중단 절차가 자동으로 호출된다. 즉 수행 중인 작업이 중단을 지원하면
	 * (예: {@code CancellableWork} 구현) 강제 중단되며, 그렇지 않으면 cancel 시도가
	 * 실패하고 작업은 그대로 진행된다.
	 * <p>
	 * 시각 비교는 시스템 벽시계({@link System#currentTimeMillis()}) 기준이므로,
	 * NTP 보정 등 시스템 시각 변경에 영향을 받을 수 있다.
	 * <p>
	 * 경계 케이스:
	 * <ul>
	 *   <li>이미 종료된 작업({@link #isDone()} == {@code true})에 호출하면 no-op으로 처리된다 —
	 *       제한 시간이 도래해도 cancel 시도가 무시된다.
	 *   <li>{@code timeout}이 0 또는 음수이면 즉시 cancel이 시도된다.
	 *   <li>같은 작업에 본 메소드를 여러 번 호출하면 각 호출이 독립적으로 등록되어
	 *       가장 빠른 제한 시간이 cancel을 발생시킨다(이후의 cancel 시도는 이미 종료된
	 *       작업에 대한 호출이라 무해함).
	 * </ul>
	 * <p>
	 * 반환값이 없으므로 등록 성공 여부는 별도로 확인할 수 없다. 적용 결과는
	 * {@link #getState()} 또는 {@link #waitForFinished()}로 확인한다.
	 *
	 * @param timeout	제한 시간.
	 * @param unit		제한 시간의 단위.
	 */
	public void setTimeout(long timeout, TimeUnit unit);
	
	/**
	 * 본 작업이 성공적으로 완료된 경우, 결과 값에 {@code mapper}를 적용한
	 * 새 {@link Execution}을 반환한다.
	 * <p>
	 * 본 작업이 실패하거나 취소된 경우 {@code mapper}는 호출되지 않으며,
	 * 반환된 {@link Execution}은 동일한 실패 또는 취소 상태로 종료된다.
	 * 즉 표준 monadic map 시맨틱(성공 시에만 변환)을 따른다.
	 * <p>
	 * 새 {@link Execution}을 chain하여 다른 비동기 작업을 이어가려면
	 * {@link EventDrivenExecution#flatMap(Function)}을 사용한다.
	 *
	 * @param mapper	성공 결과 값을 변환하는 함수.
	 * @param <S>	변환 후 결과 타입.
	 * @return 본 작업의 결과에 {@code mapper}를 적용한 {@link Execution}.
	 */
	public default <S> Execution<S> map(Function<? super T,? extends S> mapper) {
		return new MapChainExecution<>(this, mapper);
	}
	

	/**
	 * 작업의 상태가 {@link AsyncState#STARTING}에서 {@link AsyncState#RUNNING}으로
	 * 전이되는 시점에 호출될 리스너를 등록한다.
	 * <p>
	 * 리스너는 작업의 시작을 알리는 스레드(작업을 구동시킨 스레드 또는 워커 스레드)에서
	 * <b>동기적으로</b> 실행되며, 본 작업의 내부 상태 lock을 보유한 채로 실행된다.
	 * 따라서 리스너 본문은 짧고 블로킹되지 않아야 하며, 본 작업의 다른 메소드를
	 * 호출해서는 안 된다 (deadlock 위험). 무거운 작업이 필요하면
	 * {@link #whenStartedAsync(Runnable)}을 사용한다.
	 * <p>
	 * 등록 시점에 작업이 이미 RUNNING에 한 번이라도 도달한 적이 있는 경우, 리스너는 곧바로 실행된다.
	 * 단, 작업이 RUNNING에 도달하지
	 * 못한 채로 종료된 경우(예: NOT_STARTED → CANCELLED 직접 전이, STARTING → FAILED)에는
	 * 리스너가 호출되지 않는다 — "작업이 시작되었음"을 통지하는 것이 시맨틱이기 때문이다.
	 * <p>
	 * 이미 같은 리스너 인스턴스가 등록되어 있는 경우에는 등록이 무시된다.
	 * 즉, 동일 리스너로 여러 번 등록해도 단 한 번만 호출된다.
	 *
	 * @param listener	작업 시작 시 호출될 리스너.
	 * @return 본 {@link Execution} 객체 (체이닝용).
	 */
	public Execution<T> whenStarted(Runnable listener);

	/**
	 * 작업의 상태가 {@link AsyncState#STARTING}에서 {@link AsyncState#RUNNING}으로
	 * 전이되는 시점에 호출될 리스너를 등록한다.
	 * <p>
	 * 리스너는 본 작업과 무관한 별도의 비동기 스레드 풀에서 실행되므로, 본 작업의
	 * 진행을 막지 않으며 리스너 본문이 길어지거나 블로킹되어도 안전하다.
	 * 콜백 실행 컨텍스트가 어느 스레드인지 보장되지 않으므로 thread-affinity가 필요한
	 * 작업은 호출자가 직접 스레드를 전환해야 한다.
	 * <p>
	 * 등록 시점에 작업이 이미 RUNNING에 한 번이라도 도달한 적이 있는 경우, 리스너는
	 * 즉시 실행된다. 단, 작업이 RUNNING에 도달하지 못한 채로 종료된 경우
	 * (예: NOT_STARTED → CANCELLED 직접 전이, STARTING → FAILED)에는 리스너가
	 * 호출되지 않는다.
	 * <p>
	 * 이미 같은 리스너 인스턴스가 등록되어 있는 경우에는 등록이 무시된다.
	 * 즉, 동일 리스너로 여러 번 등록해도 단 한 번만 호출된다.
	 *
	 * @param listener	작업 시작 시 호출될 리스너.
	 * @return 본 {@link Execution} 객체 (체이닝용).
	 */
	public Execution<T> whenStartedAsync(Runnable listener);

	/**
	 * 작업이 종료될 때 호출될 핸들러를 등록한다.
	 * <p>
	 * 핸들러는 작업의 종료를 알리는 스레드에서 <b>동기적으로</b> 실행되며,
	 * 본 작업의 내부 상태 lock을 보유한 채로 실행된다. 따라서 핸들러 본문은 짧고
	 * 블로킹되지 않아야 하며, 본 작업의 다른 메소드를 호출해서는 안 된다
	 * (deadlock 위험). 무거운 작업이 필요하면 {@link #whenFinishedAsync(Consumer)}을 사용한다.
	 * <p>
	 * 등록 시점에 작업이 이미 종료된 상태인 경우, 핸들러는 호출 스레드에서 즉시 실행된다.
	 * 핸들러는 작업의 모든 종료 케이스(성공/실패/취소)에 대해 단 한 번 호출된다.
	 * <p>
	 * 이미 핸들러가 등록된 경우에는 등록이 무시된다.
	 * 즉, 동일 핸들러로 여러 번 등록해도 단 한 번만 호출된다.
	 *
	 * @param handler	작업 종료 시 호출될 핸들러. 인자로 종료 결과({@link Result})가 전달된다.
	 * @return 본 {@link Execution} 객체 (체이닝용).
	 */
	public Execution<T> whenFinished(Consumer<Result<T>> handler);

	/**
	 * 작업이 종료될 때 호출될 핸들러를 등록한다.
	 * <p>
	 * 핸들러는 본 작업과 무관한 별도의 비동기 스레드 풀에서 실행되므로,
	 * 핸들러 본문이 길어지거나 블로킹되어도 안전하다.
	 * 콜백 실행 컨텍스트가 어느 스레드인지 보장되지 않으므로 thread-affinity가 필요한
	 * 작업은 호출자가 직접 스레드를 전환해야 한다.
	 * 핸들러는 작업의 모든 종료 케이스(성공/실패/취소)에 대해 단 한 번 호출된다.
	 * <p>
	 * 이미 핸들러가 등록된 경우에는 등록이 무시된다.
	 * 즉, 동일 핸들러로 여러 번 등록해도 단 한 번만 호출된다.
	 *
	 * @param handler	작업 종료 시 호출될 핸들러. 인자로 종료 결과({@link Result})가 전달된다.
	 * @return 본 {@link Execution} 객체 (체이닝용).
	 */
	public Execution<T> whenFinishedAsync(Consumer<Result<T>> handler);
	
	/**
	 * 작업이 성공적으로 완료된 경우에만 호출될 핸들러를 등록한다.
	 * <p>
	 * 작업이 실패하거나 취소된 경우에는 핸들러가 호출되지 않는다.
	 * 핸들러는 {@link #whenFinishedAsync(Consumer)}을 통해 등록되므로 별도의 비동기
	 * 스레드 풀에서 실행되며, 본 작업의 진행을 막지 않는다.
	 * <p>
	 * <b>주의</b>: 본 메소드는 매 호출마다 새 wrapper 람다를 생성하여
	 * {@link #whenFinishedAsync(Consumer)}에 등록한다. 따라서 같은 {@code handler}로
	 * 여러 번 호출해도 각 호출이 별개의 등록으로 누적되며, override 시맨틱이 적용되지 않는다.
	 * 동일 핸들러로 1회만 등록되도록 보장하려면 {@link #whenFinishedAsync(Consumer)}에
	 * 직접 핸들러를 등록한다.
	 *
	 * @param handler	성공 결과 값을 인자로 받는 핸들러.
	 * @return 본 {@link Execution} 객체 (체이닝용).
	 */
	public default Execution<T> whenCompleted(Consumer<T> handler) {
		Preconditions.checkNotNullArgument(handler, "handler is null");

		return whenFinishedAsync(result -> result.ifSuccessful(handler));
	}

	/**
	 * 작업이 예외 발생으로 실패한 경우에만 호출될 핸들러를 등록한다.
	 * <p>
	 * 작업이 성공적으로 완료되거나 취소된 경우에는 핸들러가 호출되지 않는다.
	 * 핸들러에 전달되는 원인 예외는 작업 내부에서 발생한 원본 예외이며,
	 * {@link ExecutionException}으로 감싸지지 않는다.
	 * 핸들러는 {@link #whenFinishedAsync(Consumer)}을 통해 등록되므로 별도의 비동기
	 * 스레드 풀에서 실행되며, 본 작업의 진행을 막지 않는다.
	 * <p>
	 * <b>주의</b>: 본 메소드는 매 호출마다 새 wrapper 람다를 생성하여
	 * {@link #whenFinishedAsync(Consumer)}에 등록한다. 따라서 같은 {@code handler}로
	 * 여러 번 호출해도 각 호출이 별개의 등록으로 누적되며, override 시맨틱이 적용되지 않는다.
	 * 동일 핸들러로 1회만 등록되도록 보장하려면 {@link #whenFinishedAsync(Consumer)}에
	 * 직접 핸들러를 등록한다.
	 *
	 * @param handler	실패 원인 예외를 인자로 받는 핸들러.
	 * @return 본 {@link Execution} 객체 (체이닝용).
	 */
	public default Execution<T> whenFailed(Consumer<Throwable> handler) {
		Preconditions.checkNotNullArgument(handler, "handler is null");

		return whenFinishedAsync(result -> result.ifFailed(handler));
	}

	/**
	 * 작업이 취소된 경우에만 호출될 핸들러를 등록한다.
	 * <p>
	 * 작업이 성공적으로 완료되거나 실패한 경우에는 핸들러가 호출되지 않는다.
	 * 핸들러는 {@link #whenFinishedAsync(Consumer)}을 통해 등록되므로 별도의 비동기
	 * 스레드 풀에서 실행되며, 본 작업의 진행을 막지 않는다.
	 * <p>
	 * <b>주의</b>: 본 메소드는 매 호출마다 새 wrapper 람다를 생성하여
	 * {@link #whenFinishedAsync(Consumer)}에 등록한다. 따라서 같은 {@code handler}로
	 * 여러 번 호출해도 각 호출이 별개의 등록으로 누적되며, override 시맨틱이 적용되지 않는다.
	 * 동일 핸들러로 1회만 등록되도록 보장하려면 {@link #whenFinishedAsync(Consumer)}에
	 * 직접 핸들러를 등록한다.
	 *
	 * @param handler	작업 취소 시 호출될 핸들러.
	 * @return 본 {@link Execution} 객체 (체이닝용).
	 */
	public default Execution<T> whenCancelled(Runnable handler) {
		Preconditions.checkNotNullArgument(handler, "handler is null");

		return whenFinishedAsync(result -> result.ifNone(handler));
	}
	
	/**
	 * {@code Execution<? extends T>}을 {@code Execution<T>}로 안전하게 변환한다.
	 * <p>
	 * {@link Execution}은 결과를 생산하는 producer 의미만 가지므로 결과 타입에 대해
	 * 공변(covariant)이며, {@code Execution<? extends T>}는 {@code Execution<T>}로
	 * 변환해도 타입 안정성이 유지된다. 본 메소드는 이 변환을 캡슐화하여
	 * 호출자가 unchecked cast 경고를 처리하지 않아도 되도록 한다.
	 *
	 * @param exec	변환할 {@link Execution}.
	 * @param <T>	변환 후 타입 파라미터.
	 * @return {@code Execution<T>}로 narrow된 동일 객체.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Execution<T> narrow(Execution<? extends T> exec) {
		return (Execution<T>)exec;
	}
}
