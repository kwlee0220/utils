package utils.jdbc;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;
import java.util.stream.Stream;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.proxy.NoOp;
import utils.Errors;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcUtils {
	private JdbcUtils() {
		throw new AssertionError("Should not be called: " + JdbcUtils.class);
	}
	
	public static boolean closeQuietly(Statement stmt) {
		if ( stmt != null ) {
			try {
				stmt.close();
				return true;
			}
			catch ( SQLException e ) { }
		}
		
		return false;
	}
	
	public static boolean closeQuietly(Connection conn) {
		if ( conn != null ) {
			try {
				conn.close();
				return true;
			}
			catch ( SQLException e ) { }
		}
		
		return false;
	}

	public static <T> Stream<T> stream(ResultSet rs, Function<ResultSet,T> trans)
		throws SQLException {
		return Utilities.stream(new JdbcObjectIterator<T>(rs, trans));
	}
	
	public static ResultSet bindToConnection(ResultSet rset) {
		CallbackFilter filter = new CallbackFilter() {
			@Override
			public int accept(Method method) {
				if ( method.getDeclaringClass() == ResultSet.class
					&& method.getName().equals("close") ) {
					return 1;
				}
				else {
					return 0;
				}
			}
		};
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
				throws Throwable {
				Errors.runQuietly(()->proxy.invokeSuper(obj, args));
				closeQuietly(rset.getStatement().getConnection());
				
				return null;
			}
		};
		
		return (ResultSet)Enhancer.create(rset.getClass(), rset.getClass().getInterfaces(),
										filter, new Callback[]{NoOp.INSTANCE, interceptor});
	}
}
