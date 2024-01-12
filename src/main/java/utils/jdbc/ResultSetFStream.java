package utils.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import utils.func.FOption;
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
	public FOption<ResultSet> next() {
		try {
			return m_rs.next() ? FOption.of(m_rs) : FOption.empty();
		}
		catch ( SQLException e ) {
			throw new FStreamException(e);
		}
	}

}
