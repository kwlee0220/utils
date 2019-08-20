package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SequenceInputStream extends InputStream {
	private final FStream<InputStream> m_sources;
	private InputStream m_src;
	
	public static SequenceInputStream sequence(InputStream... srcs) {
		return new SequenceInputStream(FStream.of(srcs));
	}
	
	public static SequenceInputStream sequence(List<InputStream> srcs) {
		return new SequenceInputStream(FStream.from(srcs));
	}
	
	public SequenceInputStream(FStream<InputStream> sources) {
		m_sources = sources;
		m_src = sources.next().getOrNull();
	}

	@Override
	public int read() throws IOException {
		while ( m_src != null ) {
			int ret = m_src.read();
			if ( ret != -1 ) {
				return ret;
			}
			
			m_src = m_sources.next().getOrNull();
		}
		
		return -1;
	}

	@Override
    public int read(byte b[], int off, int len) throws IOException {
		while ( m_src != null ) {
			int nread = m_src.read(b, off, len);
			if ( nread >= 0 ) {
				return nread;
			}
			m_src = m_sources.next().getOrNull();
		}
		
		return -1;
    }
	
	@Override
    public int available() throws IOException {
        return m_src != null ? m_src.available() : 0;
    }
}
