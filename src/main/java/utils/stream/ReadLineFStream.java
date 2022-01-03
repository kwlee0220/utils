/**
 * 
 */
package utils.stream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;

import utils.func.FOption;
import utils.io.IOUtils;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ReadLineFStream implements FStream<String> {
	private final BufferedReader m_lineReader;
	private boolean m_closed = false;
	
	public ReadLineFStream(Reader reader) {
		m_lineReader = (reader instanceof BufferedReader)
							? (BufferedReader)reader
							: new BufferedReader(reader);
	}
	
	public ReadLineFStream(InputStream is) {
		m_lineReader = new BufferedReader(new InputStreamReader(is));
	}

	@Override
	public FOption<String> next() {
		try {
			if ( m_closed ) {
				return FOption.empty();
			}
			
			String line = m_lineReader.readLine();
			if ( line == null ) {
				IOUtils.closeQuietly(m_lineReader);
				m_closed = true;
				
				return FOption.empty();
			}
			else {
				return FOption.of(line);
			}
		}
		catch ( IOException e ) {
			IOUtils.closeQuietly(m_lineReader);
			m_closed = true;
			
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() throws Exception {
		if ( !m_closed ) {
			IOUtils.closeQuietly(m_lineReader);
			m_closed = true;
		}
	}

}
