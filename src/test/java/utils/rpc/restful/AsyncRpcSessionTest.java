package utils.rpc.restful;

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.async.StartableExecution;
import utils.async.op.AsyncExecutions;
import utils.rpc.restful.AsyncRpcSession;


/**
 * {@link AsyncRpcSession} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncRpcSessionTest {
	@Test
	public void testGetters() {
		StartableExecution<?> exec = AsyncExecutions.idle(Duration.ofMillis(10));
		try ( AsyncRpcSession session = new AsyncRpcSession("/sessions/1", exec) ) {
			Assertions.assertEquals("/sessions/1", session.getSessionEndpoint());
			Assertions.assertSame(exec, session.getExecution());
		}
	}

	@Test
	public void testCloseDoesNotThrow() {
		// 시작하지 않은 실행에 대해 close()를 호출해도 예외 없이 처리되어야 한다.
		StartableExecution<?> exec = AsyncExecutions.idle(Duration.ofMillis(10));
		AsyncRpcSession session = new AsyncRpcSession("/sessions/1", exec);

		Assertions.assertDoesNotThrow(session::close);
	}
}
