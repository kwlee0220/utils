package utils.jdbc;

import static utils.Utilities.checkState;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

import utils.func.CheckedFunction;
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
	private JdbcProcessor m_proc;
	private Connection m_conn;
	private ResultSet m_rs;
	private CheckedFunction<Connection, ResultSet> m_exector;
	private CheckedFunction<ResultSet, T> m_deser;
	
	private JdbcRowSource(CheckedFunction<ResultSet, T> deserializer) {
		m_deser = deserializer;
	}
	
	private static CheckedFunction<ResultSet, List<Object>> deserializeAsObjectList() {
		return rs -> FStream.rangeClosed(1, rs.getMetaData().getColumnCount())
							.mapOrThrow(idx -> rs.getObject(idx))
							.toList();
	}
	
	public static <T> JdbcRowSource<T> select(CheckedFunction<ResultSet, T> deserializer) {
		return new JdbcRowSource<>(deserializer);
	}
	
	public static JdbcRowSource<List<Object>> selectAsValueList() {
		return new JdbcRowSource<>(deserializeAsObjectList());
	}
	
	public static JdbcRowSource<Object> selectAsObject() {
		return new JdbcRowSource<>(rs -> rs.getObject(1));
	}
	public static JdbcRowSource<String> selectAsString() {
		return new JdbcRowSource<>(rs -> rs.getString(1));
	}
	public static JdbcRowSource<Integer> selectAsInt() {
		return new JdbcRowSource<>(rs -> rs.getInt(1));
	}
	public static JdbcRowSource<Long> selectAsLong() {
		return new JdbcRowSource<>(rs -> rs.getLong(1));
	}
	
	public static JdbcRowSource<Tuple<Object,Object>> selectAsTuple2() {
		return new JdbcRowSource<>(rs -> Tuple.of(rs.getObject(1), rs.getObject(2)));
	}
	public static JdbcRowSource<Tuple3<Object,Object,Object>> selectAsTuple3() {
		return new JdbcRowSource<>(rs -> Tuple.of(rs.getObject(1), rs.getObject(2), rs.getObject(3)));
	}
	
	public JdbcRowSource<T> from(JdbcProcessor proc) {
		m_proc = proc;
		return this;
	}
	
	public JdbcRowSource<T> from(Connection conn) {
		m_conn = conn;
		return this;
	}
	
	public JdbcRowSource<T> from(ResultSet rs) {
		m_rs = rs;
		return this;
	}
	
	public JdbcRowSource<T> executeQuery(CheckedFunction<Connection, ResultSet> executor) {
		m_exector = executor;
		return this;
	}
	
	public JdbcRowSource<T> executeQuery(String sql) {
		m_exector = conn -> conn.createStatement().executeQuery(sql);
		return this;
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
		public FOption<T> next() {
			try {
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
}
