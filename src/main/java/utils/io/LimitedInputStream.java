package utils.io;

import java.io.IOException;
import java.io.InputStream;

import com.google.common.base.Preconditions;

import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class LimitedInputStream extends InputStream {
	private final InputStream m_src;
	private int m_remains;
	
	public LimitedInputStream(InputStream src, int limit) {
		Utilities.checkNotNullArgument(src, "Source InputStream");
		Preconditions.checkArgument(limit >= 0, "limit >= 0");
		
		m_src = src;
		m_remains = limit;
	}

	@Override
	public int read() throws IOException {
		if ( m_remains == 0 ) {
			return -1;
		}
		
		--m_remains;
		return m_src.read();
	}

	@Override
    public int read(byte b[], int off, int len) throws IOException {
		if ( m_remains == 0 ) {
			return -1;
		}
		
		int nbytes = Math.min(m_remains, len);
		int nreads = m_src.read(b, off, nbytes);
		if ( nreads < 0 ) {
			return -1;
		}
		m_remains -= nreads;
		
		return nreads;
    }
}