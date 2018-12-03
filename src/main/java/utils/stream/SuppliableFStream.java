package utils.stream;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import io.vavr.control.Option;
import net.jcip.annotations.GuardedBy;
import utils.Suppliable;
import utils.Throwables;
import utils.async.ThreadInterruptedException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SuppliableFStream<T> implements FStream<T>, Suppliable<T> {
	private final int m_length;
	
	private final ReentrantLock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private final List<T> m_buffer;
	@GuardedBy("m_lock") private boolean m_closed = false;
	@GuardedBy("m_lock") private boolean m_eos = false;
	@GuardedBy("m_lock") @Nullable private Throwable m_error = null;
	
	SuppliableFStream(int length) {
		m_buffer = new ArrayList<>(length);
		m_length = length;
	}
	
	ReentrantLock getLock() {
		return m_lock;
	}
	
	Condition getCondition() {
		return m_cond;
	}

	@Override
	public void close() throws Exception {
		m_lock.lock();
		try {
			if ( !m_closed ) {
				m_closed = true;
				m_buffer.clear();
				m_cond.signalAll();
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public Option<T> next() {
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_buffer.size() > 0 ) {
					T value = m_buffer.remove(0);
					m_cond.signalAll();
					
					return Option.some(value);
				}
				else if ( m_eos ) {
					if ( m_error == null ) {
						return Option.none();
					}
					else {
						throw Throwables.toRuntimeException(m_error);
					}
				}
				
				try {
					m_cond.await();
				}
				catch ( InterruptedException e ) {
					throw new ThreadInterruptedException("" + e);
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	public Option<T> next(long timeout, TimeUnit tu) throws TimeoutException {
		Date due = new Date(System.currentTimeMillis() + tu.toMillis(timeout));
		
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_buffer.size() > 0 ) {
					T value = m_buffer.remove(0);
					m_cond.signalAll();
					
					return Option.some(value);
				}
				else if ( m_eos ) {
					if ( m_error == null ) {
						Option.none();
					}
					else {
						throw Throwables.toRuntimeException(m_error);
					}
				}
				
				try {
					if ( !m_cond.awaitUntil(due) ) {
						throw new TimeoutException();
					}
				}
				catch ( InterruptedException e ) {
					throw new ThreadInterruptedException("" + e);
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public Option<T> poll() {
		m_lock.lock();
		try {
			if ( m_buffer.size() > 0 ) {
				T value = m_buffer.remove(0);
				m_cond.signalAll();
				
				return Option.some(value);
			}
			else if ( m_eos ) {
				if ( m_error == null ) {
					Option.none();
				}
				else {
					throw Throwables.toRuntimeException(m_error);
				}
			}
			
			return Option.none();
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean supply(T value) {
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_closed || m_eos ) {
					return false;
				}
				else if ( m_buffer.size() < m_length ) {
					m_buffer.add(value);
					m_cond.signalAll();
					
					return true;
				}
				
				try {
					m_cond.await();
				}
				catch ( InterruptedException e ) {
					throw new ThreadInterruptedException("" + e);
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean supply(T value, long timeout, TimeUnit tu) throws IllegalStateException {
		Date due = new Date(System.currentTimeMillis() + tu.toMillis(timeout));
		
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_closed ) {
					throw new IllegalStateException("closed already");
				}
				else if ( m_eos ) {
					throw new IllegalStateException("end-of-stream");
				}
				else if ( m_buffer.size() < m_length ) {
					m_buffer.add(value);
					m_cond.signalAll();
				}
				
				try {
					if ( !m_cond.awaitUntil(due) ) {
						return false;
					}
				}
				catch ( InterruptedException e ) {
					throw new ThreadInterruptedException("" + e);
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public void endOfSupply() {
		m_lock.lock();
		try {
			if ( !m_eos ) {
				m_eos = true;
				m_cond.signalAll();
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public void endOfSupply(Throwable error) {
		m_lock.lock();
		try {
			if ( !m_eos ) {
				m_eos = true;
				m_error = error;
				m_cond.signalAll();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
}