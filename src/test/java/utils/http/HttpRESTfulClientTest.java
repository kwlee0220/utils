package utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class HttpRESTfulClientTest {
	@Test
	public void throwException_wraps_remote_checked_exception() throws Exception {
		HttpRESTfulClient client = HttpRESTfulClient.newDefaultClient();
		String respBody = "{\"code\":\"java.io.IOException\",\"message\":\"disk full\"}";

		InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
														() -> invokeThrowException(client, respBody));
		RESTfulRemoteException remote
			= assertInstanceOf(RESTfulRemoteException.class, thrown.getTargetException());

		assertInstanceOf(IOException.class, remote.getCause());
		assertEquals("disk full", remote.getCause().getMessage());
	}

	private Object invokeThrowException(HttpRESTfulClient client, String respBody) throws Exception {
		Method method = HttpRESTfulClient.class.getDeclaredMethod("throwException", String.class);
		method.setAccessible(true);
		return method.invoke(client, respBody);
	}
}
