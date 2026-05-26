package utils.io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.Duration;
import java.time.Instant;
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
 *   <li>새 line 추가 — {@link LogTailerListener#handleLogTail(String)}.</li>
 *   <li>파일 rewind(크기가 이전보다 작아짐) — {@link LogTailerListener#logFileRewinded(File)}.
 *       이 경우 파일을 재오픈하여 처음부터 다시 읽는다.</li>
 *   <li>한 주기 동안 변화 없음 — {@link LogTailerListener#handleLogFileSilence(Duration)}.</li>
 * </ul>
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
 * 인스턴스는 {@link #builder()}로 생성하며, {@link #addLogTailerListener(LogTailerListener)}로
 * listener를 등록한 뒤 {@link #run()}을 호출해야 한다. {@link CheckedRunnable}을 구현하므로
 * 별도 스레드나 {@code utils.async} 실행기로 구동할 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LogTailer implements CheckedRunnable {
	private final File m_logFile;
	private final Duration m_sampleInterval;
	@Nullable private final Duration m_timeout;		// 무한 대기인 경우에는 null
	private final boolean m_startAtBeginning;
	private volatile LogTailerListener m_listener;
	
	private LogTailer(Builder builder) {
		m_logFile = builder.m_logFile;
		m_sampleInterval = builder.m_sampleInterval;
		m_timeout = builder.m_timeout;
		m_startAtBeginning = builder.m_startAtBeginning;
	}
	
	/**
	 * 로그 파일의 변화를 통지받을 listener를 등록한다.
	 * <p>
	 * {@link #run()} 호출 전에 반드시 설정되어야 하며, 설정되지 않은 채 {@code run()}이 호출되면
	 * {@link IllegalStateException}이 발생한다. 단일 listener만 보관하며, 다시 호출하면 이전 listener를
	 * 대체한다.
	 *
	 * @param listener	로그 파일 변화를 처리할 listener.
	 */
	public void addLogTailerListener(LogTailerListener listener) {
		m_listener = listener;
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
		Preconditions.checkState(m_listener != null, "LogTailerListener is not set");
		
		Instant started = Instant.now();
		
		// 'm_startAtBeginning'이 설정되지 않으면, 현재 파일의 마지막부터 시작한다.
		long fpos = (m_startAtBeginning) ? 0  : m_logFile.length();
		
		RandomAccessFile raf = null;
		try {
			// 'm_sampleInterval' 동안 최소 1줄의 파일을 읽었는지 여부를 판단하기 위해 사용함.
			boolean tailFound = true;

			// raf 변수가 로그 파일이 rewind될 때 다시 열리기 때문에
			// try-resources 구문을 사용할 수 없다.
			raf = new RandomAccessFile(m_logFile, "r");
			while ( true ) {
				long len = m_logFile.length();
				if ( len < fpos ) {
					// Log file의 크기가 마지막으로 확인했던 file position보다 작다면
					// log file이 rewind한 것으로 간주하여 파일을 재오픈하여 처음부터 읽기 시작한다.
					raf.close();
					raf = new RandomAccessFile(m_logFile, "r");
					fpos = 0L;
					
					// rewind 이벤트를 listener에게 알린다.
					// 만일 listener가 false를 반환하면, log tailing을 중단한다.
					if ( !m_listener.logFileRewinded(m_logFile) ) {
						return;
					}
				}
				
				if ( len > fpos ) {
					raf.seek(fpos);

					// 개행으로 끝나는 완전한 줄만 소비한다. 아직 끝까지 기록되지 않은 마지막
					// 미완성 줄은 readLineUtf8이 file pointer를 되돌리고 null을 반환하므로,
					// 다음 polling 주기에 더 채워진 내용과 함께 처음부터 다시 읽게 된다.
					String line = IOUtils.readLineUtf8(raf, true);
					while ( line != null ) {
						tailFound = true;

						// 새로운 log line을 listener에게 알린다.
						// 만일 listener가 false를 반환하면, log tailing을 중단한다.
						if ( !m_listener.handleLogTail(line) ) {
							return;
						}
						line = IOUtils.readLineUtf8(raf, true);
					}
					// 마지막 미완성 줄을 다시 읽기 위해 그 줄의 시작 위치를 보존한다.
					fpos = raf.getFilePointer();
				}
				
				if ( !tailFound ) {
					// 'm_sampleInterval' 동안 log file에 변화가 없다면, listener에게 알린다.
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
				tailFound = false;
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
	 * (sample interval 5초, timeout 없음(무한 대기), 파일 끝부터 시작).
	 */
	public static final class Builder {
		private File m_logFile;
		private Duration m_sampleInterval = Duration.ofSeconds(5);
		@Nullable private Duration m_timeout = null;	// 무한 대기인 경우는 null
		private boolean m_startAtBeginning = false;

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
		 * 파일 변화를 확인하는 폴링 주기를 지정한다.
		 * <p>
		 * 이 주기는 {@link LogTailerListener#handleLogFileSilence(Duration)}에 전달되는
		 * "변화 없음" 구간의 길이이기도 하다. 기본값은 5초이다.
		 *
		 * @param interval	폴링 주기. {@code null}이면 안 된다.
		 * @return	메서드 체이닝을 위한 자기 자신.
		 */
		public Builder sampleInterval(Duration interval) {
			Preconditions.checkArgument(interval != null, "interval is null");
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
		 * @throws IllegalStateException	감시할 로그 파일({@link #file(File)})이 지정되지 않은 경우.
		 */
		public LogTailer build() {
			Preconditions.checkState(m_logFile != null, "logFile is not set");
			return new LogTailer(this);
		}
	}
}
