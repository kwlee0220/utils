package utils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;


public class StopWatch {
	private long m_started;
	private long m_finished;
	private final AtomicBoolean m_working = new AtomicBoolean(false);
	
	public static StopWatch start() {
		StopWatch watch = new StopWatch();
		watch.m_started = System.currentTimeMillis();
		watch.m_working.set(true);
		return watch;
	}
	
	public boolean isWorking() {
		return m_working.get();
	}
	
	public void restart() {
		m_working.set(true);
		m_started = System.currentTimeMillis();
	}
	
	public void stop() {
		m_working.set(false);
		m_finished = System.currentTimeMillis();
	}
	
	public long stopInMillis() {
		m_working.set(false);
		return (m_finished = System.currentTimeMillis()) - m_started;
	}
	
	public long stopInSeconds() {
		return stopInMillis() / 1000;
	}
	
	public Duration getElapsed() {
		return Duration.ofMillis(getElapsedInMillis());
	}
	
	public long getElapsedInMillis() {
		if ( m_working.get() ) {
			return System.currentTimeMillis() - m_started;
		}
		else {
			return m_finished - m_started;
		}
	}
	
	public long getElapsedInMillis(int count) {
		return getElapsedInMillis()/count;
	}
	
	public long getElapsedInSeconds() {
		return getElapsedInMillis() / 1000;
	}
	
	public double getElapsedInFloatingSeconds() {
		return getElapsedInMillis() / 1000.0;
	}
	
	public double getElapsedInFloatingSeconds(int count) {
		return getElapsedInFloatingSeconds() / count;
	}
	
	public double getRatesInSecond(int count) {
		return count / getElapsedInFloatingSeconds();
	}
	
	public String stopAndGetElpasedTimeString() {
		stop();
		return getElapsedMillisString();
	}
	
	public String getElapsedMillisString() {
		return UnitUtils.toMillisString(getElapsedInMillis());
	}
	
	public String getElapsedSecondString() {
		return UnitUtils.toSecondString(getElapsedInMillis());
	}
}
