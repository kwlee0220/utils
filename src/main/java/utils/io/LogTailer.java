package utils.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;
import utils.func.CheckedRunnable;
import utils.func.Unchecked;

/**
 * 로그 파일의 끝부분을 지속적으로 감시하여 새로 추가되는 line을 {@link LogTailerListener}로 전달하는
 * tailer이다 (유닉스 {@code tail -f}에 해당).
 * <p>
 * {@link #run()}이 호출되면 일정 주기({@link Builder#sampleInterval(Duration)})로 파일 크기를
 * 폴링하면서 다음 이벤트를 listener에게 통지한다.
 * <ul>
 *   <li>새 line 추가 — {@link LogTailerListener#handleLogTail(String)}. 개행({@code \n}, {@code \r\n},
 *       {@code \r})으로 끝난 완전한 줄만 UTF-8로 디코딩하여 전달하며, 아직 개행이 기록되지 않은 마지막 조각은
 *       다음 주기까지 보관했다가 이어 붙인다.</li>
 *   <li>파일 rewind(크기가 이전보다 작아짐) — {@link LogTailerListener#logFileRewinded(File)}.
 *       이 경우 파일을 재오픈하여 처음부터 다시 읽고, 보관 중이던 미완성 조각은 버린다.</li>
 *   <li>한 주기 동안 파일 크기 변화 없음 — {@link LogTailerListener#handleLogFileSilence(Duration)}.
 *       개행 없는 조각만 늘어난 경우는 변화로 본다.</li>
 * </ul>
 * 파일은 8KB 단위로 읽고 줄 경계는 읽은 버퍼 안에서 찾는다.
 * 어느 콜백이든 {@code false}를 반환하면 tailing을 정상 종료한다.
 * <p>
 * <b>rewind 감지의 한계:</b> rewind는 오직 "현재 파일 크기가 마지막으로 읽은 위치보다 작아진" 경우로만
 * 판정한다. 따라서 파일이 truncate 또는 교체된 뒤 다음 polling 전에 이미 그 위치 이상으로 다시 커지면
 * rewind를 놓치고 잘못된 offset부터 읽을 수 있다. 마찬가지로 rename 후 재생성(inode 교체) 방식의 로그
 * 로테이션은 크기만으로는 감지되지 않는다. inode 단위의 교체 감지는 지원하지 않는다.
 * 기본적으로 파일의 현재 끝부터 읽으며, {@link Builder#startAtBeginning(boolean)}로 처음부터
 * 읽도록 할 수 있다. {@link Builder#timeout(Duration)}이 설정되면 그 시간이 지난 뒤
 * {@link TimeoutException}으로 종료한다.
 * <p>
 * 인스턴스는 {@link #builder()}로 생성하며 감시할 파일과 listener를 반드시 지정해야 한다.
 * {@link LogTailerListener}는 새 줄 처리만 필수이므로 람다로 줄 수 있다.
 * <pre>{@code
 * LogTailer.builder().file(logFile).listener(line -> !line.contains("started")).build().run();
 * }</pre>
 * {@link CheckedRunnable}을 구현하므로 별도 스레드나 {@code utils.async} 실행기로 구동할 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LogTailer implements CheckedRunnable {
	private final File m_logFile;
	private final Duration m_sampleInterval;
	@Nullable private final Duration m_timeout;		// 무한 대기인 경우에는 null
	private final boolean m_startAtBeginning;
	private final LogTailerListener m_listener;
	
	private static final int READ_BUFFER_SIZE = 8 * 1024;
	
	private LogTailer(Builder builder) {
		m_logFile = builder.m_logFile;
		m_sampleInterval = builder.m_sampleInterval;
		m_timeout = builder.m_timeout;
		m_startAtBeginning = builder.m_startAtBeginning;
		m_listener = builder.m_listener;
	}

	/**
	 * 로그 파일 tailing을 시작하여 listener가 종료를 요청하거나 timeout에 도달할 때까지 수행한다.
	 * <p>
	 * 설정된 sample interval 마다 파일 크기를 확인하면서 새 line, rewind, silence 이벤트를
	 * listener에게 통지한다. listener의 어느 콜백이든 {@code false}를 반환하면 정상 종료한다.
	 * 이 메소드는 위 조건 중 하나가 충족될 때까지 호출 스레드를 점유한다.
	 *
	 * @throws IOException			파일 입출력 중 오류가 발생한 경우.
	 * @throws InterruptedException	대기 중 스레드가 인터럽트된 경우.
	 * @throws TimeoutException		{@link Builder#timeout(Duration)}으로 설정한 시간을 초과한 경우.
	 * @throws ExecutionException	listener 콜백에서 위 예외 외의 오류가 발생한 경우(원인 예외를 감싼다).
	 */
	@SuppressWarnings("resource")
	@Override
	public void run() throws IOException, InterruptedException, TimeoutException, ExecutionException {
		Instant started = Instant.now();
		
		// 'm_startAtBeginning'이 설정되지 않으면, 현재 파일의 마지막부터 시작한다.
		long fpos = (m_startAtBeginning) ? 0  : m_logFile.length();
		long lastLength = -1;		// 직전 주기의 파일 크기. 첫 주기는 변화로 본다.
		LineAssembler assembler = new LineAssembler();
		byte[] buffer = new byte[READ_BUFFER_SIZE];
		
		RandomAccessFile raf = null;
		try {
			// raf 변수가 로그 파일이 rewind될 때 다시 열리기 때문에
			// try-resources 구문을 사용할 수 없다.
			raf = new RandomAccessFile(m_logFile, "r");
			while ( true ) {
				long len = m_logFile.length();
				if ( len < fpos ) {
					// Log file의 크기가 마지막으로 읽은 위치보다 작다면
					// log file이 rewind한 것으로 간주하여 파일을 재오픈하여 처음부터 읽기 시작한다.
					raf.close();
					raf = new RandomAccessFile(m_logFile, "r");
					fpos = 0L;
					assembler.reset();
					
					// 만일 listener가 false를 반환하면, log tailing을 중단한다.
					if ( !m_listener.logFileRewinded(m_logFile) ) {
						return;
					}
				}
				boolean changed = (len != lastLength);
				lastLength = len;
				
				if ( len > fpos ) {
					raf.seek(fpos);
					while ( fpos < len ) {
						int nread = raf.read(buffer, 0, (int)Math.min(buffer.length, len - fpos));
						if ( nread <= 0 ) {
							break;
						}
						fpos += nread;
						
						// 개행으로 끝난 줄만 전달된다. 미완성 조각은 assembler가 다음 읽기까지 보관한다.
						for ( String line: assembler.feed(buffer, nread) ) {
							// 만일 listener가 false를 반환하면, log tailing을 중단한다.
							if ( !m_listener.handleLogTail(line) ) {
								return;
							}
						}
					}
				}
				
				if ( !changed ) {
					// 'm_sampleInterval' 동안 log file의 크기에 변화가 없다면, listener에게 알린다.
					// 만일 listener가 false를 반환하면, log tailing을 중단한다.
					if ( !m_listener.handleLogFileSilence(m_sampleInterval) ) {
						return;
					}
				}
				
				Thread.sleep(m_sampleInterval.toMillis());
				if ( m_timeout != null ) {
					if ( Duration.between(started, Instant.now()).compareTo(m_timeout) >= 0 ) {
						throw new TimeoutException("" + m_timeout);
					}
				}
			}
		}
		catch ( IOException | InterruptedException | TimeoutException e ) {
			throw e;
		}
		catch ( Exception e ) {
			throw new ExecutionException(e);
		}
		finally {
			if ( raf != null ) {
				Unchecked.runOrIgnore(raf::close);
			}
		}
	}
	
	/**
	 * 읽은 바이트를 이어 붙여 개행({@code \n}, {@code \r\n}, {@code \r})으로 끝난 줄을 UTF-8 문자열로 잘라 낸다.
	 * 줄 끝 문자는 결과에 포함하지 않는다. 버퍼 경계에 걸친 줄과 {@code \r\n}도 다음 읽기에서 이어서 처리한다.
	 */
	static final class LineAssembler {
		private final ByteArrayOutputStream m_pending = new ByteArrayOutputStream();
		private boolean m_lastWasCr = false;	// 직전 바이트가 줄을 끝낸 '\r'이었는지. 뒤따르는 '\n'을 건너뛴다.
		
		List<String> feed(byte[] bytes, int length) {
			List<String> lines = new ArrayList<>();
			int lineStart = 0;
			for ( int i = 0; i < length; ++i ) {
				byte b = bytes[i];
				if ( b == '\n' && m_lastWasCr ) {
					// '\r\n'의 '\n': 줄은 이미 '\r'에서 끝났다.
					m_lastWasCr = false;
					lineStart = i + 1;
					continue;
				}
				m_lastWasCr = false;
				if ( b == '\n' || b == '\r' ) {
					m_pending.write(bytes, lineStart, i - lineStart);
					lines.add(m_pending.toString(StandardCharsets.UTF_8));
					m_pending.reset();
					m_lastWasCr = (b == '\r');
					lineStart = i + 1;
				}
			}
			m_pending.write(bytes, lineStart, length - lineStart);
			return lines;
		}
		
		void reset() {
			m_pending.reset();
			m_lastWasCr = false;
		}
	}

	/**
	 * {@code LogTailer} 인스턴스를 구성하기 위한 새 {@link Builder}를 반환한다.
	 *
	 * @return	새 {@code Builder} 인스턴스.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * {@link LogTailer} 인스턴스를 구성하는 빌더이다.
	 * <p>
	 * 감시할 로그 파일({@link #file(File)})은 반드시 지정해야 하며, 나머지 설정은 기본값을 가진다
	 * (sample interval 5초, timeout 없음(무한 대기), 파일 끝부터 시작). listener({@link #listener(LogTailerListener)})도
	 * 반드시 지정해야 한다.
	 */
	public static final class Builder {
		private File m_logFile;
		private LogTailerListener m_listener;
		private Duration m_sampleInterval = Duration.ofSeconds(5);
		@Nullable private Duration m_timeout = null;	// 무한 대기인 경우는 null
		private boolean m_startAtBeginning = false;

		/**
		 * 기본 설정으로 빌더를 생성한다. 일반적으로는 {@link LogTailer#builder()}를 사용한다.
		 */
		public Builder() {}

		/**
		 * 감시할 로그 파일을 지정한다.
		 *
		 * @param logFile	감시 대상 로그 파일. {@code null}이면 안 된다.
		 * @return	메서드 체이닝을 위한 자기 자신.
		 */
		public Builder file(File logFile) {
			Preconditions.checkArgument(logFile != null, "logFile is null");
			m_logFile = logFile;

			return this;
		}

		/**
		 * 로그 파일의 변화를 통지받을 listener를 지정한다. 하나만 보관하며 다시 호출하면 대체한다.
		 *
		 * @param listener	로그 파일 변화를 처리할 listener. {@code null}이면 안 된다.
		 * @return	메서드 체이닝을 위한 자기 자신.
		 */
		public Builder listener(LogTailerListener listener) {
			Preconditions.checkArgument(listener != null, "listener is null");
			m_listener = listener;

			return this;
		}

		/**
		 * 파일 변화를 확인하는 폴링 주기를 지정한다.
		 * <p>
		 * 이 주기는 {@link LogTailerListener#handleLogFileSilence(Duration)}에 전달되는
		 * "변화 없음" 구간의 길이이기도 하다. 기본값은 5초이다.
		 *
		 * @param interval	폴링 주기. 양수여야 한다.
		 * @return	메서드 체이닝을 위한 자기 자신.
		 * @throws IllegalArgumentException	{@code interval}이 {@code null}이거나 0 이하인 경우.
		 */
		public Builder sampleInterval(Duration interval) {
			Preconditions.checkArgument(interval != null, "interval is null");
			Preconditions.checkArgument(!interval.isNegative() && !interval.isZero(),
										"interval must be positive: " + interval);
			m_sampleInterval = interval;

			return this;
		}

		/**
		 * tailing의 최대 수행 시간을 지정한다.
		 * <p>
		 * {@link #run()} 시작 후 이 시간이 지나면 {@link TimeoutException}으로 종료한다.
		 * {@code null}(기본값)이면 무한 대기한다.
		 *
		 * @param timeout	최대 수행 시간. {@code null}이면 무한 대기.
		 * @return	메서드 체이닝을 위한 자기 자신.
		 */
		public Builder timeout(Duration timeout) {
			m_timeout = timeout;

			return this;
		}

		/**
		 * 파일의 어느 위치부터 읽기 시작할지를 지정한다.
		 *
		 * @param flag	{@code true}이면 파일 처음부터, {@code false}(기본값)이면 현재 끝부터 읽는다.
		 * @return	메서드 체이닝을 위한 자기 자신.
		 */
		public Builder startAtBeginning(boolean flag) {
			m_startAtBeginning = flag;

			return this;
		}

		/**
		 * 현재 설정으로 {@link LogTailer} 인스턴스를 생성한다.
		 *
		 * @return	생성된 {@code LogTailer} 인스턴스.
		 * @throws IllegalStateException	감시할 로그 파일({@link #file(File)})이나 listener
		 * 									({@link #listener(LogTailerListener)})가 지정되지 않은 경우.
		 */
		public LogTailer build() {
			Preconditions.checkState(m_logFile != null, "logFile is not set");
			Preconditions.checkState(m_listener != null, "listener is not set");
			return new LogTailer(this);
		}
	}
}
