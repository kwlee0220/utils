package utils.jdbc;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.sf.cglib.proxy.MethodProxy;
import utils.CallHandler;
import utils.ProxyUtils;
import utils.Utilities;
import utils.func.CheckedFunctionX;
import utils.func.Try;
import utils.io.IOUtils;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcUtils {
	private JdbcUtils() {
		throw new AssertionError("Should not be called: " + JdbcUtils.class);
	}
	
//	public static boolean closeQuietly(Statement stmt) {
//		if ( stmt != null ) {
//			try {
//				stmt.close();
//				return true;
//			}
//			catch ( SQLException e ) { }
//		}
//		
//		return false;
//	}
//	
//	public static boolean closeQuietly(Connection conn) {
//		if ( conn != null ) {
//			try {
//				conn.close();
//				return true;
//			}
//			catch ( SQLException e ) { }
//		}
//		
//		return false;
//	}
	
	public static Stream<ResultSet> stream(ResultSet rs) throws SQLException {
		return StreamSupport.stream(new ResultSetSpliterator(rs), false);
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
		CallHandler<ResultSet> replacer = new CallHandler<ResultSet>() {
			@Override
			public boolean test(Method method) {
				return method.getName().equals("close")
					&& method.getDeclaringClass() == ResultSet.class;
			}
			
			@Override
			public Object intercept(ResultSet rs, Method method, Object[] args, MethodProxy proxy)
				throws Throwable {
				Connection conn = rs.getStatement().getConnection();

				Try.run(() -> proxy.invoke(rs, args));
				IOUtils.closeQuietly(conn);
				
				return null;
			}
		};
		
		return ProxyUtils.replaceAction(rset.getClass().getClassLoader(), rset, replacer);
	}
}
