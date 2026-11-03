package utils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * 경과 시간을 밀리초 단위로 재는 간단한 스톱워치.
 * <p>
 * 시각은 {@link System#currentTimeMillis()}로 얻는다. 동작 여부만 {@link java.util.concurrent.atomic.AtomicBoolean}으로
 * 관리하고 시작/종료 시각 필드는 동기화하지 않으므로, 여러 스레드가 함께 조작하는 용도로는 적합하지 않다.
 */
public class StopWatch {
	private long m_started;
	private long m_finished;
	private final AtomicBoolean m_working;
	
	/**
	 * 시작하지 않은 스톱워치를 만든다.
	 * <p>
	 * <b>주의:</b> 시작 시각이 0으로 남아 있으므로, {@link #restart()} 없이 {@link #stop()}을 부르면
	 * 경과 시간이 1970-01-01 이후의 전체 밀리초가 된다. 시작 전에는 경과 시간이 0이다.
	 *
	 * @return	생성된 스톱워치.
	 */
	public static StopWatch create() {
		return new StopWatch();
	}
	
	/**
	 * 스톱워치를 만들고 현재 시각부터 바로 측정을 시작한다.
	 *
	 * @return	측정 중인 스톱워치.
	 */
	public static StopWatch start() {
		StopWatch watch = create();
		watch.restart();
		return watch;
	}
	
	/**
	 * 주어진 시각을 시작 시각으로 하여 측정 중인 스톱워치를 만든다.
	 *
	 * @param startMillis	시작 시각 ({@link System#currentTimeMillis()} 기준 epoch 밀리초).
	 * @return	측정 중인 스톱워치.
	 */
	public static StopWatch start(long startMillis) {
		StopWatch watch = create();
		watch.m_started = startMillis;
		watch.m_working.set(true);
		
		return watch;
	}
	
	private StopWatch() {
		m_working = new AtomicBoolean(false);
	}
	
	/**
	 * 측정 중인지 여부를 반환한다.
	 *
	 * @return	측정 중이면 {@code true}, 시작 전이거나 정지되었으면 {@code false}.
	 */
	public boolean isWorking() {
		return m_working.get();
	}
	
	/**
	 * 현재 시각을 시작 시각으로 하여 측정을 (다시) 시작한다. 이전 측정 결과는 버린다.
	 */
	public void restart() {
		m_working.set(true);
		m_started = System.currentTimeMillis();
	}
	
	/**
	 * 측정을 멈추고 현재 시각을 종료 시각으로 기록한다.
	 * 이미 정지된 상태에서 다시 부르면 종료 시각이 현재 시각으로 갱신된다.
	 */
	public void stop() {
		m_working.set(false);
		m_finished = System.currentTimeMillis();
	}
	
	/**
	 * 측정을 멈추고 경과 시간을 밀리초 단위로 반환한다.
	 * {@link #stop()}과 마찬가지로 부를 때마다 종료 시각이 현재 시각으로 갱신된다.
	 *
	 * @return	경과 시간 (밀리초).
	 */
	public long stopInMillis() {
		m_working.set(false);
		return (m_finished = System.currentTimeMillis()) - m_started;
	}
	
	/**
	 * 측정을 멈추고 경과 시간을 초 단위로 반환한다. 1초 미만은 버린다.
	 *
	 * @return	경과 시간 (초).
	 */
	public long stopInSeconds() {
		return stopInMillis() / 1000;
	}
	
	/**
	 * 경과 시간을 {@link Duration}으로 반환한다.
	 * 측정 중이면 현재 시각까지, 정지되었으면 종료 시각까지의 시간이다.
	 *
	 * @return	경과 시간.
	 */
	public Duration getElapsed() {
		return Duration.ofMillis(getElapsedInMillis());
	}
	
	/**
	 * 경과 시간을 밀리초 단위로 반환한다.
	 * 측정 중이면 현재 시각까지, 정지되었으면 종료 시각까지의 시간이다.
	 *
	 * @return	경과 시간 (밀리초).
	 */
	public long getElapsedInMillis() {
		if ( m_working.get() ) {
			return System.currentTimeMillis() - m_started;
		}
		else {
			return m_finished - m_started;
		}
	}
	
	/**
	 * 경과 시간을 {@code count}로 나눈 값, 즉 한 번당 평균 경과 시간을 밀리초 단위로 반환한다.
	 * 정수 나눗셈이므로 나머지는 버린다.
	 *
	 * @param count	나눌 횟수.
	 * @return	회당 평균 경과 시간 (밀리초).
	 * @throws ArithmeticException	{@code count}가 0인 경우.
	 */
	public long getElapsedInMillis(int count) {
		return getElapsedInMillis()/count;
	}
	
	/**
	 * 경과 시간을 초 단위로 반환한다. 1초 미만은 버린다.
	 *
	 * @return	경과 시간 (초).
	 */
	public long getElapsedInSeconds() {
		return getElapsedInMillis() / 1000;
	}
	
	/**
	 * 경과 시간을 소수점이 있는 초 단위로 반환한다.
	 *
	 * @return	경과 시간 (초, 밀리초 정밀도).
	 */
	public double getElapsedInFloatingSeconds() {
		return getElapsedInMillis() / 1000.0;
	}
	
	/**
	 * 경과 시간(초)을 {@code count}로 나눈 값, 즉 한 번당 평균 경과 시간을 반환한다.
	 * 부동소수 나눗셈이므로 {@code count}가 0이면 예외 없이 무한대 또는 NaN이 된다.
	 *
	 * @param count	나눌 횟수.
	 * @return	회당 평균 경과 시간 (초).
	 */
	public double getElapsedInFloatingSeconds(int count) {
		return getElapsedInFloatingSeconds() / count;
	}
	
	/**
	 * 경과 시간 동안 {@code count}번 처리했다고 보고 초당 처리 횟수를 반환한다.
	 * 경과 시간이 0이면 예외 없이 무한대 또는 NaN이 된다.
	 *
	 * @param count	처리 횟수.
	 * @return	초당 처리 횟수.
	 */
	public double getRatesInSecond(int count) {
		return count / getElapsedInFloatingSeconds();
	}
	
	/**
	 * 측정을 멈추고 경과 시간을 {@link UnitUtils#toMillisString(long)} 형식의 문자열로 반환한다.
	 *
	 * @return	밀리초까지 표시한 경과 시간 문자열.
	 */
	public String stopAndGetElpasedTimeString() {
		stop();
		return getElapsedMillisString();
	}
	
	/**
	 * 경과 시간을 {@link UnitUtils#toMillisString(long)} 형식의 문자열로 반환한다. 측정은 멈추지 않는다.
	 *
	 * @return	밀리초까지 표시한 경과 시간 문자열.
	 */
	public String getElapsedMillisString() {
		return UnitUtils.toMillisString(getElapsedInMillis());
	}
	
	/**
	 * 경과 시간을 초 단위로 반올림하여 {@link UnitUtils#toSecondString(long)} 형식의 문자열로 반환한다.
	 * 측정은 멈추지 않는다.
	 *
	 * @return	초 단위 경과 시간 문자열.
	 */
	public String getElapsedSecondString() {
		return UnitUtils.toSecondString(getElapsedInMillis());
	}
	
	@Override
	public String toString() {
		return getElapsedSecondString();
	}
}
