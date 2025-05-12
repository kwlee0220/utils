package utils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import utils.func.CheckedRunnable;
import utils.func.CheckedSupplier;


/**
 * {@code StateChangePoller}는 주어진 목표 상태가 될 때까지 주기적으로 확인하는 기능을
 * 수행한다.
 * 목표 상태 도달 여부는 인자로 제공되는  {@code statePredicate}를 사용한다.
 * 만일 주어진 제한 시간 내에 목표 상태가 되지 않는 경우에는 {@code TimeoutException} 예외가
 * 발생한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StateChangePoller implements CheckedRunnable, LoggerSettable {
	private final Logger s_logger = LoggerFactory.getLogger(StateChangePoller.class);
	
	private final CheckedSupplier<Boolean> m_endOfPollingPredicate;
	private Duration m_pollInterval;
	@Nullable private Duration m_timeout = null;
	@Nullable private Instant m_due = null;
	private Logger m_logger;
	
	private StateChangePoller(Builder builder) {
		Preconditions.checkArgument(builder.m_endOfPollingPredicate != null, "endOfPollingPredicate is not set");
		Preconditions.checkArgument(builder.m_pollInterval != null, "pollInterval is not set");
		
		m_endOfPollingPredicate = builder.m_endOfPollingPredicate;
		m_pollInterval = builder.m_pollInterval;
		m_timeout = builder.m_timeout;
		m_due = builder.m_due;
		
		m_logger = s_logger;
	}
	
	/**
	 * Polling 간격을 반환합니다.
	 *
	 * @return 폴링 간격의 Duration 객체
	 */
	public Duration getPollingInterval() {
		return m_pollInterval;
	}
	
	/**
	 * 실행 제한 시간을 반환합니다.
	 *
	 * @return 설정된 실행 제한 시간의 Duration 객체, 설정되지 않은 경우 null
	 */
	public Duration getTimeout() {
		return m_timeout;
	}
	
	/**
     * Polling 제한 시각을 반환합니다.
     * 
     * @return 설정된 Polling 제한 시각의 Instant 객체, 설정되지 않은 경우 null
     */
	public Instant getDue() {
		return m_due;
	}

	/**
	 * Polling을 수행한다.
	 * <p>
	 * Polling이 시작되면 주어진 목표 상태가 될 때까지 주기적으로 상태를 확인한다.
	 * 상태 확인은 {@code m_endOfPollingPredicate}를 호출하여 그 반환 값이 {@code true}가 될 때 까지 반복한다.
	 * 만일 Polling 제한 시간이 설정되어 있으면 그 시간이 지나면 {@code TimeoutException} 예외가 발생한다.
	 *
	 * @throws TimeoutException     Polling 제한 시간이 지난 경우
	 * @throws InterruptedException Polling 대기 과정 중에 쓰레드가 중단된 경우
	 * @throws RuntimeExecutionException Polling 확인 과정에서 예외가 발생한 경우
	 */
	@Override
	public void run() throws TimeoutException, InterruptedException, RuntimeExecutionException {
		Preconditions.checkState(m_endOfPollingPredicate != null, "Poller is not set");
		Preconditions.checkState(m_pollInterval != null, "Sample interval is not set");

		Instant due;
		if ( m_due != null ) {
			due = m_due;
		}
		else if ( m_timeout != null ) {
			due = Instant.now().plus(m_timeout);
		}
		else {
			due = null;
		}
		
		while ( true ) {
			if ( getLogger().isDebugEnabled() ) {
				getLogger().debug("polling started");
			}
			
			try {
				boolean endOfPolling = m_endOfPollingPredicate.get();
				if ( endOfPolling ) {
					if ( getLogger().isInfoEnabled() ) {
						getLogger().info("polling has finished");
					}
					return;
				}
				if ( getLogger().isDebugEnabled() ) {
					getLogger().debug("waiting for the next polling: interval={}", m_pollInterval);
				}
			}
			catch ( Throwable e ) {
				Throwable cause = Throwables.unwrapThrowable(e);
				throw new RuntimeExecutionException(cause);
			}
			
			if ( due != null ) {
				Instant done = Instant.now();
				if ( m_timeout != null && due.compareTo(done) < 0 ) {
					throw new TimeoutException("timeout=" + m_timeout);
				}
			}
			else {
				TimeUnit.MILLISECONDS.sleep(m_pollInterval.toMillis());
			}
		}
	}

	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = (logger != null) ? logger : s_logger;
	}

	/**
	 * Polling을 수행할 {@code StateChangePoller} 객체를 생성한다.
	 * <p>
	 * Polling 종료 조건은 주어진 함수가 {@code false}를 반환할 때 Polling을 종료한다.
	 *
	 * @param predicate Polling 유지 조건을 판단하는 함수
	 * @return 생성된 StateChangePoller 객체
	 */
	public static Builder pollWhile(CheckedSupplier<Boolean> predicate) {
		return new Builder(() -> !predicate.get());
	}
	
	/**
	 * Polling을 수행할 {@code StateChangePoller} 객체를 생성한다.
	 * <p>
	 * Polling 종료 조건은 주어진 함수가 {@code true}를 반환할 때 Polling을 종료한다.
	 *
	 * @param predicate Polling 종료 조건을 판단하는 함수
	 * @return 생성된 StateChangePoller 객체
	 */
	public static Builder pollUntil(CheckedSupplier<Boolean> predicate) {
		return new Builder(predicate);
	}
	
	public static class Builder {
		private final CheckedSupplier<Boolean> m_endOfPollingPredicate;
		private Duration m_pollInterval;
		@Nullable private Duration m_timeout = null;
		@Nullable private Instant m_due = null;
		
		private Builder(CheckedSupplier<Boolean> endOfPollingPredicate) {
			Preconditions.checkArgument(endOfPollingPredicate != null, "endOfPollingPredicate is null");
			m_endOfPollingPredicate = endOfPollingPredicate;
		}
		
		/**
		 * Polling을 수행할 {@code StateChangePoller} 객체를 생성한다.
		 *
		 * @return 생성된 StateChangePoller 객체
		 */
		public StateChangePoller build() {
			return new StateChangePoller(this);
		}
		
		/**
		 * Polling 간격을 설정한다.
		 *
		 * @param interval polling 시간 간격
		 * @return Builder 객체
		 */
		public Builder pollInterval(Duration interval) {
			m_pollInterval = interval;
			return this;
		}
		
		/**
		 * Polling 제한 기간을 설정한다.
		 *
		 * @param timeout Polling 제한 기간
		 * @return Builder 객체
		 */
		public Builder timeout(@Nullable Duration timeout) {
			m_timeout = timeout;
			return this;
		}
		
		/**
         * Polling 제한 시각을 설정한다.
         *
         * @param due Polling 제한 시각
         * @return Builder 객체
         */
		public Builder due(@Nullable Instant due) {
			m_due = due;
			return this;
		}
	}
}
