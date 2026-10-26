package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import utils.Throwables;
import utils.func.FOption;


/**
 * Loop 형태로 반복 작업을 수행하여 최종 결과를 생성하는 비동기 Execution의 추상 기반 클래스.
 * <p>
 * 본 클래스는 다음의 단계로 작업을 수행한다 (자세한 계약은 각 메소드 Javadoc 참고):
 * <ol>
 *   <li>{@link #initializeLoop()} — 1회 호출되어 loop 수행에 필요한 초기화 작업을 수행한다.</li>
 *   <li>{@link #iterate(long)} — 최종 결과가 생성될 때까지 또는 취소 신호가 감지될 때까지 반복 호출된다.</li>
 *   <li>{@link #finalizeLoop()} — {@link #initializeLoop()}이 성공한 이후라면 항상 한 번 호출되어
 *       cleanup을 수행한다.</li>
 * </ol>
 *
 * <h2>예외 처리</h2>
 * 부모 클래스 {@link AbstractThreadedExecution}의 계약에 따라 {@link #executeWork()} 메서드가
 * 던지는 예외는 다음과 같이 해석된다:
 * <ul>
 *   <li>{@link InterruptedException}, {@link CancellationException} —
 *       작업이 취소된 것으로 간주되어 {@link AsyncState#CANCELLED} 상태로 전이.</li>
 *   <li>그 외 {@link Exception} — 작업 실패로 간주되어 {@link AsyncState#FAILED} 상태로 전이.</li>
 * </ul>
 *
 * <h2>취소(cancel) 협력 계약</h2>
 * 본 클래스의 취소는 협력적(cooperative)으로 동작한다. 실행 중({@code RUNNING})에
 * {@link EventDrivenExecution#cancel(boolean) cancel(true)}가 호출되면 상태를 {@code CANCELLING}으로
 * 바꾸고 곧바로 반환하며, 최종 {@code CANCELLED} 전이는 기다리지 않는다. 워커 스레드를 인터럽트하지도 않는다.
 * loop는 다음 {@link #iterate(long)}을 시작하기 직전에 {@link #isCancelRequested()}로 취소 요청을 확인하고
 * {@link CancellationException}으로 끝나며, 그때 {@link #finalizeLoop()}을 거쳐 {@code CANCELLED}가 된다.
 * 따라서 한 번의 {@link #iterate(long)}이 오래 걸리면 그만큼 종료가 늦어지고, 취소 뒤 종료를 기다리는 쪽
 * ({@link #waitForFinished()}, {@link #get()} 등)도 그동안 반환되지 않는다.
 * <p>
 * 같은 시점에 이미 종료 상태({@code COMPLETED}/{@code FAILED}/{@code CANCELLED})인 경우에도 loop는
 * 다음 iteration을 수행하지 않고 끝나며, {@link #finalizeLoop()}을 거치되 이미 확정된 최종 상태는 유지된다.
 * {@link CancellableWork#cancelWork()}가 예외를 던져 {@code cancel()}이 실행을 {@code FAILED}로 전이시킨 경우나
 * 외부에서 {@link #notifyFailed(Throwable)} 등을 직접 호출한 경우가 이에 해당한다.
 * <p>
 * 취소 요청 시점에 진행 중이던 {@link #iterate(long)}이 최종 결과를 반환하면, 그 결과로 loop가 끝나
 * {@code COMPLETED}가 된다({@code CANCELLING}에서의 완료 전이는 허용된다). 즉 {@code cancel(true)}가
 * {@code true}를 반환했더라도 최종 상태는 {@code COMPLETED}일 수 있으므로, 호출자는 최종 상태를
 * {@link #waitForFinished()} 등으로 확인해야 한다.
 * <p>
 * 본 클래스는 {@link CancellableWork}를 구현하지 않는다. 진행 중인 iteration을 깨워야 하는 하위 클래스
 * (예: 블로킹 대기 중인 소켓이나 하위 작업을 중단해야 하는 경우)는 {@link CancellableWork}를 구현할 수 있으며,
 * 그 경우 {@link EventDrivenExecution#cancel(boolean)}이 {@code CANCELLING} 전이 뒤 lock 외부에서
 * {@link CancellableWork#cancelWork()}를 호출한다. {@code cancelWork()}는 {@code cancel()}을 호출한
 * 스레드에서 실행되므로, loop가 끝나기를 기다리지 말고 중단 신호만 보낸 뒤 반환해야 한다.
 * 구현 시 지켜야 할 패턴은 {@link #iterate(long)}의 Javadoc을 참고할 것.
 *
 * <h2>스레드 모델</h2>
 * {@link #executeWork()}는 1회만 수행된다. {@link #start()}로 시작하면 {@link AbstractThreadedExecution}이
 * 만든 별도의 워커 스레드(또는 주입된 {@link java.util.concurrent.Executor})에서, {@link #run()}으로 실행하면
 * 호출 스레드에서 수행된다. 어느 경우든 {@link #initializeLoop()} / {@link #iterate(long)} /
 * {@link #finalizeLoop()}은 모두 같은 스레드에서 순차적으로 호출되며, 구현체에서 별도의
 * 동기화는 일반적으로 필요하지 않다. 다만 {@link CancellableWork#cancelWork()}는 외부 스레드에서
 * 호출되므로 구현체가 외부에서 관찰 가능한 상태를 노출하는 경우에는 별도의 동기화 처리를 고려해야 한다.
 *
 * @param <T>	loop 수행으로 생성되는 최종 결과의 타입
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractLoopExecution<T> extends AbstractThreadedExecution<T> {
	private volatile @Nullable AsyncResult<T> m_loopOutcome = null;

	/**
	 * Loop을 수행하기 위해 초기화 작업을 수행한다.
	 * <p>
	 * Loop 본체({@link #iterate(long)})가 시작되기 전 1회 호출된다.
	 * 본 메소드에서 예외가 발생한 경우 {@link #finalizeLoop()}은 호출되지 않으며,
	 * 부모 계약에 따라 Execution은 예외의 종류에 따라 다음 상태로 전이된다.
	 * <ul>
	 *   <li>{@link InterruptedException}, {@link CancellationException} — {@link AsyncState#CANCELLED}.</li>
	 *   <li>그 외 {@link Exception} — {@link AsyncState#FAILED}.</li>
	 * </ul>
	 * 따라서 일부 초기화만 성공하고 실패한 경우의 cleanup은 본 메소드 내부에서 직접 처리해야 한다.
	 * <p>
	 * 이 코드는 {@code STARTING} 상태에서 {@link #initializeThread()}를 통해 호출된다.
	 * {@link #start()}로 시작하면 별도의 워커 스레드에서, {@link #run()}으로 실행하면 호출 스레드에서 수행된다.
	 *
	 * @throws InterruptedException		초기화 도중 스레드가 인터럽트된 경우. 연산은 취소된다.
	 * @throws CancellationException	초기화 도중 취소된 경우. 연산은 취소된다.
	 * @throws Exception	그 밖의 초기화 오류. 연산은 실패한다.
	 */
	protected abstract void initializeLoop() throws Exception;

	/**
	 * Loop의 한 번의 iteration 작업을 수행하여 최종 결과를 생성한다.
	 * <p>
	 * 반환값에 따라 loop 진행이 결정된다:
	 * <ul>
	 *   <li>{@link FOption#empty()} — 다음 iteration 진행.</li>
	 *   <li>값이 채워진 {@link FOption} — 그 값이 최종 결과가 되어 loop 종료.</li>
	 * </ul>
	 * 작업이 중단되어야 할 때는 {@link InterruptedException} 또는 {@link CancellationException}을
	 * 직접 throw 한다. 본 메소드는 {@code null}을 반환해서는 안 되며, 반환하면
	 * {@link IllegalStateException}으로 연산이 {@link AsyncState#FAILED} 상태가 된다.
	 * <p>
	 * <b>취소(cancel) 협력 계약:</b> 본 클래스의 취소 처리는 협력적(cooperative)으로 동작한다.
	 * 취소 요청(및 외부에서의 종료 상태 전이)은 loop 본체가 다음 iteration을 시작하기 직전에만 검사되므로,
	 * 한 번의 iteration이 오래 걸리면 {@code cancel(true)}가 반환된 뒤에도 그 iteration이 끝날 때까지
	 * 실행은 {@code CANCELLING}에 머물고 {@code CANCELLED}로 끝나지 않는다. 따라서 본 메소드 구현체는
	 * 다음 중 하나 이상을 따라야 한다:
	 * <ul>
	 *   <li>iteration 내부에서 주기적으로 {@link #isCancelRequested()}를 호출하여 {@code true}이면
	 *       {@link CancellationException}을 던져 빠르게 종료한다.</li>
	 *   <li>iteration이 짧게 끝나도록 작업 단위를 충분히 잘게 분할한다.</li>
	 *   <li>블로킹 대기가 불가피하면 하위 클래스가 {@link CancellableWork}를 구현하여
	 *       {@link CancellableWork#cancelWork()}에서 그 대기를 깨운다(소켓 닫기, 하위 작업 취소 등).</li>
	 * </ul>
	 * 위 계약을 준수하지 않으면 취소한 실행이 끝나지 않아, 종료를 기다리는 쪽이 사실상 hang될 수 있으므로
	 * 주의해야 한다.
	 *
	 * @param loopIndex		loop 인덱스. 0부터 시작함.
	 * @return		최종 결과를 담은 {@link FOption} (loop 종료) 또는 {@link FOption#empty()} (다음 iteration).
	 * @throws InterruptedException		loop 작업이 인터럽트된 경우.
	 * @throws CancellationException	loop 작업이 취소된 경우.
	 * @throws TimeoutException			작업이 시간 제한을 넘긴 경우.
	 * @throws ExecutionException		비동기 작업의 결과가 실패한 경우.
	 */
	protected abstract @NotNull FOption<T> iterate(long loopIndex)
		throws InterruptedException, CancellationException, TimeoutException, ExecutionException;
	
	/**
	 * Loop 작업이 종료된 후 cleanup 작업을 수행한다.
	 * <p>
	 * 호출 보장: {@link #initializeLoop()}이 성공한 경우에만 호출되며, loop의 종료 사유
	 * (정상 완료/취소/실패)와 무관하게 항상 1회 호출된다. 초기화 도중 외부에서 이미 종료(실패·취소)되어
	 * loop 본체가 시작되지 못한 경우에도 호출된다. loop 본체가 실행된 경우에는 최종 상태로 전이하기 전에 호출된다.
	 * 단, loop 수행 중 외부에서 이미 종료 상태로 전이된 경우에는 그 전이 이후에 호출된다.
	 * 본 메소드에서 발생한 예외는 무시되고 로깅만 되므로, loop 본체의 결과/예외에 영향을 주지 않는다.
	 * 따라서 정밀한 오류 처리가 필요한 경우 구현체 내부에서 직접 처리해야 한다.
	 * <p>
	 * 스레드 인터럽트로 loop가 끝난 경우에도 정리 작업의 블로킹 호출이 바로 중단되지 않도록,
	 * 인터럽트 상태를 지운 채로 호출하고 호출이 끝나면 인터럽트 상태를 복원한다.
	 * <p>
	 * Loop가 어떻게 끝났는지(완료·실패·취소와 실패 원인)는 본 메소드 안에서 {@link #getLoopOutcome()}으로
	 * 조회할 수 있다.
	 */
	protected abstract void finalizeLoop();

	/**
	 * Loop의 종료 결과를 반환한다. {@link #finalizeLoop()} 안에서 호출하기 위한 메소드이다.
	 * <p>
	 * {@link #finalizeLoop()}은 최종 상태로 전이하기 전에 호출되므로, 그 시점에는 {@link #poll()}로 결과를
	 * 얻을 수 없다(아직 {@code RUNNING}이다). 본 메소드는 loop 본체의 반환값·예외로부터 최종 결과를 미리
	 * 구성하여 제공한다.
	 * <ul>
	 *   <li>외부에서 이미 종료 상태로 확정된 경우 — 그 확정된 결과.</li>
	 *   <li>{@link #iterate(long)}이 결과를 반환한 경우 — {@code COMPLETED}와 그 결과 값({@code null}일 수 있다).</li>
	 *   <li>{@link InterruptedException}/{@link CancellationException}으로 끝난 경우 — {@code CANCELLED}.</li>
	 *   <li>그 외 예외로 끝난 경우 — {@code FAILED}와 그 원인({@link utils.Throwables#unwrapThrowable}로 풀린 예외).</li>
	 * </ul>
	 * {@link #finalizeLoop()} 반환 직후부터 최종 전이 사이의 좁은 구간에 외부에서 종료가 확정되면, 본 메소드가
	 * 반환한 결과와 실제 최종 상태가 다를 수 있다.
	 *
	 * @return	loop의 종료 결과. {@link #finalizeLoop()} 호출 이전에는 {@code null}.
	 */
	protected @Nullable AsyncResult<T> getLoopOutcome() {
		return m_loopOutcome;
	}
	
	@Override
	protected void initializeThread() throws Exception {
		initializeLoop();
	}

	/**
	 * 초기화는 성공했지만 loop 본체({@link #executeWork()})가 시작되지 못한 경우에도
	 * {@link #finalizeLoop()}이 1회 호출되도록 한다.
	 */
	@Override
	protected void abortAfterInitialize() {
		// 시작하지 못한 것은 외부에서 이미 종료가 확정되었기 때문이므로, 그 결과를 종료 결과로 사용한다.
		m_loopOutcome = poll();
		finalizeLoopQuietly();
	}

	/**
	 * Loop 작업을 수행한다.
	 * <p>
	 * 이 함수는 {@link #notifyStarted()}가 호출된 후에 별도의 쓰레드에서 수행된다.
	 */
	@Override
	protected T executeWork() throws InterruptedException, CancellationException, Exception {
		long iterCount = -1;
		boolean completed = false;
		T loopResult = null;
		Throwable failure = null;
		try {
			while ( true ) {
				if ( isCancelRequested() || Thread.currentThread().isInterrupted() ) {
					throw new CancellationException("loop execution is cancelled");
				}
				// 외부에서 이미 종료 상태로 전이된 경우(예: cancelWork() 실패로 인한 FAILED, 외부 notifyFailed())
				// 다음 iteration을 수행하지 않고 loop를 끝낸다. 이미 확정된 최종 상태는 바뀌지 않는다.
				if ( isDone() ) {
					throw new CancellationException("loop execution is already finished: " + this);
				}

				// iterate가 결과를 반환한 경우(FOption.isPresent) loop를 종료.
				// FOption.empty()인 경우는 다음 iteration 진행.
				FOption<T> result = iterate(++iterCount);
				if ( result == null ) {
					throw new IllegalStateException("iterate() returned null: loopIndex=" + iterCount + ", " + this);
				}
				if ( result.isPresent() ) {
					loopResult = result.get();
					completed = true;
					return loopResult;
				}
			}
		}
		catch ( Throwable e ) {
			failure = e;
			throw e;
		}
		finally {
			m_loopOutcome = toLoopOutcome(completed, loopResult, failure);
			finalizeLoopQuietly();
		}
	}

	private AsyncResult<T> toLoopOutcome(boolean completed, @Nullable T loopResult, @Nullable Throwable failure) {
		if ( isDone() ) {
			// 외부에서 이미 종료 상태로 확정된 경우에는 그 결과가 최종 결과가 된다.
			return poll();
		}
		else if ( completed ) {
			return AsyncResult.completed(loopResult);
		}
		else if ( failure instanceof InterruptedException || failure instanceof CancellationException ) {
			return AsyncResult.cancelled();
		}
		else {
			return AsyncResult.failed(Throwables.unwrapThrowable(failure));
		}
	}

	/**
	 * finalizeLoop 수행과정에 발생하는 예외는 무시한다.
	 * 인터럽트 발생으로 이 메소드가 수행되는 경우, 인터럽트 상태로 인해
	 * finalizeLoop 수행이 블로킹 호출로 인해 바로 중단되지 않도록 인터럽트 상태를 지운 채로 호출한다.
	 * 호출이 끝나면 인터럽트 상태를 복원한다.
	 */
	private void finalizeLoopQuietly() {
		boolean interrupted = Thread.interrupted();
		try {
			finalizeLoop();
		}
		catch ( Exception e ) {
			getLogger().warn("failed to finalize loop execution", e);
		}
		finally {
			if ( interrupted ) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
