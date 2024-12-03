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
	
	private CheckedSupplier<Boolean> m_goalStatePredicate;
	private Duration m_pollInterval;
	@Nullable private Duration m_timeout = null;
	@Nullable private Instant m_due = null;
	private Logger m_logger;
	
	private StateChangePoller(Builder builder) {
		Preconditions.checkNotNull(builder.m_statePredicate);
		Preconditions.checkNotNull(builder.m_pollInterval);
		
		m_goalStatePredicate = builder.m_statePredicate;
		m_pollInterval = builder.m_pollInterval;
		m_timeout = builder.m_timeout;
		m_due = builder.m_due;
		
		m_logger = s_logger;
	}
	
	public Duration getPollingInterval() {
		return m_pollInterval;
	}
	
	public Duration getTimeout() {
		return m_timeout;
	}
	
	public Instant getDue() {
		return m_due;
	}

	@Override
	public void run() throws TimeoutException, InterruptedException, ExecutionRuntimeExecution {
		Preconditions.checkState(m_goalStatePredicate != null, "Poller is not set");
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
				boolean endOfPolling = !m_goalStatePredicate.get();
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
				throw new ExecutionRuntimeExecution(cause);
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

	public static Builder pollWhile(CheckedSupplier<Boolean> stayStatePredicate) {
		return new Builder(stayStatePredicate);
	}
	public static Builder pollUntil(CheckedSupplier<Boolean> goalStatePredicate) {
		return new Builder(() -> !goalStatePredicate.get());
	}
	
	public static class Builder {
		private CheckedSupplier<Boolean> m_statePredicate;
		private Duration m_pollInterval;
		@Nullable private Duration m_timeout = null;
		@Nullable private Instant m_due = null;
		
		private Builder(CheckedSupplier<Boolean> predicate) {
			m_statePredicate = predicate;
		}
		
		public StateChangePoller build() {
			return new StateChangePoller(this);
		}
		
		public Builder pollInterval(Duration interval) {
			m_pollInterval = interval;
			return this;
		}
		
		public Builder timeout(@Nullable Duration timeout) {
			m_timeout = timeout;
			return this;
		}
		
		public Builder due(@Nullable Instant due) {
			m_due = due;
			return this;
		}
	}
}
