package utils.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import utils.Preconditions;
import utils.func.CheckedFunctionX;
import utils.func.FOption;
import utils.io.IOUtils;
import utils.stream.FStreams.AbstractFStream;

/**
 * {@link ResultSet}의 각 행을 주어진 함수로 변환해 내보내는 {@link utils.stream.FStream}.
 * <p>
 * {@link JdbcUtils#fstream(ResultSet, CheckedFunctionX)}와 {@link JdbcProcessor#query}가 쓰는 유일한
 * ResultSet 순회 구현이다.
 * <ul>
 *   <li>행은 {@link #next()}가 불릴 때 지연해서 읽는다. 첫 행도 마찬가지다.</li>
 *   <li>마지막 행 뒤에는 {@link ResultSet#next()}를 다시 부르지 않고 계속 빈 값을 돌려준다.</li>
 *   <li>행 읽기나 변환이 {@link SQLException}으로 실패하면 {@link JdbcException}으로 감싸 던진다. 런타임 예외는
 *       그대로 전파된다. 실패해도 스트림은 닫히지 않으므로 호출자가 닫는다.</li>
 *   <li>닫으면 {@link ResultSet}을 닫는다(Statement·Connection은 닫지 않는다). 닫은 뒤 {@link #next()}는
 *       {@link IllegalStateException}이다({@link AbstractFStream} 계약).</li>
 * </ul>
 *
 * @param <T>	각 행을 변환한 결과의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
class ResultSetObjectFStream<T> extends AbstractFStream<T> {
	private final ResultSet m_rs;
	private final CheckedFunctionX<ResultSet,T,SQLException> m_deserializer;
	private boolean m_exhausted = false;
	
	ResultSetObjectFStream(ResultSet rs, CheckedFunctionX<ResultSet,T,SQLException> deserializer) {
		Preconditions.checkNotNullArgument(rs, "ResultSet is null");
		Preconditions.checkNotNullArgument(deserializer, "deserializer is null");
		
		m_rs = rs;
		m_deserializer = deserializer;
	}

	@Override
	protected void closeInGuard() throws Exception {
		IOUtils.closeQuietly(m_rs);
	}

	@Override
	protected FOption<T> nextInGuard() {
		if ( m_exhausted ) {
			return FOption.empty();
		}
		
		try {
			if ( !m_rs.next() ) {
				m_exhausted = true;
				return FOption.empty();
			}
			return FOption.of(m_deserializer.apply(m_rs));
		}
		catch ( SQLException e ) {
			// FStream.next()는 checked 예외를 던질 수 없으므로 JdbcException으로 감싼다.
			throw new JdbcException("failed to read a row", e);
		}
	}
}
