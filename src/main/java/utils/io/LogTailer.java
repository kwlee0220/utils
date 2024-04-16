package utils.io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.google.common.base.Preconditions;

import utils.func.CheckedRunnable;
import utils.func.FOption;
import utils.func.Unchecked;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LogTailer implements CheckedRunnable {
	private final File m_logFile;
	private final Duration m_sampleInterval;
	private final FOption<Duration> m_timeout;
	private final boolean m_startAtBeginning;
	private volatile LogTailerListener m_listener;
	
	private LogTailer(Builder builder) {
		m_logFile = builder.m_logFile;
		m_sampleInterval = builder.m_sampleInterval;
		m_timeout = builder.m_timeout;
		m_startAtBeginning = builder.m_startAtBeginning;
	}
	
	public void addLogTailerListener(LogTailerListener listener) {
		m_listener = listener;
	}

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
			
			raf = new RandomAccessFile(m_logFile, "r");
			while ( true ) {
				long len = m_logFile.length();
				if ( len < fpos ) {
					// Log file의 크기가 마지막으로 확인했던 file position보다 작다면
					// log file이 rewind한 것으로 간주하여 파일을 재오픈하여 처음부터 읽기 시작한다.
					raf = new RandomAccessFile(m_logFile, "r");
					fpos = 0L;
					
					if ( !m_listener.logFileRewinded(m_logFile) ) {
						return;
					}
				}
				
				if ( len > fpos ) {
					raf.seek(fpos);
					
					String line = raf.readLine();
					while ( line != null ) {
						tailFound = true;
						if ( !m_listener.handleLogTail(line) ) {
							return;
						}
						line = raf.readLine();
					}
					fpos = raf.getFilePointer();
				}
				
				if ( !tailFound ) {
					if ( !m_listener.handleLogFileSilence(m_sampleInterval) ) {
						return;
					}
				}
				
				Thread.sleep(m_sampleInterval.toMillis());
				m_timeout.ifPresentOrThrow(timeout -> {
					if ( Duration.between(started, Instant.now()).compareTo(timeout) >= 0 ) {
						throw new TimeoutException("" + timeout);
					}
				});
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

	public static Builder builder() {
		return new Builder();
	}
	public static final class Builder {
		private File m_logFile;
		private Duration m_sampleInterval = Duration.ofSeconds(5);
		private FOption<Duration> m_timeout = FOption.empty();
		private boolean m_startAtBeginning = false;
		
		public Builder file(File logFile) {
			Preconditions.checkArgument(logFile != null);
			m_logFile = logFile;
			
			return this;
		}
		
		public Builder sampleInterval(Duration interval) {
			Preconditions.checkArgument(interval != null);
			m_sampleInterval = interval;
			
			return this;
		}
		
		public Builder timeout(Duration timeout) {
			m_timeout = FOption.ofNullable(timeout);
			
			return this;
		}
		
		public Builder timeout(FOption<Duration> timeout) {
			m_timeout = timeout;
			
			return this;
		}
		
		public Builder startAtBeginning(boolean flag) {
			m_startAtBeginning = flag;
			
			return this;
		}
		
		public LogTailer build() {
			return new LogTailer(this);
		}
	}
}
