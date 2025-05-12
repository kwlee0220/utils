package utils.async;

import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;

import com.google.common.collect.MinMaxPriorityQueue;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Timer {
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private boolean m_shutdown = false;
	@GuardedBy("m_guard") private final MinMaxPriorityQueue<Schedule> m_timerQueue;
	private final Thread m_schedulerThread;
	
	private static class Schedule {
		private final Execution<?> m_exec;
		private final Date m_due;
		
		Schedule(Execution<?> exec, Date due) {
			m_exec = exec;
			m_due = due;
		}
	}
	
	public Timer() {
		m_timerQueue = MinMaxPriorityQueue
						.<Schedule>orderedBy(Comparator.comparing(s -> s.m_due))
						.maximumSize(64)
						.create();
		m_schedulerThread = new Thread(m_scheduler);
		m_schedulerThread.setDaemon(true);
		m_schedulerThread.start();
	}
	
	public void shutdown() {
		m_guard.run(() -> m_shutdown=true);
	}
	
	public void setTimer(Execution<?> task, Date due) {
		m_guard.lock();
		try {
			Schedule schedule = new Schedule(task, due);
			m_timerQueue.add(schedule);
			if ( m_timerQueue.peekFirst() == schedule ) {
				m_guard.signalAll();
			}
		}
		finally {
			m_guard.unlock();
		}
	}
	
	public void setTimer(Execution<?> task, long timeout, TimeUnit unit) {
    	Date due = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
		setTimer(task, due);
	}
	
	private final Runnable m_scheduler = new Runnable() {
		@Override
		public void run() {
			m_guard.lock();
			try {
				while ( true ) {
					Date now = new Date();
					if ( m_shutdown ) {
						return;
					}
					
					try {
						Schedule schedule = m_timerQueue.peekFirst();
						if ( schedule != null ) {
							if ( !schedule.m_exec.isDone() ) {
								if ( schedule.m_due.compareTo(now) <= 0 ) {
									m_timerQueue.removeFirst();
									schedule.m_exec.cancel(true);
								}
								else {
									m_guard.awaitSignal(schedule.m_due);
								}
							}
							else {
								m_timerQueue.removeFirst();
							}
						}
						else {
							m_guard.awaitSignal();
						}
					}
					catch ( InterruptedException expected ) { }
				}
			}
			finally {
				m_guard.unlock();
			}
		}
	};
}
