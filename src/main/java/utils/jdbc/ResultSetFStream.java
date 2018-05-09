package utils.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import utils.func.FOptional;
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
	public FOptional<ResultSet> next() {
		try {
			return m_rs.next() ? FOptional.some(m_rs) : FOptional.none();
		}
		catch ( SQLException e ) {
			throw new FStreamException(e);
		}
	}

}
