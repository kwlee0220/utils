package utils.jdbc;

import static utils.Utilities.checkState;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import utils.Throwables;
import utils.func.CheckedFunctionX;
import utils.func.FOption;
import utils.func.Tuple;
import utils.func.Tuple3;
import utils.func.Unchecked;
import utils.stream.FStream;
import utils.stream.FStreamException;
import utils.stream.FStreams.AbstractFStream;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcRowSource<T> {
	private final JdbcProcessor m_proc;
	private Connection m_conn;
	private ResultSet m_rs;
	private final CheckedFunctionX<Connection, ResultSet, SQLException> m_exector;
	private final CheckedFunctionX<ResultSet, T, SQLException> m_deser;
	
	private JdbcRowSource(JdbcProcessor proc, Connection conn, ResultSet rs,
							CheckedFunctionX<Connection, ResultSet, SQLException> exector,
							CheckedFunctionX<ResultSet, T, SQLException> deserializer) {
		m_proc = proc;
		m_conn = conn;
		m_rs = rs;
		m_exector = exector;
		m_deser = deserializer;
	}
	
	public static <T> WaitRowSource<T> select(CheckedFunctionX<ResultSet, T, SQLException> deserializer) {
		return new WaitRowSource<>(deserializer);
	}
	
	public static WaitRowSource<List<Object>> selectAsValueList() {
		return new WaitRowSource<>(deserializeAsObjectList());
	}
	
	public static WaitRowSource<Object> selectAsObject() {
		return new WaitRowSource<>(rs -> rs.getObject(1));
	}
	public static WaitRowSource<String> selectAsString() {
		return new WaitRowSource<>(rs -> rs.getString(1));
	}
	public static WaitRowSource<Integer> selectAsInt() {
		return new WaitRowSource<>(rs -> rs.getInt(1));
	}
	public static WaitRowSource<Long> selectAsLong() {
		return new WaitRowSource<>(rs -> rs.getLong(1));
	}
	
	public static WaitRowSource<Tuple<Object,Object>> selectAsTuple2() {
		return new WaitRowSource<>(rs -> Tuple.of(rs.getObject(1), rs.getObject(2)));
	}
	public static WaitRowSource<Tuple3<Object,Object,Object>> selectAsTuple3() {
		return new WaitRowSource<>(rs -> Tuple.of(rs.getObject(1), rs.getObject(2), rs.getObject(3)));
	}
	
	class OnSubscribe implements ObservableOnSubscribe<T> {
		@Override
		public void subscribe(@NonNull ObservableEmitter<@NonNull T> emitter) throws Throwable {
			try {
				if ( m_rs == null ) {
					if ( m_conn == null ) {
						checkState(m_proc != null, "Connection has not set.");
						m_conn = m_proc.connect();
					}
					checkState(m_exector != null, "Query executor has not set.");
					m_rs = m_exector.apply(m_conn);
				}
				
				while ( m_rs.next() ) {
					emitter.onNext(m_deser.apply(m_rs));
				}
				emitter.onComplete();
			}
			catch ( Throwable e ) {
				emitter.onError(e);
			}
		}
	}
	public Observable<T> observe() {
		return Observable.create(new OnSubscribe());
	}

	class RowStream extends AbstractFStream<T> {
		@Override
		protected void closeInGuard() throws Exception {
			Unchecked.runOrIgnore(m_rs::close);
			if ( m_proc != null ) {
				m_conn.close();
			}
		}
		
		@Override
		public FOption<T> nextInGuard() {
			try {
				// 처음으로 레코드를 fetch할 때 SQL 처리를 수행하여 ResultSet 객체를 생성한다.
				if ( m_rs == null ) {
					if ( m_conn == null ) {
						checkState(m_proc != null, "Connection has not set.");
						m_conn = m_proc.connect();
					}
					checkState(m_exector != null, "Query executor has not set.");
					m_rs = m_exector.apply(m_conn);
				}
				
				return m_rs.next() ? FOption.of(m_deser.apply(m_rs)) : FOption.empty();
			}
			catch ( SQLException e ) {
				Throwables.sneakyThrow(e);
				throw new AssertionError("Should not be here");
			}
			catch ( Throwable e ) {
				throw new FStreamException(e);
			}
		}
	}
	public FStream<T> fstream() {
		return new RowStream();
	}
	
	public List<T> toList() {
		return observe().toList().blockingGet();
	}
	
	public FOption<T> first() {
		return fstream().findFirst();
	}
	
	public static class WaitRowSource<T> {
		private final CheckedFunctionX<ResultSet, T, SQLException> m_deser;

		private WaitRowSource(CheckedFunctionX<ResultSet, T, SQLException> deser) {
			m_deser = deser;
		}
		
		public WaitExecutor<T> from(JdbcProcessor proc) {
			return new WaitExecutor<>(m_deser, proc, null, null);
		}
		
		public WaitExecutor<T> from(Connection conn) {
			return new WaitExecutor<>(m_deser, null, conn, null);
		}
		
		public JdbcRowSource<T> from(ResultSet rs) {
			return new JdbcRowSource<>(null, null, rs, null, m_deser);
		}
	}
	
	public static class WaitExecutor<T> {
		private final CheckedFunctionX<ResultSet, T, SQLException> m_deser;
		private final JdbcProcessor m_proc;
		private final Connection m_conn;
		private final ResultSet m_rs;

		private WaitExecutor(CheckedFunctionX<ResultSet, T, SQLException> deser, JdbcProcessor proc,
							Connection conn, ResultSet rs) {
			m_deser = deser;
			m_proc = proc;
			m_conn = conn;
			m_rs = rs;
		}
		
		public JdbcRowSource<T> executeQuery(CheckedFunctionX<Connection, ResultSet, SQLException> executor) {
			return new JdbcRowSource<>(m_proc, m_conn, m_rs, executor, m_deser);
		}
		
		public JdbcRowSource<T> executeQuery(String sql) {
			CheckedFunctionX<Connection, ResultSet, SQLException> exector = conn -> conn.createStatement().executeQuery(sql);
			return new JdbcRowSource<>(m_proc, m_conn, m_rs, exector, m_deser);
		}
	}
	
	private static CheckedFunctionX<ResultSet, List<Object>, SQLException> deserializeAsObjectList() {
		return rs -> FStream.range(1, rs.getMetaData().getColumnCount()+1)
							.mapOrThrow(idx -> rs.getObject(idx))
							.toList();
	}
}
