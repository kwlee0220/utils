package utils.rpc.restful;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.async.StartableExecution;
import utils.async.op.AsyncExecutions;
import utils.json.JacksonUtils;
import utils.rpc.restful.AsyncRpcSession;
import utils.rpc.restful.RESTfulAsyncRpcServer;
import utils.rpc.restful.RpcRequestMessage;
import utils.rpc.restful.RpcResponseMessage;
import utils.rpc.restful.RpcState;


/**
 * {@link RESTfulAsyncRpcServer}의 세션 수명주기 로직 테스트.
 * <p>
 * 추상 클래스이므로, 동작을 제어할 수 있는 경량 하위 클래스({@link TestServer})와 인메모리 실행
 * ({@link AsyncExecutions#idle})으로 start/status/cancel 경로와 엣지 케이스를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulAsyncRpcServerTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;

	/**
	 * 미리 지정한 실행을 그대로 사용하는 테스트용 서버.
	 */
	private static class TestServer extends RESTfulAsyncRpcServer {
		private final StartableExecution<?> m_exec;
		private final boolean m_failCreate;
		private final Map<String,JsonNode> m_outputs;
		private final AtomicInteger m_idGen = new AtomicInteger();

		TestServer(StartableExecution<?> exec, boolean failCreate, Map<String,JsonNode> outputs) {
			m_exec = exec;
			m_failCreate = failCreate;
			m_outputs = outputs;
		}

		@Override
		protected StartableExecution<?> createExecution(RpcRequestMessage reqMsg) throws IOException {
			if ( m_failCreate ) {
				throw new IOException("createExecution failed");
			}
			return m_exec;
		}

		@Override
		protected Map<String,JsonNode> getExecutionOutput(StartableExecution<?> execution) {
			return m_outputs;
		}

		@Override
		protected AsyncRpcSession allocateSession(StartableExecution<?> execution) {
			return new AsyncRpcSession("/sessions/" + m_idGen.incrementAndGet(), execution);
		}

		@Override
		protected void releaseSession(AsyncRpcSession session) { }

		@Override
		protected Duration getStartTimeout() {
			return Duration.ofSeconds(2);
		}
	}

	private RpcRequestMessage emptyRequest() {
		return new RpcRequestMessage(Map.of());
	}

	private RpcResponseMessage waitForTermination(RESTfulAsyncRpcServer server,
														RpcResponseMessage resp) throws InterruptedException {
		String sessionUrl = resp.getSessionEndpoint();
		long deadline = System.currentTimeMillis() + 5000;
		while ( resp.getState() == RpcState.RUNNING && System.currentTimeMillis() < deadline ) {
			Thread.sleep(20);
			resp = server.status(sessionUrl);
		}
		return resp;
	}

	// ----- 인자 검증 -----

	@Test
	public void testStartNullRequestRejected() {
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofMillis(10)), false, Map.of());
		Assertions.assertThrows(IllegalArgumentException.class, () -> server.start(null));
	}

	@Test
	public void testStatusNullRejected() {
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofMillis(10)), false, Map.of());
		Assertions.assertThrows(IllegalArgumentException.class, () -> server.status(null));
	}

	@Test
	public void testStatusUnknownSessionRejected() {
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofMillis(10)), false, Map.of());
		Assertions.assertNull(server.status("/sessions/nope"));
	}

	@Test
	public void testCancelUnknownSessionRejected() {
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofMillis(10)), false, Map.of());
		Assertions.assertNull(server.cancel("/sessions/nope"));
	}

	// ----- createExecution 실패 -----

	@Test
	public void testCreateExecutionFailureReturnsFailedWithNullSession() {
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofMillis(10)), true, Map.of());

		RpcResponseMessage resp = server.start(emptyRequest());

		Assertions.assertEquals(RpcState.FAILED, resp.getState());
		Assertions.assertNull(resp.getSessionEndpoint());
		Assertions.assertNotNull(resp.getError());
	}

	// ----- 정상 완료 + read-once -----

	@Test
	public void testCompletedReturnsOutputsThenSessionRemoved() throws Exception {
		Map<String,JsonNode> outputs = Map.of("r", MAPPER.readTree("7"));
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofMillis(50)), false, outputs);

		RpcResponseMessage started = server.start(emptyRequest());
		String sessionUrl = started.getSessionEndpoint();
		Assertions.assertNotNull(sessionUrl);

		RpcResponseMessage resp = waitForTermination(server, started);
		Assertions.assertEquals(RpcState.COMPLETED, resp.getState());
		Assertions.assertEquals(outputs, resp.getOutputs());

		// 종료 상태를 한 번 회수하면 세션이 제거되어 이후 조회는 null을 반환한다(read-once).
		Assertions.assertNull(server.status(sessionUrl));
	}

	// ----- 진행 중 조회 / 취소 -----

	@Test
	public void testRunningThenCancel() throws Exception {
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofSeconds(10)), false, Map.of());

		RpcResponseMessage started = server.start(emptyRequest());
		Assertions.assertEquals(RpcState.RUNNING, started.getState());

		RpcResponseMessage resp = server.cancel(started.getSessionEndpoint());
		Assertions.assertEquals(RpcState.CANCELLED, resp.getState());
	}

	@Test
	public void testCancelAlreadyTerminatedReturnsTerminalState() throws Exception {
		Map<String,JsonNode> outputs = Map.of("r", MAPPER.readTree("1"));
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofMillis(50)), false, outputs);

		RpcResponseMessage started = server.start(emptyRequest());
		// 완료될 때까지 기다린 뒤 status로 한 번 회수하면 세션이 제거되므로, 여기서는 바로 cancel을 시험한다.
		String sessionUrl = started.getSessionEndpoint();
		Thread.sleep(150);

		RpcResponseMessage resp = server.cancel(sessionUrl);
		Assertions.assertEquals(RpcState.COMPLETED, resp.getState());
	}
}
