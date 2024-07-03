package utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import utils.func.CheckedRunnable;
import utils.func.CheckedSupplier;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StateChangePoller implements CheckedRunnable, LoggerSettable {
	private final Logger s_logger = LoggerFactory.getLogger(StateChangePoller.class);
	
	private CheckedSupplier<Boolean> m_stayPredicate;
	private Duration m_pollInterval;
	private Optional<Duration> m_timeout = Optional.empty();
	private Logger m_logger;
	
	private StateChangePoller(Builder builder) {
		m_stayPredicate = builder.m_stayPredicate;
		m_pollInterval = builder.m_pollInterval;
		m_timeout = builder.m_timeout;
		
		m_logger = s_logger;
	}
	
	public void setEndOfPollingPredicate(CheckedSupplier<Boolean> pred) {
		m_stayPredicate = pred;
	}
	
	public Duration getPollingInterval() {
		return m_pollInterval;
	}
	
	public Optional<Duration> getTimeout() {
		return m_timeout;
	}

	@Override
	public void run() throws TimeoutException, InterruptedException, ExecutionException {
		Preconditions.checkState(m_stayPredicate != null, "Poller is not set");
		Preconditions.checkState(m_pollInterval != null, "Sample interval is not set");
		
		Instant started = Instant.now();
		while ( true ) {
			if ( getLogger().isDebugEnabled() ) {
				getLogger().debug("polling started");
			}
			
			try {
				boolean endOfPolling = !m_stayPredicate.get();
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
				throw new ExecutionException(e);
			}
			
			if ( m_timeout.isPresent() ) {
				Duration elapsed = Duration.between(started, Instant.now());
				if ( elapsed.compareTo(m_timeout.get()) >= 0 ) {
					throw new TimeoutException("elapsed=" + elapsed);
				}
			}
			TimeUnit.MILLISECONDS.sleep(m_pollInterval.toMillis());
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

	public static Builder pollWhile(CheckedSupplier<Boolean> cond) {
		return new Builder(cond);
	}
	public static Builder pollUntil(CheckedSupplier<Boolean> cond) {
		return new Builder(() -> !cond.get());
	}
	
	public static class Builder {
		private CheckedSupplier<Boolean> m_stayPredicate;
		private Duration m_pollInterval;
		private Optional<Duration> m_timeout = Optional.empty();
		
		private Builder(CheckedSupplier<Boolean> predicate) {
			m_stayPredicate = predicate;
		}
		
		public StateChangePoller build() {
			return new StateChangePoller(this);
		}
		
		public Builder interval(Duration interval) {
			m_pollInterval = interval;
			return this;
		}
		
		public Builder timeout(@Nullable Duration timeout) {
			m_timeout = (timeout != null) ? Optional.of(timeout) : Optional.empty();
			return this;
		}
		
		public Builder timeout(Optional<Duration> timeout) {
			m_timeout = timeout;
			return this;
		}
	}
}
