package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.function.Function;

import utils.Utilities;
import utils.async.Execution;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class InputStreamFromOutputStream extends InputStream {
	private static final int DEFAULT_PIPE_SIZE = 32 * 1024;
	
	private final PipedInputStream m_pipe;
	private final Execution<Long> m_pump;
	private Throwable m_error;
	
	public InputStreamFromOutputStream(Function<OutputStream,Execution<Long>> pump, int pipeSize) {
		Utilities.checkNotNullArgument(pump, "pump");
		Utilities.checkArgument(pipeSize > 0, "invalid pipe size: " + pipeSize);
		
		try {
			PipedOutputStream pipeOut = new PipedOutputStream();
			m_pipe = new PipedInputStream(pipeOut, pipeSize);
			m_pump = pump.apply(pipeOut);
		}
		catch ( Exception e ) {
			throw new IllegalArgumentException("" + e);
		}
	}
	
	public InputStreamFromOutputStream(Function<OutputStream,Execution<Long>> pump) {
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
