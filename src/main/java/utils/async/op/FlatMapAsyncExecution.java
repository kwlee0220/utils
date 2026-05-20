package utils.async.op;

import java.util.function.Function;

import utils.Throwables;
import utils.async.EventDrivenExecution;
import utils.async.Execution;
import utils.async.StartableExecution;
import utils.func.Result;


/**
 * 선행(leader) {@link Execution}의 종료 결과를 받아 후속(follower) {@link Execution}을 생성·실행하는
 * 합성 비동기 수행.
 * <p>
 * 두 단계로 동작한다:
 * <ol>
 *   <li>leader가 RUNNING에 도달하면 본 실행도 RUNNING으로 전이한다.</li>
 *   <li>leader가 종료(성공/실패/취소)되면 그 결과를 {@code chain} 함수에 전달하여 follower
 *       {@link Execution}을 얻는다. follower가 아직 시작되지 않았다면 본 클래스가 시작시킨다
 *       (단, follower가 {@link StartableExecution}인 경우에 한함).</li>
 *   <li>follower의 종료 결과가 그대로 본 실행의 종료 결과가 된다.</li>
 * </ol>
 * 본 클래스는 {@link utils.async.CancellableWork}를 구현하지 않으므로, {@link #cancel(boolean)} 호출은
 * 본 실행이 NOT_STARTED 상태일 때만 효과가 있고, leader/follower는 자동으로 취소되지 않는다.
 * 필요하면 호출자가 leader/follower를 직접 취소해야 한다.
 *
 * @param <T> leader 결과 타입.
 * @param <S> follower(=본 실행) 결과 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FlatMapAsyncExecution<T,S> extends EventDrivenExecution<S> {
	/**
	 * leader 실행과 그 결과로부터 follower를 생성할 chain 함수를 받아 합성 실행을 구성한다.
	 * <p>
	 * 등록되는 리스너:
	 * <ul>
	 *   <li>leader의 {@code whenStarted}(RUNNING 진입) 이벤트 → 본 실행의 {@link #notifyStarted()}로
	 *       위임하여 RUNNING으로 전이.</li>
	 *   <li>leader의 {@code whenFinished}(종료) 이벤트 → {@code chain.apply(result)}로 follower를
	 *       얻고, 종료 콜백을 등록한 뒤 (필요 시) follower를 시작.</li>
	 *   <li>follower의 {@code whenFinished} 이벤트 → 결과에 따라 {@link #notifyCompleted(Object)} /
	 *       {@link #notifyFailed(Throwable)} / {@link #notifyCancelled()}로 본 실행의 종료 결과를
	 *       결정.</li>
	 * </ul>
	 * 본 실행의 RUNNING 전이는 leader의 STARTED 이벤트만으로 일어나므로, follower 단계에서 별도의
	 * STARTED 콜백은 등록하지 않는다 (이 시점에 본 실행은 이미 RUNNING 상태).
	 * <p>
	 * chain 함수에서 예외가 발생하면 {@link utils.Throwables#unwrapThrowable}로 풀어 본 실행의
	 * {@link #notifyFailed(Throwable)} 원인으로 사용한다.
	 * <p>
	 * <b>주의</b>: chain이 반환한 follower가 NOT_STARTED인데 {@link StartableExecution}이 아니면
	 * {@code leader.whenFinished} 콜백 안에서 {@link IllegalStateException}이 던져진다. 그러나
	 * 이 예외는 {@link EventDrivenExecution}의 리스너 디스패처가 try/catch로 로깅만 하고 삼키기
	 * 때문에 호출자에게 전파되지 않는다. 결과적으로 본 합성 실행은 종료 상태로 전이하지 못하고
	 * RUNNING 상태에 머무른다 — chain은 항상 시작 가능하거나 이미 시작된 follower를 반환해야 한다.
	 *
	 * @param leader 선행 실행. 본 생성자 외부에서 시작되어야 한다.
	 * @param chain  leader 결과를 follower 실행으로 변환하는 함수.
	 */
	FlatMapAsyncExecution(Execution<T> leader, Function<? super Result<T>, ? extends EventDrivenExecution<S>> chain) {
		leader.whenStarted(this::notifyStarted);
		leader.whenFinished(ret -> {
			EventDrivenExecution<S> follower;
			try {
				follower = chain.apply(ret);
			}
			catch ( Throwable e ) {
				notifyFailed(Throwables.unwrapThrowable(e));
				return;
			}
			
			follower.whenFinished(ret2 -> ret2.ifSuccessful(this::notifyCompleted)
												.ifFailed(this::notifyFailed)
												.ifNone(this::notifyCancelled));
			if ( !follower.isStarted() ) {
				if ( follower instanceof StartableExecution startable ) {
					startable.start();
				}
				else {
					throw new IllegalStateException(
									String.format("follower has not been started: %s", follower));
				}
			}
		});
	}
}