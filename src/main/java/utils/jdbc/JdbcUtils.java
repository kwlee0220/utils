package utils.jdbc;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
	
	public static Timestamp toTimestamp(Instant instant) {
		return Timestamp.from(instant);
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
}
