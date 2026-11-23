package utils.rpc.restful;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.async.AbstractThreadedExecution;
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

		@Override
		protected Duration getCancelWait() {
			return Duration.ofMillis(300);
		}
	}

	/**
	 * 취소 요청을 받아도 {@code release}가 열릴 때까지 끝나지 않는 실행(멈추는 데 오래 걸리는 연산).
	 */
	private static class SlowStopExecution extends AbstractThreadedExecution<Void> {
		private final CountDownLatch m_release = new CountDownLatch(1);

		@Override
		protected Void executeWork() {
			while ( true ) {
				try {
					m_release.await();
					return null;
				}
				catch ( InterruptedException ignored ) { }
			}
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

	// ----- 정상 완료 + 종료 세션 보존 -----

	@Test
	public void testCompletedResultCanBeReadRepeatedly() throws Exception {
		Map<String,JsonNode> outputs = Map.of("r", MAPPER.readTree("7"));
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofMillis(50)), false, outputs);

		RpcResponseMessage started = server.start(emptyRequest());
		String sessionUrl = started.getSessionEndpoint();
		Assertions.assertNotNull(sessionUrl);

		RpcResponseMessage resp = waitForTermination(server, started);
		Assertions.assertEquals(RpcState.COMPLETED, resp.getState());
		Assertions.assertEquals(outputs, resp.getOutputs());

		// 종료된 세션은 보존 시간 동안 남아, 다시 조회해도 같은 결과를 돌려준다(응답을 잃은 클라이언트의 재조회).
		RpcResponseMessage again = server.status(sessionUrl);
		Assertions.assertEquals(RpcState.COMPLETED, again.getState());
		Assertions.assertEquals(outputs, again.getOutputs());
	}

	@Test
	public void testStartOfFastExecutionReturnsRunning() throws Exception {
		// 시작 대기 중에 이미 끝난 연산도 시작 응답은 RUNNING이고, 결과는 status로 회수한다.
		// 끝나는 시점이 경쟁이므로 여러 번 시도한다.
		Map<String,JsonNode> outputs = Map.of("r", MAPPER.readTree("3"));
		for ( int i = 0; i < 20; ++i ) {
			TestServer server = new TestServer(AsyncExecutions.idle(Duration.ZERO), false, outputs);

			RpcResponseMessage started = server.start(emptyRequest());
			Assertions.assertEquals(RpcState.RUNNING, started.getState());
			Assertions.assertNotNull(started.getSessionEndpoint());

			RpcResponseMessage resp = waitForTermination(server, server.status(started.getSessionEndpoint()));
			Assertions.assertEquals(RpcState.COMPLETED, resp.getState());
			Assertions.assertEquals(outputs, resp.getOutputs());
		}
	}

	// ----- 진행 중 조회 / 취소 -----

	@Test
	public void testRefusedCancelIsNotAccepted() throws Exception {
		// 취소를 거부하는 연산: 계속 실행 중이어도 "멈추는 중"으로 답하면 안 된다.
		SlowStopExecution exec = new SlowStopExecution() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return false;
			}
		};
		TestServer server = new TestServer(exec, false, Map.of());
		RpcResponseMessage started = server.start(emptyRequest());

		RpcCancelResult result = server.cancel(started.getSessionEndpoint());
		Assertions.assertFalse(result.accepted());
		Assertions.assertEquals(RpcState.RUNNING, result.response().getState());
		exec.m_release.countDown();
	}

	@Test
	public void testRunningThenCancel() throws Exception {
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofSeconds(10)), false, Map.of());

		RpcResponseMessage started = server.start(emptyRequest());
		Assertions.assertEquals(RpcState.RUNNING, started.getState());

		RpcCancelResult result = server.cancel(started.getSessionEndpoint());
		Assertions.assertTrue(result.accepted());
		Assertions.assertTrue(result.isCancelled());
		Assertions.assertEquals(RpcState.CANCELLED, result.response().getState());

		// 이미 취소된 세션에 다시 취소하면 받아들여진 것으로 본다.
		Assertions.assertTrue(server.cancel(started.getSessionEndpoint()).isCancelled());
	}

	@Test
	public void testCancelAlreadyTerminatedReturnsTerminalState() throws Exception {
		Map<String,JsonNode> outputs = Map.of("r", MAPPER.readTree("1"));
		TestServer server = new TestServer(AsyncExecutions.idle(Duration.ofMillis(50)), false, outputs);

		RpcResponseMessage started = server.start(emptyRequest());
		String sessionUrl = started.getSessionEndpoint();
		Thread.sleep(150);

		RpcCancelResult cancelled = server.cancel(sessionUrl);
		Assertions.assertFalse(cancelled.accepted());
		Assertions.assertEquals(RpcState.COMPLETED, cancelled.response().getState());

		// 완료 직후 들어온 취소가 결과를 지우지 않는다. 클라이언트는 취소가 거부된 뒤 결과를 조회한다.
		RpcResponseMessage result = server.status(sessionUrl);
		Assertions.assertEquals(RpcState.COMPLETED, result.getState());
		Assertions.assertEquals(outputs, result.getOutputs());
	}

	@Test
	public void testCancelOfSlowStoppingExecutionReturnsRunningWithinWait() throws Exception {
		SlowStopExecution exec = new SlowStopExecution();
		TestServer server = new TestServer(exec, false, Map.of());
		RpcResponseMessage started = server.start(emptyRequest());
		String sessionUrl = started.getSessionEndpoint();

		long t0 = System.nanoTime();
		RpcCancelResult result = server.cancel(sessionUrl);
		long elapsedMillis = (System.nanoTime() - t0) / 1_000_000;
		// 취소 대기 시간(300ms) 안에 끝나지 않으면 기다리지 않고 RUNNING(멈추는 중)으로 답한다.
		Assertions.assertTrue(result.isStopping());
		Assertions.assertTrue(elapsedMillis < 2000, "cancel waited too long: " + elapsedMillis + "ms");

		// 멈추는 중인 세션에 다시 취소해도 cancelTimeout(3초)에 묶이지 않고 취소 대기 시간 안에 답한다.
		t0 = System.nanoTime();
		RpcCancelResult again = server.cancel(sessionUrl);
		elapsedMillis = (System.nanoTime() - t0) / 1_000_000;
		Assertions.assertTrue(again.isStopping());
		Assertions.assertTrue(elapsedMillis < 1500, "repeated cancel waited too long: " + elapsedMillis + "ms");

		exec.m_release.countDown();
		RpcResponseMessage last = waitForTermination(server, server.status(sessionUrl));
		Assertions.assertNotEquals(RpcState.RUNNING, last.getState());
	}
}
