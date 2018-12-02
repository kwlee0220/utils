package utils.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.vavr.control.Option;
import utils.stream.FStream;
import utils.stream.FStreamException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ResultSetFStream implements FStream<ResultSet> {
	private final ResultSet m_rs;
	
	ResultSetFStream(ResultSet rs) {
		m_rs = rs;
	}

	@Override
	public void close() throws Exception {
		m_rs.close();
	}

	@Override
	public Option<ResultSet> next() {
		try {
			return m_rs.next() ? Option.some(m_rs) : Option.none();
		}
		catch ( SQLException e ) {
			throw new FStreamException(e);
		}
	}

}
