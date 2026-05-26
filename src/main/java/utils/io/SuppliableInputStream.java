package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import utils.Throwables;
import utils.UnitUtils;
import utils.thread.Guard;


/**
 * 외부에서 데이터를 밀어넣어(supply) 채우는 생산자-소비자 형태의 {@link InputStream}이다.
 * <p>
 * 일반적인 {@code InputStream}이 소스를 직접 읽는 것과 달리, 이 스트림은 데이터의 출처를 모른다.
 * 생산자 쓰레드가 {@link #supply(ByteBuffer)}로 {@link ByteBuffer} chunk를 큐에 밀어넣고,
 * 소비자 쓰레드는 {@link #read()} / {@link #read(byte[], int, int)}로 그 chunk들을 공급된 순서대로
 * 읽는다. chunk가 없으면 소비자는 새 chunk가 공급되거나 스트림이 종료될 때까지 대기(block)한다.
 *
 * <h3>생애주기</h3>
 * <ul>
 *   <li><b>공급</b> — {@link #supply(ByteBuffer)} 또는 시간제한 있는
 *       {@link #supply(ByteBuffer, long, TimeUnit)}로 chunk를 추가한다. 큐 길이 상한
 *       ({@link #getMaxQueueLength()})에 도달하면 공급 쪽이 대기한다(back-pressure).</li>
 *   <li><b>정상 종료</b> — {@link #endOfSupply()}를 호출하면 EOS(end-of-stream)로 표시되어,
 *       남은 chunk를 모두 읽은 뒤 {@link #read()}가 {@code -1}을 반환한다.</li>
 *   <li><b>오류 종료</b> — {@link #endOfSupply(Throwable)}로 종료하면, 남은 chunk를 모두 읽은 뒤
 *       소비자의 {@code read} 호출에서 해당 원인 예외가 {@link IOException}으로 전달된다.</li>
 *   <li><b>닫힘</b> — {@link #close()}는 큐에 남은 chunk를 버리고 즉시 닫는다. 이후의 공급은
 *       {@link StreamClosedException}, 읽기는 {@link IOException}이 된다.</li>
 * </ul>
 *
 * <h3>스레드 안전성</h3>
 * 내부 상태는 {@link Guard}로 보호되며 공급 쪽과 소비 쪽을 서로 다른 쓰레드에서 사용하도록 설계되었다.
 * 단, 소비(읽기)는 단일 쓰레드에서 수행하는 것을 전제로 한다. 외부 협력 대기를 위해
 * {@link #getGuard()}, {@link #awaitActiveChunk(int, long, TimeUnit)}를 제공한다.
 * <p>
 * 공급한 {@link ByteBuffer}는 복사하지 않고 참조로 보관하므로, 공급 이후 해당 버퍼를 외부에서
 * 변경하면 안 된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SuppliableInputStream extends InputStream {
	private static final Logger s_logger = LoggerFactory.getLogger(SuppliableInputStream.class);
	
	private final int m_maxQueueLength;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private final List<ByteBuffer> m_chunkQueue;
	@GuardedBy("m_guard") private int m_current = 0;
	@GuardedBy("m_guard") private boolean m_closed = false;
	@GuardedBy("m_guard") private boolean m_eos = false;
	@GuardedBy("m_guard") private Throwable m_cause = null;
	@GuardedBy("m_guard") private ByteBuffer m_buffer;
	// 소비 쓰레드가 갱신하고 다른 쓰레드가 offset()으로 조회할 수 있으므로 volatile로 가시성을 보장한다.
	private volatile long m_totalOffset = 0;
	
	/**
	 * 큐 길이 제한이 없는 {@code SuppliableInputStream}을 생성한다.
	 * <p>
	 * 공급된 chunk는 소비 속도와 무관하게 무제한으로 큐에 쌓일 수 있으므로
	 * {@link #supply(ByteBuffer)}는 큐 포화로 대기하지 않는다.
	 *
	 * @return	생성된 {@code SuppliableInputStream} 인스턴스.
	 */
	public static SuppliableInputStream create() {
		return new SuppliableInputStream();
	}

	/**
	 * 큐 길이 상한이 지정된 {@code SuppliableInputStream}을 생성한다.
	 * <p>
	 * 큐에 쌓인 chunk 수가 {@code chunkQLength}에 도달하면, 소비로 빈자리가 생길 때까지
	 * {@link #supply(ByteBuffer)}가 대기한다(back-pressure).
	 *
	 * @param chunkQLength	큐에 보관할 수 있는 chunk의 최대 개수.
	 * @return	생성된 {@code SuppliableInputStream} 인스턴스.
	 */
	public static SuppliableInputStream create(int chunkQLength) {
		return new SuppliableInputStream(chunkQLength);
	}
	
	private SuppliableInputStream(int chunkQLength) {
		m_maxQueueLength = chunkQLength;
		m_chunkQueue = Lists.newArrayListWithCapacity(chunkQLength);
		m_buffer = null;
	}
	
	private SuppliableInputStream() {
		m_maxQueueLength = Integer.MAX_VALUE;
		m_chunkQueue = Lists.newArrayList();
		m_buffer = null;
	}
	
	/**
	 * 스트림을 닫는다.
	 * <p>
	 * 큐에 남아 있는 chunk를 모두 버리고 닫힌 상태로 표시한 뒤, 대기 중인 공급/소비 쓰레드를 모두 깨운다.
	 * 닫힌 이후의 {@link #supply(ByteBuffer)}는 {@link StreamClosedException}, 읽기는
	 * {@link IOException}으로 실패한다. {@link #endOfSupply()}와 달리 남은 데이터를 소비하게
	 * 두지 않고 즉시 폐기한다.
	 */
	@Override
	public void close() throws IOException {
		m_guard.lock();
		try {
			m_chunkQueue.clear();
			m_closed = true;

			m_guard.signalAll();
		}
		finally {
			m_guard.unlock();
		}

		super.close();
	}

	/**
	 * 스트림이 닫혔는지 여부를 반환한다.
	 *
	 * @return	{@link #close()}가 호출되어 닫혔으면 {@code true}.
	 */
	public boolean isClosed() {
		return m_guard.get(() -> m_closed);
	}

	/**
	 * 큐에 보관할 수 있는 chunk의 최대 개수를 반환한다.
	 *
	 * @return	큐 길이 상한. {@link #create()}로 생성한 경우 {@link Integer#MAX_VALUE}.
	 */
	public int getMaxQueueLength() {
		return m_maxQueueLength;
	}

	/**
	 * 현재 큐에 쌓여 있는(아직 소비되지 않은) chunk의 개수를 반환한다.
	 *
	 * @return	현재 큐 길이.
	 */
	public int getQueueLength() {
		return m_guard.get(() -> m_chunkQueue.size());
	}

	/**
	 * 내부 상태를 보호하는 {@link Guard}를 반환한다.
	 * <p>
	 * 공급/소비 진행 상황에 맞춰 외부에서 추가적인 조건 대기를 수행할 때 사용한다.
	 *
	 * @return	이 스트림의 내부 {@code Guard}.
	 */
	public Guard getGuard() {
		return m_guard;
	}

	/**
	 * 지금까지 읽은 누적 바이트 수를 반환한다.
	 *
	 * @return	{@code read} 호출로 소비된 총 바이트 수.
	 */
	public long offset() {
		return m_totalOffset;
	}

	/**
	 * 현재 소비 중인 chunk의 순번을 반환한다.
	 * <p>
	 * 소비가 새 chunk로 넘어갈 때마다 1씩 증가하므로, 공급된 chunk 중 몇 번째까지 소비가
	 * 진행되었는지를 나타낸다.
	 *
	 * @return	현재 활성 chunk의 순번(소비를 시작한 chunk 개수).
	 */
	public int getCurrentChunkSeqNo() {
		return m_guard.get(() -> m_current);
	}

	/**
	 * 소비가 지정한 순번의 chunk에 도달할 때까지 대기한다.
	 * <p>
	 * 현재 활성 chunk 순번({@link #getCurrentChunkSeqNo()})이 {@code chunkNo} 이상이 되거나
	 * 스트림이 닫힐 때까지 대기한다. 공급 쪽이 소비 진행을 따라가며 동기화할 때 사용한다.
	 *
	 * @param chunkNo	대기할 목표 chunk 순번.
	 * @param timeout	최대 대기 시간.
	 * @param tu		{@code timeout}의 시간 단위.
	 * @return	목표 순번에 도달했거나 스트림이 닫혀 대기가 끝나면 {@code true},
	 * 			제한 시간이 먼저 경과하면 {@code false}.
	 * @throws InterruptedException	대기 중 쓰레드가 인터럽트된 경우.
	 */
	public boolean awaitActiveChunk(int chunkNo, long timeout, TimeUnit tu)
		throws InterruptedException {
		Date due = new Date(System.currentTimeMillis() + tu.toMillis(timeout));
		
		m_guard.lock();
		try {
			// 이미 lock을 보유하고 있으므로 m_closed를 직접 참조한다(isClosed()는 매번 재진입+불필요한
			// signalAll을 유발한다).
			while ( !m_closed && m_current < chunkNo ) {
				if ( !m_guard.awaitSignal(due) ) {
					return false;
				}
			}

			return true;
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 다음 한 바이트를 읽어 반환한다.
	 * <p>
	 * 소비할 데이터가 없으면 새 chunk가 공급되거나 스트림이 종료될 때까지 대기한다.
	 *
	 * @return	읽은 바이트 값(0~255). 스트림 끝({@link #endOfSupply()})에 도달하면 {@code -1}.
	 * @throws IOException	스트림이 닫혔거나, {@link #endOfSupply(Throwable)}로 전달된 오류가 있는 경우.
	 */
	@Override
	public int read() throws IOException {
		ByteBuffer chunk = locateCurrentChunk();
		if ( chunk != null ) {
			++m_totalOffset;
			return chunk.get() & 0x000000ff;
		}
		else {
			return -1;
		}
	}

	/**
	 * 최대 {@code len} 바이트를 {@code b}의 {@code off} 위치부터 채워 읽는다.
	 * <p>
	 * 소비할 데이터가 없으면 새 chunk가 공급되거나 스트림이 종료될 때까지 대기한다.
	 * 한 번의 호출은 현재 chunk에 남은 바이트까지만 반환하므로, 여러 chunk에 걸쳐 채우지 않는다.
	 * 즉, 실제 읽은 바이트 수는 {@code len}보다 작을 수 있다.
	 *
	 * @param b		읽은 바이트를 채울 버퍼.
	 * @param off	{@code b}에 채우기 시작할 위치.
	 * @param len	읽을 최대 바이트 수.
	 * @return	실제로 읽은 바이트 수. 스트림 끝에 도달하면 {@code -1}.
	 * @throws IOException	스트림이 닫혔거나, {@link #endOfSupply(Throwable)}로 전달된 오류가 있는 경우.
	 */
	@Override
    public int read(byte b[], int off, int len) throws IOException {
		if ( len == 0 ) {
			// InputStream 계약: len이 0이면 바로 0을 반환한다(블록하지 않음).
			return 0;
		}

		ByteBuffer chunk = locateCurrentChunk();
		if ( chunk != null ) {
			int nbytes = Math.min(chunk.remaining(), len);
			
			chunk.get(b, off, nbytes);
			m_totalOffset += nbytes;

			return nbytes;
		}
		else {
			return -1;
		}
    }
	
	/**
	 * 주어진 데이터 chunk를 스트림에 추가한다.
	 * <p>
	 * 큐가 가득 차 있으면({@link #getMaxQueueLength()}) 빈자리가 생길 때까지 무한 대기한다.
	 * 이미 {@link #endOfSupply()}가 호출된 상태라면 chunk를 추가하지 않고 조용히 반환한다.
	 *
	 * @param chunk	추가할 데이터 chunk. 복사하지 않고 참조로 보관하므로 이후 외부에서 변경하면 안 된다.
	 * @throws InterruptedException	큐 포화로 대기하던 중 쓰레드가 인터럽트된 경우.
	 * @throws StreamClosedException	스트림이 이미 닫힌 경우.
	 */
	public void supply(ByteBuffer chunk) throws InterruptedException, StreamClosedException {
		m_guard.lock();
		try {
			while ( true ) {
				if ( m_closed ) {
					throw new StreamClosedException("Stream is closed already");
				}
				if ( m_eos ) {
					return;
				}
				if ( m_chunkQueue.size() < m_maxQueueLength ) {
					m_chunkQueue.add(chunk);
					m_guard.signalAll();
					
					return;
				}
				
				m_guard.awaitSignal();
			}
		}
		finally {
			m_guard.unlock();
		}
	}
	
	/**
	 * 주어진 데이터를 입력 스트림에 추가시킨다.
	 * 
	 * @param chunk		제공할 데이터 chunk
	 * @param timeout	시간제한 시간
	 * 					0이면 바로 {@link TimeoutException} 발생,
	 * 					양수이면 지정된 시간만큼만 대기 후 {@link TimeoutException} 발생,
	 * 					음수이면 무한 대기
	 * @param unit		제한 시간 단위
	 * @throws InterruptedException	데이터 추가 과정에서 대기가 발생한 상태에서 대기 쓰레드가
	 * 					중단된 경우.
	 * @throws StreamClosedException	입력 스트림이 닫힌 경우
	 * @throws TimeoutException		데이터 추가 과정에서 대기가 발생한 상태에서
	 * 								지정된 시간이 경과한 경우.
	 */
	public void supply(ByteBuffer chunk, long timeout, TimeUnit unit)
		throws InterruptedException, StreamClosedException, TimeoutException {
		Date due = (timeout > 0) ? new Date(System.currentTimeMillis() + unit.toMillis(timeout)) : null;
		
		m_guard.lock();
		try {
			while ( true ) {
				if ( m_closed ) {
					throw new StreamClosedException("Stream is closed already");
				}
				if ( m_eos ) {
					return;
				}
				if ( m_chunkQueue.size() < m_maxQueueLength ) {
					m_chunkQueue.add(chunk);
					m_guard.signalAll();
					
					return;
				}
				
				if ( timeout < 0 ) {
					m_guard.awaitSignal();
				}
				else if ( timeout == 0 ) {
					throw new TimeoutException("supply timeout");
				}
				else {
					if ( !m_guard.awaitSignal(due) ) {
						String details = String.format("supply timeout: %s",
														UnitUtils.toMillisString(unit.toMillis(timeout)));
						throw new TimeoutException(details);
					}
				}
			}
		}
		finally {
			m_guard.unlock();
		}
	}
	
	/**
	 * 더 이상 공급할 데이터가 없음을 표시하여 스트림을 정상 종료한다.
	 * <p>
	 * 호출 후 소비자는 큐에 남은 chunk를 모두 읽은 뒤 {@code read}에서 {@code -1}(EOS)을 받는다.
	 */
	public void endOfSupply() {
		m_guard.run(() -> m_eos = true);
	}

	/**
	 * 오류와 함께 스트림 공급을 종료한다.
	 * <p>
	 * 소비자는 큐에 남은 chunk를 모두 읽은 뒤, {@code read} 호출에서 주어진 {@code cause}를
	 * 전달받는다({@link IOException}이면 그대로, 아니면 감싸거나 sneaky-throw 된다).
	 * 이미 종료된 상태이면 아무 동작도 하지 않으므로, 최초 종료 원인만 유지된다.
	 *
	 * @param cause	종료를 유발한 원인 예외.
	 */
	public void endOfSupply(Throwable cause) {
		m_guard.run(() -> {
			if ( !m_eos ) {
				m_eos = true;
				m_cause = cause;
			}
		});
	}
	
	@Override
	public String toString() {
		String chunkStr = String.format("[%d:%d]", m_current, m_chunkQueue.size());
		return String.format("%s[chunks%s, %s, %s, offset=%d]",
								getClass().getSimpleName(), chunkStr,
								m_closed ? "closed" : "open",
								m_eos ? "eos" : "supplying",
								m_totalOffset);
	}
	
	private ByteBuffer locateCurrentChunk() throws IOException {
		m_guard.lock();
		try {
			if ( m_closed ) {
				throw new IOException("closed already");
			}
			
			if ( m_buffer == null || !m_buffer.hasRemaining() ) {
				m_buffer = getNextChunkInGuard();
			}
			
			return m_buffer;
		}
		catch ( InterruptedException e ) {
			throw new IOException("" + e);
		}
		finally {
			m_guard.unlock();
		}
	}
	
	private ByteBuffer getNextChunkInGuard() throws InterruptedException, IOException {
		if ( !m_chunkQueue.isEmpty() && m_current > 0 ) {
			m_chunkQueue.remove(0);
		}

		while ( true ) {
			// 데이터가 없는 빈 chunk는 소비할 것이 없으므로 건너뛴다. 이를 그대로 활성 chunk로
			// 반환하면 read()에서 BufferUnderflowException, read(byte[])에서 0 반환 루프가 발생한다.
			boolean skipped = false;
			while ( !m_chunkQueue.isEmpty() && !m_chunkQueue.get(0).hasRemaining() ) {
				m_chunkQueue.remove(0);
				skipped = true;
			}
			if ( skipped ) {
				// 큐에 빈자리가 생겼으니 공급 대기자를 깨운다.
				m_guard.signalAll();
			}
			if ( !m_chunkQueue.isEmpty() ) {
				break;
			}

			if ( m_closed ) {
				throw new IOException("Stream has been closed already");
			}
			if ( m_eos ) {
				// 더 이상의  chunk supply가 없는 경우, 정상적으로 종료된 것인지
				// 오류에 의한 종료인지를 확인한다.
				if ( m_cause != null ) {
					Throwables.throwIfInstanceOf(m_cause, IOException.class);
					Throwables.sneakyThrow(m_cause);
				}
				return null;
			}

			m_guard.awaitSignal();
		}

		ByteBuffer head = m_chunkQueue.get(0);
		++m_current;
		s_logger.debug("get_next_chunk: {}", this);
		m_guard.signalAll();

		return head;
	}
}