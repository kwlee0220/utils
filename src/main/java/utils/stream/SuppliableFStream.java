package utils.stream;

import static utils.Utilities.checkArgument;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.Nullable;
import javax.annotation.concurrent.GuardedBy;

import utils.RuntimeInterruptedException;
import utils.Suppliable;
import utils.Throwables;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SuppliableFStream<T> implements TimedFStream<T>, Suppliable<T> {
	private final int m_length;
	
	private final ReentrantLock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private final List<T> m_buffer;
	@GuardedBy("m_lock") private boolean m_closed = false;
	@GuardedBy("m_lock") private boolean m_eos = false;
	@GuardedBy("m_lock") private @Nullable Throwable m_error = null;
	
	public SuppliableFStream(int length) {
		checkArgument(length > 0, String.format("invalid length: %d", length));
		
		m_buffer = new ArrayList<>(length);
		m_length = length;
	}
	
	SuppliableFStream() {
		m_buffer = new ArrayList<>();
		m_length = Integer.MAX_VALUE;
	}
	
	public int capacity() {
		return m_length;
	}
	
	public int size() {
		return m_buffer.size();
	}
	
	public int emptySlots() {
		return m_length - m_buffer.size();
	}

	@Override
	public void close() throws Exception {
		m_lock.lock();
		try {
			if ( !m_closed ) {
				m_closed = true;
				m_buffer.clear();
				
				// 본 conditional variable을 보고 sleep하고 있는 모든 thread을 깨운다.
				m_cond.signalAll();
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public FOption<T> next() {
		m_lock.lock();
		try {
			while ( true ) {
				// wait에서 깨고 나서 m_buffer.size() == 0 인 경우는
				// close 상태와 오류 발생 여부를 확인할 필요가 있음
				//
				if ( m_buffer.size() > 0 ) {
					T value = m_buffer.remove(0);
					m_cond.signalAll();
					
					return FOption.of(value);
				}
				else if ( m_closed || m_eos ) {
					if ( m_error == null ) {
						return FOption.empty();
					}
					else {
						throw Throwables.toRuntimeException(m_error);
					}
				}
				
				try {
					m_cond.await();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					throw new RuntimeInterruptedException(e);
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * 스트림의 다음 원소를 반환한다.
	 * <p>
	 * 만일 다음 원소가 없는 경우는 지정된 시간만큼 새 원소가 스트림에 삽입될 때까지
	 * 대기한다. 지정된 시간동안 원소가 삽입되지 않으면 {@link TimeoutException} 예외가 발생된다.
	 * 
	 * @param timeout	제한 시간
	 * @param tu		제한 시간 단위
	 * @throws TimeoutException	제한된 시간 동안 스트림이 비어있었던 경우.
	 */
	@Override
	public FOption<T> next(long timeout, TimeUnit tu) throws TimeoutException {
		Date due = new Date(System.currentTimeMillis() + tu.toMillis(timeout));
		
		m_lock.lock();
		try {
			while ( true ) {
				// wait에서 깨고 나서 m_buffer.size() == 0 인 경우는
				// close 상태와 오류 발생 여부를 확인할 필요가 있음
				//
				if ( m_buffer.size() > 0 ) {
					T value = m_buffer.remove(0);
					m_cond.signalAll();
					
					return FOption.of(value);
				}
				else if ( m_closed || m_eos ) {
					if ( m_error == null ) {
						return FOption.empty();
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
					Thread.currentThread().interrupt();
					throw new RuntimeInterruptedException(e);
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * 스트림의 다음 원소를 반환한다.
	 * <p>
	 * 원소가 없는 경우는 {@link FOption#empty()}가 반환된다.
	 * 
	 * @return	다음 원소. 스트림이 빈 경우는 {@link FOption#empty()}
	 */
	public FOption<T> poll() {
		m_lock.lock();
		try {
			if ( m_buffer.size() > 0 ) {
				T value = m_buffer.remove(0);
				m_cond.signalAll();
				
				return FOption.of(value);
			}
			else if ( m_closed || m_eos ) {
				if ( m_error == null ) {
					return FOption.empty();
				}
				else {
					throw Throwables.toRuntimeException(m_error);
				}
			}
			else {
				return FOption.empty();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public boolean isClosed() {
		m_lock.lock();
		try {
			return m_closed;
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean isEndOfSupply() {
		m_lock.lock();
		try {
			return m_eos;
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean supply(T value) throws IllegalStateException, RuntimeInterruptedException {
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_closed ) {
					throw new IllegalStateException("closed at consumer-side");
				}
				else if ( m_eos ) {
					throw new IllegalStateException("Supplier has closed the stream");
				}
				if ( m_buffer.size() < m_length ) {
					m_buffer.add(value);
					m_cond.signalAll();
					
					return true;
				}
				
				try {
					m_cond.await();
				}
				catch ( InterruptedException e ) {
					throw new RuntimeInterruptedException(e);
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean supply(T value, long timeout, TimeUnit tu) throws IllegalStateException, RuntimeInterruptedException {
		Date due = new Date(System.currentTimeMillis() + tu.toMillis(timeout));
		
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_closed ) {
					throw new IllegalStateException("closed at consumer-side");
				}
				else if ( m_eos ) {
					throw new IllegalStateException("Supplier has closed the stream");
				}
				
				if ( m_buffer.size() < m_length ) {
					m_buffer.add(value);
					m_cond.signalAll();
					
					return true;
				}
				
				try {
					if ( !m_cond.awaitUntil(due) ) {
						return false;
					}
				}
				catch ( InterruptedException e ) {
					throw new RuntimeInterruptedException(e);
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	public T supply(Supplier<T> supplier) throws IllegalStateException, RuntimeInterruptedException {
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_closed ) {
					throw new IllegalStateException("closed at consumer-side");
				}
				else if ( m_eos ) {
					throw new IllegalStateException("Supplier has closed the stream");
				}
				if ( m_buffer.size() < m_length ) {
					T value = supplier.get();
					m_buffer.add(value);
					m_cond.signalAll();
					
					return value;
				}
				
				try {
					m_cond.await();
				}
				catch ( InterruptedException e ) {
					throw new RuntimeInterruptedException(e);
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
	
	@Override
	public String toString() {
		return String.format("%s[nbuffer=%d]: %s", getClass().getSimpleName(), m_buffer.size(), m_buffer);
	}
}
