package utils.jdbc;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import utils.CallHandler;
import utils.ProxyUtils;
import utils.Utilities;
import utils.func.CheckedFunctionX;
import utils.func.Try;
import utils.io.IOUtils;
import utils.stream.FStream;

import net.sf.cglib.proxy.MethodProxy;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcUtils {
	private JdbcUtils() {
		throw new AssertionError("Should not be called: " + JdbcUtils.class);
	}
	
	/**
	 * 주어진 Instant 객체를 Timestamp 객체로 변환한다.
	 *
	 * @param instant	변환할 Instant 객체.
	 * @return	변환된 Timestamp 객체.
	 */
	public static Timestamp toTimestamp(Instant instant) {
		return Timestamp.from(instant);
	}
	
	public static List<Object> toColumnObjectList(ResultSet rs) throws SQLException {
		return FStream.range(1, rs.getMetaData().getColumnCount()+1)
						.mapOrThrow(idx -> rs.getObject(idx))
						.toList();
	}

	public static <T> FStream<T> fstream(ResultSet rs,
										CheckedFunctionX<ResultSet,T,SQLException> trans)
		throws SQLException {
		return FStream.from(new JdbcObjectIterator<T>(rs, trans));
	}

	public static <T> Stream<T> stream(ResultSet rs,
										CheckedFunctionX<ResultSet,T,SQLException> trans)
		throws SQLException {
		return Utilities.stream(new JdbcObjectIterator<T>(rs, trans));
	}

	public static ResultSet bindToConnection(ResultSet rset) {
		CallHandler replacer = new CallHandler() {
			@Override
			public boolean test(Method method) {
				return method.getName().equals("close")
					&& method.getDeclaringClass() == ResultSet.class;
			}
			
			@Override
			public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
				throws Throwable {
				ResultSet rs = (ResultSet)obj;
				Connection conn = rs.getStatement().getConnection();

				Try.run(() -> proxy.invoke(rs, args));
				IOUtils.closeQuietly(conn);
				
				return null;
			}
		};
		
		return (ResultSet)ProxyUtils.replaceAction(rset.getClass().getClassLoader(), rset, replacer);
	}
	
	public static String toPostgresInterval(Duration dur) {
		long seconds = dur.getSeconds();
		long absSeconds = Math.abs(seconds);
		
		long days = absSeconds / 86400;
		long hours = (absSeconds % 86400) / 3600;
		long minutes = (absSeconds % 3600) / 60;
		long secs = absSeconds % 60;
		
		StringBuilder sb = new StringBuilder();
		if ( days > 0 ) {
			sb.append(days).append(" days ");
		}
		if ( hours > 0 ) {
			sb.append(hours).append(" hours ");
		}
		if ( minutes > 0 ) {
			sb.append(minutes).append(" minutes ");
		}
		if ( secs > 0 || sb.length() == 0 ) {
			sb.append(secs).append(" seconds");
		}
		
		return sb.toString().trim();	
	}
}
