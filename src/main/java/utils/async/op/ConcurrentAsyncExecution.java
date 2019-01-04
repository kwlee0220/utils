package utils.async.op;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.vavr.control.Try;
import net.jcip.annotations.GuardedBy;
import utils.Guard;
import utils.async.AbstractAsyncExecution;
import utils.async.AsyncExecution;
import utils.async.CancellableWork;
import utils.async.Result;


/**
 * <code>ConcurrentAsyncExecution</code>은 주어진 복수개의 {@link AsyncExecution}을 동시에
 * 수행시키는 비동기 수행 클래스를 정의한다.
 * <p>
 * 소속 비동기 수행 수행 중 오류 또는 취소가 발생되는 것은 모두 무시되고 종료된 것으로 간주된다.
 * 본 비동기 수행은 결과 값으로 <code>null</code>을 반환한다. 
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ConcurrentAsyncExecution extends AbstractAsyncExecution<Void>
												implements CancellableWork {
	private static final Logger s_logger = LoggerFactory.getLogger(ConcurrentAsyncExecution.class);
	
	private final AsyncExecution<?>[] m_elements;
	private int m_noOfElmCompletionToCompletion;
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private int m_noOfFinishes = 0;
	@GuardedBy("m_guard") private int m_noOfCompletions = 0;

	/**
	 * 동시 수행 비동기 수행 객체를 생성한다.
	 * <p>
	 * 모든 소속 비동기 수행이 완료되어야 전체 동시 수행 비동기 수행이 완료된 것으로 간주하지
	 * 않고, 주어진 갯수의 비동기 수행만 완료되어도 전체 동시 수행 비동기 수행이 완료된 것으로
	 * 간주한다.
	 * <br>
	 * 원소 비동기 수행의 중지 또는 실패는 동시 수행 비동기 수행에 영향을 주지 않는다.
	 * 
	 * @param elements	동시 수행될 비동기 수행 객체 배열.
	 * @throws InvalidArgumentException	<code>elements</code>가 <code>null</code>이거나
	 * 									길이가 0인 경우.
	 */
	public ConcurrentAsyncExecution(AsyncExecution<?>... elements) {
		Objects.requireNonNull(elements, "element AsyncExecutions");
		
		m_elements = elements;
		m_noOfElmCompletionToCompletion = elements.length;
		
		setLogger(s_logger);
	}
	
	public void setElementCompletionCountToComplate(int count) {
		Preconditions.checkArgument(count > 0 && count < m_elements.length);
		
		m_guard.run(() -> m_noOfElmCompletionToCompletion = count, false);
	}
	
	@Override
	public String toString() {
		return String.format("Concurrent[size=%d, noOfFinisheds=%d]",
								m_elements.length, m_noOfCompletions);
	}

	@Override
	public void start() {
		if ( !notifyStarting() ) {
			return;
		}
		
		for ( int i =0; i < m_elements.length; ++i ) {
			m_elements[i].whenDone(this::onElementFinished);
			m_elements[i].start();
		}
		
		notifyStarted();
	}

	@Override
	public boolean cancelWork() {
		// 멤버 aop들의 수행을 중단시킨다.
		for ( int i =0; i < m_elements.length; ++i ) {
			try {
				m_elements[i].cancel(true);
			}
			catch ( Exception ignored ) { }
		}
		
		return true;
	}
	
	private void onElementFinished(Result<?> result) {
		// start 과정 중에, 일부 element exec가 종료될 수도 있기 때문에
		// 모든 element의 start가 모두 끝날 때까지 대기한다.
		Try.run(() -> waitForStarted());
		
		m_guard.run(() -> {
			++m_noOfFinishes;
			result.ifCompleted(r -> ++m_noOfCompletions);
			
			if ( isCancelRequested() ) {
				notifyCancelled();
			}
			
			if ( m_noOfFinishes >= m_elements.length
				|| m_noOfCompletions >= m_noOfElmCompletionToCompletion ) {
				notifyCompleted(null);
			}
		}, false);
		
		if ( isDone() ) {
			// 아직 수행이 종료되지 않은 멤버 aop들의 수행을 중단시킨다.
			for ( int i =0; i < m_elements.length; ++i ) {
				try {
					m_elements[i].cancel(true);
				}
				catch ( Exception ignored ) { }
			}
		}
	}
}