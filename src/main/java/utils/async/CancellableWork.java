package utils.async;


/**
 * 이미 {@link AsyncState#RUNNING} 상태에 진입한 {@link Execution}을 중단시킬 수 있음을 표시하는 마커 인터페이스.
 * <p>
 * {@link EventDrivenExecution#cancel(boolean) cancel(true)}는 {@code RUNNING} 상태인 execution이
 * 본 인터페이스를 구현하는 경우에만 실제 중단을 시도한다. 그렇지 않으면
 * {@link EventDrivenExecution#cancel(boolean)}은 즉시 {@code false}를 반환한다.
 * <p>
 * 본 인터페이스를 구현하는 경우의 cancel 흐름은 다음과 같다.
 * <ol>
 *   <li>외부에서 {@link EventDrivenExecution#cancel(boolean) cancel(true)} 호출.</li>
 *   <li>프레임워크가 상태를 {@code RUNNING → CANCELLING}으로 전이.</li>
 *   <li>프레임워크가 본 인터페이스의 {@link #cancelWork()}를 lock 외부에서 호출
 *       — 구현체는 진행 중인 작업이 종료되도록 협력적으로 처리해야 한다 (예: 워커 스레드 interrupt,
 *       내부 stop 플래그 세팅, 그리고 필요하다면 작업 종료까지 대기).</li>
 *   <li>{@code cancelWork()} 반환 후 프레임워크가
 *       {@link EventDrivenExecution#notifyCancelled() notifyCancelled()}로 종료 상태로 전이.</li>
 * </ol>
 * 구현 시 다음을 유의한다.
 * <ul>
 *   <li>{@code cancelWork()}는 lock을 보유하지 않은 상태에서 호출되므로, 작업이 완료될 때까지 대기하는
 *       전략(synchronous cancel)이 가능하다. {@link AbstractLoopExecution#cancelWork()}는 이 패턴을
 *       사용한다.</li>
 *   <li>중단 시점의 작업 상태에 따라 중단 요청이 무의미할 수 있다 — 예를 들어 작업이 이미 종료에
 *       임박한 경우. 이 경우 {@link #cancelWork()}는 {@code false}를 반환하여 요청이 사실상
 *       무시되었음을 알릴 수 있다.</li>
 *   <li>{@link #cancelWork()}의 반환값은 "중단 요청이 접수/처리되었는가" 를 의미할 뿐, 작업이
 *       실제로 종료되었는지를 보장하지 않는다. 최종 확인은 {@link Execution#isCancelled()} 또는
 *       {@link Execution#waitForFinished()}로 한다.</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CancellableWork {
	/**
	 * 진행 중인 작업의 중단을 시도한다.
	 * <p>
	 * 본 메소드는 {@link EventDrivenExecution#cancel(boolean) cancel(true)} 처리 과정에서
	 * 상태가 {@link AsyncState#CANCELLING}으로 전이된 직후 프레임워크에 의해 호출된다.
	 * 구현체는 진행 중인 작업이 종료될 수 있도록 협력적인 중단 처리를 수행해야 한다.
	 * <p>
	 * 반환값의 의미는 다음과 같다.
	 * <ul>
	 *   <li>{@code true} — 중단 요청이 접수되어 처리되었다. 단, 작업이 실제로 종료되었는지를
	 *       보장하지는 않는다 (구현체가 동기 대기 전략을 채택했다면 종료 시점까지 대기 후 반환되지만,
	 *       이는 인터페이스 차원의 보장이 아니다). 작업의 최종 종료 여부는
	 *       {@link Execution#isCancelled()} / {@link Execution#waitForFinished()}로 확인한다.</li>
	 *   <li>{@code false} — 중단 요청이 거부되었거나 처리되지 못했다. 예: 작업이 이미 종료 직전이라
	 *       중단이 무의미한 경우.</li>
	 * </ul>
	 *
	 * @return	중단 요청의 접수/처리 여부.
	 */
	public boolean cancelWork();
}
