package utils.async;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * {@code AbstractStatePoller}는 주어진 목표 상태가 될 때까지 주기적으로 확인하는 기능을
 * 수행한다.
 * 목표 상태 도달 여부는 abstract method {@link #pollState()}를 사용한다.
 * {@link #pollState()} 메소드는 결과 값에 따라 다음과 같이 동작한다:
 * <dl>
 * 		<dt>{@code Optional<R>}:</dt>
 * 		<dd>목표 상태에 도달한 경우이고, polling 과정이 종료된다.</dd>
 * 		<dt>{@code Optional.empty()}:</dt>
 * 		<dd>아직 목표 상태에 도달하지 않은 경우이고, 다음 polling이 진행된다.</dd>
 * 		<dt>{@code null}:</dt>
 * 		<dd>polling 과정에서 어떤 이유에 의해서 중단경이고, polling 과정이 중단된다.</dd>
 * </dl>
 * 만일 주어진 제한 시간 내에 목표 상태가 되지 않는 경우에는 {@code TimeoutException} 예외가
 * 발생한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractStatePoller<R> extends PeriodicLoopExecution<R> {
	private final Logger s_logger = LoggerFactory.getLogger(AbstractStatePoller.class);
	
	protected void initializePoller() throws Exception { super.initializeLoop(); };
	protected abstract Optional<R> pollState() throws Exception;
	protected void finalizePoller() throws Exception { super.finalizeLoop(); };
	
	protected final void initializeLoop() throws Exception { super.initializeLoop(); }
	protected final void finalizeLoop() throws Exception { super.finalizeLoop(); }
	
	protected AbstractStatePoller(Duration pollInterval, boolean cumulativeInterval) {
		super(pollInterval, cumulativeInterval);
		
		setLogger(s_logger);
	}
	
	protected AbstractStatePoller(Duration pollInterval) {
		super(pollInterval, true);
		
		setLogger(s_logger);
	}

	@Override
	protected final Optional<R> performPeriodicAction(long loopIndex) throws Exception {
		return pollState();
	}
}
