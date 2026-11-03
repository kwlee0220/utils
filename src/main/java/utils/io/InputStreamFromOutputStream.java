package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.function.Function;

import com.google.common.base.Preconditions;

import utils.Tuple;
import utils.async.Execution;

/**
 * {@link OutputStream}에 데이터를 쓰는 작업(pump)의 출력을 pipe를 통해 읽을 수 있게 하는 {@link InputStream}이다.
 * <p>
 * pump 함수는 pipe의 출력 측 {@link OutputStream}을 받아 데이터를 기록하는 {@link Execution}을
 * 반환해야 하며, 본 스트림은 pipe의 입력 측에서 데이터를 읽는다. pump 작업은 읽는 스레드와 다른
 * 스레드에서 수행되어야 하고, 기록을 마치면 출력 스트림을 닫아야 본 스트림에서 EOF를 볼 수 있다.
 * pump 작업이 실패한 경우, pipe에서 EOF를 읽은 시점에 그 오류가 {@link IOException}으로 변환되어
 * read 메소드에서 발생한다. {@link #close()}는 pump 작업을 취소하고 pipe를 닫는다.
 * @author Kang-Woo Lee (ETRI)
 */
public class InputStreamFromOutputStream extends InputStream {
	private static final int DEFAULT_PIPE_SIZE = 32 * 1024;
	
	private final PipedInputStream m_pipe;
	private final Execution<Void> m_pump;
	private Throwable m_error;
	
	/**
	 * 주어진 pump 함수와 pipe 버퍼 크기로 입력 스트림을 생성한다.
	 * 생성 과정에서 pump 함수가 즉시 호출된다.
	 *
	 * @param pump	pipe 출력 스트림을 받아 데이터를 기록하는 {@link Execution}을 반환하는 함수.
	 * @param pipeSize	pipe 버퍼 크기(byte). 0보다 커야 한다.
	 * @throws NullPointerException	{@code pump}가 {@code null}인 경우.
	 * @throws IllegalArgumentException	{@code pipeSize}가 0 이하이거나, pipe 생성 또는 pump 함수
	 * 				호출 중 예외가 발생한 경우(원인 예외는 메시지 문자열로만 남는다).
	 */
	public InputStreamFromOutputStream(Function<OutputStream,Execution<Void>> pump, int pipeSize) {
		Preconditions.checkNotNull(pump, "pump is null");
		Preconditions.checkArgument(pipeSize > 0, "invalid pipe size: " + pipeSize);
		
		try {
			Tuple<PipedOutputStream, PipedInputStream> pipe = IOUtils.pipe(pipeSize);
			
			m_pipe = pipe._2;
			m_pump = pump.apply(pipe._1);
			m_pump.whenFailed(error -> m_error = error);
		}
		catch ( Exception e ) {
			throw new IllegalArgumentException("" + e);
		}
	}
	
	/**
	 * 주어진 pump 함수와 기본 pipe 버퍼 크기(32KB)로 입력 스트림을 생성한다.
	 * 생성 과정에서 pump 함수가 즉시 호출된다.
	 *
	 * @param pump	pipe 출력 스트림을 받아 데이터를 기록하는 {@link Execution}을 반환하는 함수.
	 * @throws NullPointerException	{@code pump}가 {@code null}인 경우.
	 * @throws IllegalArgumentException	pipe 생성 또는 pump 함수 호출 중 예외가 발생한 경우.
	 */
	public InputStreamFromOutputStream(Function<OutputStream,Execution<Void>> pump) {
		this(pump, DEFAULT_PIPE_SIZE);
	}
	
	@Override
	public void close() throws IOException {
		m_pump.cancel(true);
		m_pipe.close();
	}

	@Override
	public int read() throws IOException {
		int ret = m_pipe.read();
		if ( ret >= 0 || m_error == null ) {
			return ret;
		}
		
		throw toIOException(m_error);
	}

	@Override
    public int read(byte b[], int off, int len) throws IOException {
		int ret = m_pipe.read(b, off, len);
		if ( ret >= 0 || m_error == null ) {
			return ret;
		}

		throw toIOException(m_error);
	}
	
	private static IOException toIOException(Throwable error) {
		if ( error instanceof IOException ) {
			return (IOException)error;
		}
		return new IOException("" + error);
	}
}
