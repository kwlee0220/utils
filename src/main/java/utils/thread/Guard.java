package utils.thread;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.Throwables;
import utils.func.CheckedConsumerX;
import utils.func.CheckedRunnable;
import utils.func.CheckedRunnableX;
import utils.func.CheckedSupplier;
import utils.func.CheckedSupplierX;


/**
 * {@link Lock}과 {@link Condition}을 묶어 lock 획득/해제와 signal을 자동으로 처리해주는 동시성 헬퍼.
 * <p>
 * 본 클래스는 {@link ReentrantLock} 한 개와 그 위에 만든 {@link Condition} 한 개를 쌍으로 보유하며,
 * {@code utils.async} 패키지 전반에서 "lock + condition variable" 패턴의 표준 wrapper로 사용된다.
 * 사용 모드는 두 가지다:
 * <ul>
 *   <li><b>작업 실행 모드</b> — {@link #run(Runnable)} / {@link #get(Supplier)} /
 *       {@link #accept(Consumer, Object)}과 그 {@code Checked} 변형. lock을 자동 획득/해제하고,
 *       작업 완료 후 finally에서 {@link #signalAll()}을 자동 호출한다. 공유 상태를 갱신하고
 *       대기 쓰레드를 깨우는 가장 일반적인 경로.</li>
 *   <li><b>조건 대기 모드</b> — {@link #awaitCondition(Supplier)} 또는 시간 제한 변형이 반환하는
 *       {@link AwaitCondition} / {@link TimedAwaitCondition} 빌더로 "조건 대기 → 작업 → signal"을
 *       한 번에 표현한다. {@link #preAction(Runnable)}로 lock 획득 직후 한 번만 실행할 사전 작업도
 *       chaining 가능하다.</li>
 * </ul>
 * <p>
 * <b>저수준 API</b>: {@link #lock()} / {@link #unlock()} / {@link #awaitSignal()} 등도 직접 노출되므로
 * 위 두 모드로 표현하기 힘든 흐름은 수동 구성할 수 있다. 단 호출자가 lock 획득/해제 짝과
 * signal 호출 시점을 직접 책임져야 한다.
 * <p>
 * <b>재진입성</b>: 내부 lock은 {@link ReentrantLock}이므로 같은 쓰레드에서의 재진입을 허용한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Guard {
	private final Lock m_lock;
	private final Condition m_cond;

	/**
	 * 새 {@link Guard} 인스턴스를 생성한다.
	 * <p>
	 * 내부적으로 {@link ReentrantLock}과 그 위에 만든 {@link Condition}을 한 쌍 생성하여 보유한다.
	 *
	 * @return	새 {@link Guard} 인스턴스
	 */
	public static Guard create() {
		Lock lock = new ReentrantLock();
		return new Guard(lock, lock.newCondition());
	}
	
	private Guard(Lock lock, Condition cond) {
		Preconditions.checkNotNullArgument(lock, "lock is null");
		Preconditions.checkNotNullArgument(cond, "Condition is null");
		
		m_lock = lock;
		m_cond = cond;
	}
	
	/**
	 * 본 Guard의 내부 lock을 획득한다.
	 * <p>
	 * 호출 쓰레드는 반드시 짝이 되는 {@link #unlock()}을 호출해 lock을 해제할 책임이 있다.
	 * 일반적인 사용 패턴은 try/finally 안에서 lock/unlock을 짝지어 호출하는 것이다.
	 * 더 안전한 사용을 원하면 {@link #run(Runnable)} / {@link #get(Supplier)} 등의
	 * 작업 실행 모드를 우선 검토한다.
	 */
	public void lock() {
		m_lock.lock();
	}

	/**
	 * 본 Guard의 내부 lock을 해제한다.
	 * <p>
	 * 반드시 lock을 획득한 쓰레드에서만 호출되어야 한다.
	 */
	public void unlock() {
		m_lock.unlock();
	}

	/**
	 * 대기 중인 모든 쓰레드에게 signal을 보낸다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출되어야 한다.
	 * 작업 실행 모드의 메소드들({@link #run}/{@link #get}/{@link #accept})은 finally에서
	 * 자동으로 호출하므로 일반적으로 사용자가 직접 호출할 필요는 없다.
	 */
	public void signalAll() {
		m_cond.signalAll();
	}

	/**
	 * 시그널을 받거나 interrupt될 때까지 대기한다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 */
	public void awaitSignal() throws InterruptedException {
		m_cond.await();
	}

	/**
	 * Guard가 signal을 받거나 주어진 제한 시각을 경과할 때까지 대기한다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 * 
	 * @param due	대기 제한 시각
	 * @return	대기 제한 시각이 경과하기 전에 signal을 받아 대기가 멈춘 경우는 {@code true},
	 * 			그렇지 않고 시간 제한으로 대기가 멈춘 경우는 {@code false}.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public boolean awaitSignal(Date due) throws InterruptedException {
		Preconditions.checkNotNullArgument(due, "due is null");
		
		return m_cond.awaitUntil(due);
	}
	
	/**
	 * Guard가 signal을 받거나 주어진 제한 시간이 도달할 때까지 대기한다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 *
	 * @param dur	대기 제한 기간
	 * @return	대기 제한 시각이 경과하기 전에 signal을 받아 대기가 멈춘 경우는 {@code true},
	 * 			그렇지 않고 시간 제한으로 대기가 멈춘 경우는 {@code false}.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public boolean awaitSignal(Duration dur) throws InterruptedException {
		Preconditions.checkNotNullArgument(dur, "duration is null");
		Preconditions.checkArgument(!dur.isZero() && !dur.isNegative(),
									"duration must be positive: %s", dur);

		return m_cond.awaitNanos(dur.toNanos()) > 0;
	}

	/**
	 * Guard가 signal을 받거나 주어진 nanosecond 단위 제한 시간이 경과할 때까지 대기한다.
	 * <p>
	 * 본 메소드는 반드시 lock을 획득한 상태에서 호출해야 한다.
	 *
	 * @param nanosTimeout	대기 제한 시간 (nanosecond)
	 * @return	{@link Condition#awaitNanos(long)}와 동일하게, 남은 대기 시간(nanos)을 반환한다.
	 * 			{@code 0} 이하이면 시간 제한으로 대기가 종료된 것으로 간주.
	 * @throws InterruptedException	대기 중에 현재 쓰레드가 interrupt된 경우.
	 */
	public long awaitSignalNanos(long nanosTimeout) throws InterruptedException {
		return m_cond.awaitNanos(nanosTimeout);
	}
	
	/**
	 * 사전 작업을 등록한 빌더를 반환한다.
	 * <p>
	 * 등록된 {@code action}은 lock을 획득한 직후 — 사전 조건을 검사하기 직전에 — 정확히
	 * 한 번 실행된다. {@code action}에서 발생한 예외(런타임/{@link Error})는 wrapping 없이
	 * 그대로 호출자에게 전파되며, lock은 정상적으로 해제된다. 즉 후속 {@code andRunChecked} /
	 * {@code andGetChecked}의 {@link ExecutionException} 래핑 정책은 사전 작업에는 적용되지 않는다.
	 *
	 * @param action	조건 검사 직전에 한 번 실행할 사전 작업
	 * @return	{@link PreAction} 빌더
	 */
	public PreAction preAction(Runnable action) {
		return new PreAction(this, action);
	}
	
	/**
	 * 주어진 조건이 만족될 때까지 무한 대기하는 {@link AwaitCondition} 빌더를 반환한다.
	 * <p>
	 * 반환된 빌더의 {@code andRun}/{@code andGet}/{@code andReturn} 등을 호출해야 실제로
	 * lock 획득과 대기가 시작된다. 사용 예:
	 * <pre>{@code
	 * String value = guard.awaitCondition(() -> data != null)
	 *                     .andGet(() -> consume());
	 * }</pre>
	 *
	 * @param condition	대기 조건. lock을 획득한 상태에서 평가된다.
	 * @return	{@link AwaitCondition} 빌더
	 */
	public AwaitCondition awaitCondition(Supplier<Boolean> condition) {
		return new AwaitCondition(this, null, condition);
	}

	/**
	 * 주어진 조건이 만족될 때까지 지정된 시각까지 대기하는 {@link TimedAwaitCondition} 빌더를 반환한다.
	 * <p>
	 * 주어진 시각이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
	 * 만일 {@code due}가 이미 과거 시각인 경우에는 condition이 만족하지 않으면
	 * 즉시 {@link TimeoutException}을 발생시킨다.
	 *
	 * @param condition	대기 조건. lock을 획득한 상태에서 평가된다.
	 * @param due		대기 제한 시각 (non-null)
	 * @return	{@link TimedAwaitCondition} 빌더
	 */
	public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, Date due) {
		return new TimedAwaitCondition(this, null, condition, due);
	}

	/**
	 * 주어진 조건이 만족될 때까지 지정된 기간 동안 대기하는 {@link TimedAwaitCondition} 빌더를 반환한다.
	 * <p>
	 * 기간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
	 * <p>
	 * 만일 {@code timeout.isZero()} 경우에는 condition이 만족하지 않으면
	 * 즉시 {@link TimeoutException}을 발생시킨다.
	 * timeout이 음수인 경우에는 {@link IllegalArgumentException}이 발생한다.
	 *
	 * @param condition	대기 조건. lock을 획득한 상태에서 평가된다.
	 * @param timeout	대기 제한 기간 ({@code 0} 이상)
	 * @return	{@link TimedAwaitCondition} 빌더
	 * @throws IllegalArgumentException	{@code timeout}이 {@code null}이거나 음수인 경우
	 */
	public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, Duration timeout) {
		return new TimedAwaitCondition(this, null, condition, timeout);
	}

	/**
	 * 주어진 조건이 만족될 때까지 지정된 기간 동안 대기하는 {@link TimedAwaitCondition} 빌더를 반환한다.
	 * <p>
	 * 기간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
	 * <p>
	 * 만일 timeout이 0인 경우에는 condition이 만족하지 않으면 즉시 {@link TimeoutException}을 발생시킨다.
	 * timeout이 음수인 경우에는 {@link IllegalArgumentException}이 발생한다.
	 *
	 * @param condition	대기 조건. lock을 획득한 상태에서 평가된다.
	 * @param timeout	대기 제한 기간 ({@code 0} 이상)
	 * @param unit		{@code timeout}의 시간 단위 (non-null)
	 * @return	{@link TimedAwaitCondition} 빌더
	 * @throws IllegalArgumentException	{@code timeout}이 음수이거나 {@code unit}이 {@code null}인 경우
	 */
	public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, long timeout, TimeUnit unit) {
		return new TimedAwaitCondition(this, null, condition, timeout, unit);
	}
	
	/**
	 * lock을 획득한 상태에서 주어진 작업을 수행한다.
	 * <p>
	 * 작업 수행 도중 발생한 예외는 호출자에게 그대로 전파된다.
	 * 정상/예외 종료 모두 finally 블록에서 모든 대기 쓰레드에게 signal을 보내고 lock을 해제한다.
	 *
	 * @param work	수행할 작업 (non-null)
	 * @throws IllegalArgumentException	{@code work}가 {@code null}인 경우
	 */
	public void run(Runnable work) {
		Preconditions.checkNotNullArgument(work, "work is null");

		m_lock.lock();
		try {
			work.run();
		}
		finally {
			m_cond.signalAll();
			m_lock.unlock();
		}
	}

	/**
	 * lock을 획득한 상태에서 checked 예외를 던질 수 있는 작업을 수행한다.
	 * <p>
	 * 작업이 던지는 checked 예외는 wrapping 없이 그대로 호출자에게 전파된다.
	 * 정상/예외 종료 모두 finally에서 signal과 lock 해제가 보장된다.
	 *
	 * @param <X>	작업이 던지는 checked 예외 타입
	 * @param work	수행할 작업 (non-null)
	 * @throws X	작업 수행 중 발생한 예외
	 * @throws IllegalArgumentException	{@code work}가 {@code null}인 경우
	 */
	public <X extends Throwable> void runChecked(CheckedRunnableX<X> work) throws X {
		Preconditions.checkNotNullArgument(work, "work is null");

		m_lock.lock();
		try {
			work.run();
		}
		finally {
			m_cond.signalAll();
			m_lock.unlock();
		}
	}

	/**
	 * lock을 획득한 상태에서 주어진 supplier를 실행하고 그 결과를 반환한다.
	 * <p>
	 * 정상/예외 종료 모두 finally에서 signal과 lock 해제가 보장된다.
	 *
	 * @param <T>	반환 값의 타입
	 * @param suppl	값을 공급할 supplier
	 * @return		supplier가 반환한 값
	 */
	public <T> T get(Supplier<T> suppl) {
		m_lock.lock();
		try {
			T value = suppl.get();
			return value;
		}
		finally {
			m_cond.signalAll();
			m_lock.unlock();
		}
	}

	/**
	 * lock을 획득한 상태에서 checked 예외를 던질 수 있는 supplier를 실행하고 그 결과를 반환한다.
	 * <p>
	 * supplier가 던지는 checked 예외는 wrapping 없이 그대로 호출자에게 전파된다.
	 * 정상/예외 종료 모두 finally에서 signal과 lock 해제가 보장된다.
	 *
	 * @param <T>	반환 값의 타입
	 * @param <X>	supplier가 던지는 checked 예외 타입
	 * @param suppl	값을 공급할 supplier
	 * @return		supplier가 반환한 값
	 * @throws X	supplier 수행 중 발생한 예외
	 */
	public <T,X extends Throwable> T getChecked(CheckedSupplierX<T,X> suppl) throws X {
		m_lock.lock();
		try {
			T value = suppl.get();
			return value;
		}
		finally {
			m_cond.signalAll();
			m_lock.unlock();
		}
	}

	/**
	 * lock을 획득한 상태에서 주어진 consumer에 값을 전달해 실행한다.
	 * <p>
	 * 정상/예외 종료 모두 finally에서 signal과 lock 해제가 보장된다.
	 *
	 * @param <T>		값의 타입
	 * @param consumer	값을 받아 처리할 consumer
	 * @param value		consumer에 전달할 값
	 */
	public <T> void accept(Consumer<T> consumer, T value) {
		m_lock.lock();
		try {
			consumer.accept(value);
		}
		finally {
			m_cond.signalAll();
			m_lock.unlock();
		}
	}

	/**
	 * lock을 획득한 상태에서 checked 예외를 던질 수 있는 consumer에 값을 전달해 실행한다.
	 * <p>
	 * consumer가 던지는 checked 예외는 wrapping 없이 그대로 호출자에게 전파된다.
	 * 정상/예외 종료 모두 finally에서 signal과 lock 해제가 보장된다.
	 *
	 * @param <T>		값의 타입
	 * @param <X>		consumer가 던지는 checked 예외 타입
	 * @param consumer	값을 받아 처리할 consumer
	 * @param value		consumer에 전달할 값
	 * @throws X		consumer 수행 중 발생한 예외
	 */
	public <T,X extends Throwable> void acceptChecked(CheckedConsumerX<T,X> consumer, T value) throws X {
		m_lock.lock();
		try {
			consumer.accept(value);
		}
		finally {
			m_cond.signalAll();
			m_lock.unlock();
		}
	}
	
	/**
	 * lock 획득 직후 한 번 실행할 사전 작업을 담은 빌더.
	 * <p>
	 * {@link Guard#preAction(Runnable)}로 생성되며 후속 {@code awaitCondition(...)} 체이닝을 통해
	 * {@link AwaitCondition} 또는 {@link TimedAwaitCondition}을 만든다. 사전 작업은 lock 획득 후
	 * 사전 조건 검사 직전에 정확히 한 번 실행된다.
	 * <p>
	 * 사용 예 — 대기 시작 전 신호를 한 번 발송하는 패턴:
	 * <pre>{@code
	 * guard.preAction(() -> stateChanged())
	 *      .awaitCondition(() -> done)
	 *      .andReturn();
	 * }</pre>
	 */
	public static class PreAction {
		protected final Guard m_guard;
		private final Runnable m_action;

		PreAction(Guard guard, Runnable action) {
			Preconditions.checkNotNullArgument(guard, "Guard is null");
			Preconditions.checkNotNullArgument(action, "action is null");

	        m_guard = guard;
	        m_action = action;
		}

		/**
		 * 사전 조건을 추가하여 무한 대기하는 {@link AwaitCondition} 빌더를 반환한다.
		 *
		 * @param condition	대기 조건
		 * @return	{@link AwaitCondition} 빌더
		 */
		public AwaitCondition awaitCondition(Supplier<Boolean> condition) {
			return new AwaitCondition(m_guard, m_action, condition);
		}

		/**
		 * 사전 조건을 추가하고 지정 시각까지 대기하는 {@link TimedAwaitCondition} 빌더를 반환한다.
		 *
		 * @param condition	대기 조건
		 * @param due		대기 제한 시각
		 * @return	{@link TimedAwaitCondition} 빌더
		 */
		public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, Date due) {
			return new TimedAwaitCondition(m_guard, m_action, condition, due);
		}

		/**
		 * 사전 조건을 추가하고 지정 기간 동안 대기하는 {@link TimedAwaitCondition} 빌더를 반환한다.
		 *
		 * @param condition	대기 조건
		 * @param timeout	대기 제한 기간 (양수)
		 * @return	{@link TimedAwaitCondition} 빌더
		 */
		public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, Duration timeout) {
			return new TimedAwaitCondition(m_guard, m_action, condition, timeout);
		}

		/**
		 * 사전 조건을 추가하고 지정 기간 동안 대기하는 {@link TimedAwaitCondition} 빌더를 반환한다.
		 *
		 * @param condition	대기 조건
		 * @param timeout	대기 제한 기간 (양수)
		 * @param unit		{@code timeout}의 시간 단위
		 * @return	{@link TimedAwaitCondition} 빌더
		 */
		public TimedAwaitCondition awaitCondition(Supplier<Boolean> condition, long timeout, TimeUnit unit) {
			return new TimedAwaitCondition(m_guard, m_action, condition, timeout, unit);
		}
	}
	
	/**
	 * 사전 조건이 만족할 때까지 무한 대기하는 빌더.
	 * <p>
	 * {@link Guard#awaitCondition(Supplier)} 또는 {@link PreAction#awaitCondition(Supplier)}으로 생성된다.
	 * 종결 메소드 ({@code andReturn} / {@code andRun} / {@code andRunChecked} / {@code andGet} /
	 * {@code andGetChecked}) 중 하나를 호출해야 실제 lock 획득과 대기가 시작된다.
	 * <p>
	 * 종결 메소드 공통 동작:
	 * <ol>
	 *   <li>lock 획득.</li>
	 *   <li>사전 작업이 있으면 정확히 한 번 실행. 실패 시 signal 후 예외 전파.</li>
	 *   <li>{@code precondition}이 {@code true}가 될 때까지 {@code awaitSignal()}로 대기.</li>
	 *   <li>지정된 작업 수행. 정상/예외 종료 모두 finally에서 signal과 lock 해제.</li>
	 * </ol>
	 */
	public static final class AwaitCondition {
		protected final Guard m_guard;
		private final Runnable m_preAction;
		private final Supplier<Boolean> m_precondition;

		AwaitCondition(Guard guard, Runnable preAction, Supplier<Boolean> precondition) {
			Preconditions.checkNotNullArgument(guard, "Guard is null");
			Preconditions.checkNotNullArgument(precondition, "precondition is null");

	        m_guard = guard;
			m_preAction = preAction;
	        m_precondition = precondition;
		}

		/**
		 * 사전 조건이 만족될 때까지 대기한 후 반환한다.
		 *
		 * @throws InterruptedException	대기 중에 쓰레드가 인터럽트된 경우
		 */
		public void andReturn() throws InterruptedException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * 
		 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
		 */
		public void andRun(Runnable task) throws InterruptedException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();

				try {
					task.run();
				}
				finally {
					m_guard.signalAll();
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link ExecutionException}을 발생시킨다.
		 *
		 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws ExecutionException 작업 ({@code work}) 수행 중 예외가 발생한 경우.
		 */
		public void andRunChecked(CheckedRunnable task) throws InterruptedException, ExecutionException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();

				try {
					task.run();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					throw e;
				}
				catch ( Throwable e ) {
					Throwable cause = Throwables.unwrapThrowable(e);
					throw new ExecutionException(cause);
				}
				finally {
					m_guard.signalAll();
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 *
		 * @return 작업의 결과 값.
		 * @throws InterruptedException	대기 과정 중에 쓰레드가 중단된 경우.
		 */
		public <T> T andGet(Supplier<T> supplier) throws InterruptedException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();
				
				try {
					return supplier.get();
				}
				finally {
					m_guard.signalAll();
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link ExecutionException}을 발생시킨다.
		 *
		 * @return 작업의 결과 값.
		 * @throws InterruptedException	대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws ExecutionException 작업 ({@code supplier}) 수행 중 예외가 발생한 경우.
		 */
		public <T> T andGetChecked(CheckedSupplier<T> supplier) throws InterruptedException, ExecutionException {
			m_guard.lock();
			try {
				awaitConditionSatisfied();

				try {
					return supplier.get();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					throw e;
				}
				catch ( Throwable e ) {
					Throwable cause = Throwables.unwrapThrowable(e);
					throw new ExecutionException(cause);
				}
				finally {
					m_guard.signalAll();
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 사전 조건이 만족할 때까지 대기한다.
		 * <p>
		 * {@code m_precondition}이 {@code true}가 될 때까지 대기한다.
		 * 본 메소드는 {@link Guard} 객체의 lock을 획득한 상태에서 호출되어야 한다.
		 * 
		 * @throws InterruptedException	대기 중에 interrupt가 발생한 경우.
		 */
		private void awaitConditionSatisfied() throws InterruptedException {
			if ( m_preAction != null ) {
				try {
					m_preAction.run();
				}
				catch ( RuntimeException | Error t ) {
					// preAction 실패 시 다른 대기 쓰레드들에게 상태 변경을 통보한 뒤 예외를 그대로 전파.
					m_guard.signalAll();
					throw t;
				}
			}
			
			while ( !m_precondition.get() ) {
				m_guard.awaitSignal();
			}
		}
	}
	
	/**
	 * 사전 조건이 만족할 때까지 시간 제한이 있는 대기 빌더.
	 * <p>
	 * {@link Guard#awaitCondition(Supplier, Date)}, {@link Guard#awaitCondition(Supplier, Duration)},
	 * {@link Guard#awaitCondition(Supplier, long, TimeUnit)} 또는 {@link PreAction}의 동등한 메소드로
	 * 생성된다. 종결 메소드를 호출해야 실제 lock 획득과 대기가 시작된다.
	 * <p>
	 * {@link AwaitCondition}과의 차이점은 시각/기간 만료 시 {@link TimeoutException}이 발생한다는 점이다.
	 * {@link #andReturn()}만 예외 대신 {@code false}를 반환한다.
	 */
	public static final class TimedAwaitCondition {
		protected final Guard m_guard;
		private final Runnable m_preAction;
		private final Supplier<Boolean> m_precondition;
		private final @Nullable Date m_due;
		private final @Nullable Duration m_timeout;

		TimedAwaitCondition(Guard guard, Runnable preAction, Supplier<Boolean> precondition, Date due) {
			Preconditions.checkNotNullArgument(guard, "Guard is null");
			Preconditions.checkNotNullArgument(precondition, "Precondition is null");
			Preconditions.checkNotNullArgument(due, "Due is null");
			
	        m_guard = guard;
	        m_preAction = preAction;
	        m_precondition = precondition;
	        m_due = due;
	        m_timeout = null;
		}
		
		TimedAwaitCondition(Guard guard, Runnable preAction, Supplier<Boolean> precondition,
							Duration timeout) {
			Preconditions.checkNotNullArgument(guard, "Guard is null");
			Preconditions.checkNotNullArgument(precondition, "Precondition is null");
			Preconditions.checkNotNullArgument(timeout, "Timeout is null");
			Preconditions.checkArgument(!timeout.isNegative(), "Timeout is negative");
			
	        m_guard = guard;
	        m_preAction = preAction;
	        m_precondition = precondition;
	        m_due = null;
	        m_timeout = timeout;
		}
		
		TimedAwaitCondition(Guard guard, Runnable preAction, Supplier<Boolean> precondition, long timeout,
							TimeUnit unit) {
			Preconditions.checkNotNullArgument(guard, "Guard is null");
			Preconditions.checkNotNullArgument(precondition, "Precondition is null");
			Preconditions.checkArgument(timeout >= 0, "Timeout must be non-negative: %d", timeout);
			Preconditions.checkNotNullArgument(unit, "TimeUnit is null");
			
	        m_guard = guard;
	        m_preAction = preAction;
	        m_precondition = precondition;
	        m_due = null;
	        m_timeout = Duration.ofNanos(unit.toNanos(timeout));
		}
	
		/**
		 * 사전 조건이 만족될 때까지 시간 제한 안에서 대기한다.
		 * <p>
		 * 다른 종결 메소드와 달리 timeout 시 {@link TimeoutException}을 던지지 않고 {@code false}를 반환한다.
		 *
		 * @return	조건이 만족되어 정상 종료되면 {@code true}, timeout으로 종료되면 {@code false}
		 * @throws InterruptedException	대기 중에 쓰레드가 인터럽트된 경우
		 */
		public boolean andReturn() throws InterruptedException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();
				return true;
			}
			catch ( TimeoutException e ) {
				return false;
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 *
		 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws TimeoutException 대기 제한 시각을 경과한 경우.
		 */
		public void andRun(Runnable task) throws InterruptedException, TimeoutException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();
	
				try {
					task.run();
				}
				finally {
					m_guard.signalAll();
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link ExecutionException}을 발생시킨다.
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 *
		 * @throws InterruptedException 대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws TimeoutException 대기 제한 시각을 경과한 경우.
		 * @throws ExecutionException 작업 ({@code work}) 수행 중 예외가 발생한 경우.
		 */
		public void andRunChecked(CheckedRunnable task) throws InterruptedException, TimeoutException, ExecutionException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();

				try {
					task.run();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					throw e;
				}
				catch ( Throwable e ) {
					Throwable cause = Throwables.unwrapThrowable(e);
					throw new ExecutionException(cause);
				}
				finally {
					m_guard.signalAll();
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 *
		 * @return 작업의 결과 값.
		 * @throws InterruptedException	대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws TimeoutException 대기 제한 시각을 경과한 경우.
		 */
		public <T> T andGet(Supplier<T> supplier) throws InterruptedException, TimeoutException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();
				
				try {
					return supplier.get();
				}
				finally {
					m_guard.signalAll();
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 주어진 사전 조건이 만족할 때까지 대기하고 이 조건이 만족한 경우 주어진 작업을 실행하고,
		 * 필요한 경우 대기 쓰레드들에게 signal을 보낸다.
		 * <p>
		 * 주어진 작업은 내부 lock을 획득한 상태에서 실행되고, 작업 수행 중 예외가 발생한 경우
		 * {@link ExecutionException}을 발생시킨다.
		 * 대기 제한 시간이 경과한 경우에는 대기가 종료되고 {@link TimeoutException}을 발생시킨다.
		 *
		 * @return 작업의 결과 값.
		 * @throws InterruptedException	대기 과정 중에 쓰레드가 중단된 경우.
		 * @throws TimeoutException 대기 제한 시각을 경과한 경우.
		 * @throws ExecutionException 작업 ({@code supplier}) 수행 중 예외가 발생한 경우.
		 */
		public <T> T andGetChecked(CheckedSupplier<T> supplier) throws InterruptedException, TimeoutException, ExecutionException {
			m_guard.lock();
			try {
				awaitPreconditionSatisfied();

				try {
					return supplier.get();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					throw e;
				}
				catch ( Throwable e ) {
					Throwable cause = Throwables.unwrapThrowable(e);
					throw new ExecutionException(cause);
				}
				finally {
					m_guard.signalAll();
				}
			}
			finally {
				m_guard.unlock();
			}
		}
		
		/**
		 * 사전 조건이 만족할 때까지 대기한다.
		 * <p>
		 * 사전 조건이 없는 경우에는 바로 반환한다.
		 * 본 메소드는 {@link Guard} 객체의 lock을 획득한 상태에서 호출되어야 한다.
		 * 
		 * @throws TimeoutException	사전 조건 대기 시간이 경과한 경우.
		 * @throws InterruptedException	대기 중에 interrupt가 발생한 경우.
		 */
		private void awaitPreconditionSatisfied() throws InterruptedException, TimeoutException {
			if ( m_preAction != null ) {
				try {
					m_preAction.run();
				}
				catch ( RuntimeException | Error t ) {
					// preAction 실패 시 다른 대기 쓰레드들에게 상태 변경을 통보한 뒤 예외를 그대로 전파.
					m_guard.signalAll();
					throw t;
				}
			}

			try {
				if ( m_due != null ) {
					// 절대 시각 기반 대기.
					while ( !m_precondition.get() ) {
						if ( !m_guard.awaitSignal(m_due) ) {
							throw new TimeoutException(String.format("due=%s", m_due));
						}
					}
				}
				else {
					// 상대 기간 기반 대기. nanos 단위로 카운트다운.
					long remainingNanos = m_timeout.toNanos();
					while ( !m_precondition.get() ) {
						if ( remainingNanos <= 0 ) {
							throw new TimeoutException(String.format("timeout=%s", m_timeout));
						}
						remainingNanos = m_guard.awaitSignalNanos(remainingNanos);
					}
				}
			}
			catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
				throw e;
			}
		}
	}
	
}
