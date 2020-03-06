package utils.thread;


import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class CamusExecutorImpl implements CamusExecutor {
	private final DelayQueue<DelayedTask> m_queue;
	private final ExecutorService m_executor;
	private Thread m_worker;

	public CamusExecutorImpl(ExecutorService executor) {
		m_queue = new DelayQueue<DelayedTask>();
		m_executor = executor;

		m_worker = new Thread(new Worker(), "camus.executor");
		m_worker.setDaemon(true);
		m_worker.start();
	}

	public CamusExecutorImpl() {
		m_queue = new DelayQueue<DelayedTask>();
		m_executor = Executors.newCachedThreadPool();

		m_worker = new Thread(new Worker(), "camus.executor");
		m_worker.setDaemon(true);
		m_worker.start();
	}

	public Future<?> schedule(Runnable task, long delay, TimeUnit tu) {
		if ( task == null ) {
			throw new IllegalArgumentException("task was null");
		}

		DelayedTask wrapped = new DelayedTask(task, tu.toMillis(delay));
		m_queue.add(wrapped);

		return wrapped;
	}

	public Future<?> schedule(Runnable task, long delayMillis) {
		if ( task == null ) {
			throw new IllegalArgumentException("task was null");
		}

		DelayedTask wrapped = new DelayedTask(task, delayMillis);
		m_queue.add(wrapped);

		return wrapped;
	}

	public Future<?> submit(Runnable task) {
		return m_executor.submit(task);
	}

	public <T> Future<T> submit(Callable<T> task) {
		return m_executor.submit(task);
	}

	public void execute(Runnable task) {
		m_executor.execute(task);
	}

	public RecurringSchedule createScheduleWithFixedRate(RecurringWork work, long initialDelay,
															long delay) {
		if ( work == null ) {
			throw new IllegalArgumentException("work was null");
		}
		if ( delay < 0 ) {
			throw new IllegalArgumentException("delay must be equal or larger than zero");
		}

		return new RecurringScheduleImpl(work, true, initialDelay, delay, this);
	}

	public RecurringSchedule createScheduleWithFixedDelay(RecurringWork work, long initialDelay,
															long delay) {
		if ( work == null ) {
			throw new IllegalArgumentException("work was null");
		}
		if ( delay < 0 ) {
			throw new IllegalArgumentException("delay must be equal or larger than zero");
		}

		return new RecurringScheduleImpl(work, false, initialDelay, delay, this);
	}

	static class SimpleRecurringWork implements RecurringWork {
		private final Runnable m_task;

		SimpleRecurringWork(Runnable task) {
			m_task = task;
		}

		public void onStarted(RecurringSchedule schedule) throws Throwable { }
		public void onStopped() { }

		public void perform() throws Exception {
			m_task.run();
		}
	}

	public RecurringSchedule createScheduleWithFixedRate(Runnable work, long initialDelay,
															long delay) {
		if ( work == null ) {
			throw new IllegalArgumentException("work was null");
		}
		if ( delay < 0 ) {
			throw new IllegalArgumentException("delay must be equal or larger than zero");
		}

		return new RecurringScheduleImpl(new SimpleRecurringWork(work), true, initialDelay, delay, this);
	}

	public RecurringSchedule createScheduleWithFixedDelay(Runnable work, long initialDelay,
															long delay) {
		if ( work == null ) {
			throw new IllegalArgumentException("work was null");
		}
		if ( delay < 0 ) {
			throw new IllegalArgumentException("delay must be equal or larger than zero");
		}

		return new RecurringScheduleImpl(new SimpleRecurringWork(work), false, initialDelay, delay, this);
	}

	public synchronized void stop(boolean shutdownBaseExecutor) {
		if ( m_worker != null ) {
			m_worker.interrupt();
			m_worker = null;

			m_queue.clear();
		}

		if ( shutdownBaseExecutor ) {
			m_executor.shutdown();
		}
	}

	class Worker implements Runnable {
		public void run() {
			try {
				while ( true ) {
					synchronized ( CamusExecutorImpl.this ) {
						if ( m_worker == null ) {
							return;
						}
					}

					DelayedTask task = m_queue.take();
					synchronized ( task ) {
						if ( task.m_state == STATE_NOTSTARTED ) {
							task.m_state = STATE_RUNNING;
						}
						else {
							task = null;
						}
					}

					if ( task != null ) {
						m_executor.execute(task);
					}
				}
			}
			catch ( InterruptedException expected ) { }
			catch ( Throwable e ) {
				e.printStackTrace(System.err);
			}
		}
	}

	private static final int STATE_NOTSTARTED = 0;
	private static final int STATE_RUNNING = 1;
	private static final int STATE_CANCEL_REQUESTED = 2;
	private static final int STATE_FINISHED = 3;
	private static final int STATE_CANCELLED = 4;

	class DelayedTask implements Runnable, ScheduledFuture<Object> {
		private final Runnable m_task;
		@GuardedBy("this") private long m_dueNanos;
		@GuardedBy("this") private Thread m_thread;
		@GuardedBy("this") private Throwable m_cause;
		@GuardedBy("this") private int m_state;

		DelayedTask(Runnable task, long delayMillis) {
			m_task = task;
			m_state = STATE_NOTSTARTED;
			m_dueNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
		}

		@Override
		public synchronized Object get() throws InterruptedException, ExecutionException {
			while ( true ) {
				switch ( m_state ) {
					case STATE_FINISHED:
						if ( m_cause == null ) {
							return null;
						}
						else if ( m_cause instanceof InterruptedException ) {
							throw (InterruptedException)m_cause;
						}
						else {
							throw (ExecutionException)m_cause;
						}
					case STATE_CANCELLED:
						throw new CancellationException();
					default:
						this.wait();
				}
			}
		}

		@Override
		public synchronized Object get(long timeout, TimeUnit tu)
			throws InterruptedException, ExecutionException, TimeoutException {
			long dueNanos = System.nanoTime() + tu.toNanos(timeout);
			while ( true ) {
				switch ( m_state ) {
					case STATE_FINISHED:
						if ( m_cause == null ) {
							return null;
						}
						else if ( m_cause instanceof InterruptedException ) {
							throw (InterruptedException)m_cause;
						}
						else {
							throw (ExecutionException)m_cause;
						}
					case STATE_CANCELLED:
						throw new CancellationException();
					default:
						long remainNanos = dueNanos - System.nanoTime();
						if ( remainNanos <= 0 ) {
							throw new TimeoutException();
						}

						this.wait(remainNanos/1000000, (int)remainNanos%1000000);
				}
			}
		}

		@Override
		public void run() {
			synchronized ( this ) {
				m_thread = Thread.currentThread();
			}

			try {
				m_task.run();
			}
			catch ( Throwable e ) {
				synchronized ( this ) {
					m_cause = e;
				}
			}
			finally {
				synchronized ( this ) {
					m_state = ( m_state == STATE_CANCEL_REQUESTED )
							? STATE_CANCELLED : STATE_FINISHED;
					this.notifyAll();
				}
			}
		}

		@Override
		public synchronized boolean isCancelled() {
			return m_state == STATE_CANCELLED;
		}

		@Override
		public synchronized boolean isDone() {
			return m_state >= STATE_FINISHED;
		}

		@Override
		public synchronized boolean cancel(boolean mayInterruptIfRunning) {
			if ( m_state == STATE_NOTSTARTED ) {
				m_state = STATE_CANCELLED;
				m_queue.remove(this);

				return true;
			}
			else if ( m_state == STATE_RUNNING ) {
				if ( mayInterruptIfRunning ) {
					m_thread.interrupt();

					try {
						this.wait();
					}
					catch ( InterruptedException e ) {
						Thread.currentThread().interrupt();
						return false;
					}

					return m_state == STATE_CANCELLED;
				}
				else {
					return false;
				}
			}
			else if ( m_state == STATE_CANCELLED ) {
				return true;
			}
			else {
				return false;
			}
		}

		@Override
		public long getDelay(TimeUnit tu) {
			long gapNanos = m_dueNanos - System.nanoTime();
			
			return tu.convert(gapNanos, TimeUnit.NANOSECONDS);
		}
		
		@Override
		public boolean equals(Object obj) {
			return this == obj;
		}

		@Override
		public int compareTo(Delayed delayed) {
			long gap = getDelay(TimeUnit.MILLISECONDS) - delayed.getDelay(TimeUnit.MILLISECONDS);
			if ( gap > 0 ) {
				return 1;
			}
			else if ( gap < 0 ) {
				return -1;
			}
			else {
				return 0;
			}
		}
	}
}
