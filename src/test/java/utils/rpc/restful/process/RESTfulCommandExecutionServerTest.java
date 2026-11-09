package utils.rpc.restful.process;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.KeyValue;
import utils.async.command.CommandVariable;
import utils.json.JacksonUtils;
import utils.rpc.restful.RpcRequestMessage;
import utils.rpc.restful.RpcResponseMessage;
import utils.rpc.restful.RpcState;
import utils.rpc.restful.process.CommandVariableSerDe;
import utils.rpc.restful.process.DefaultCommandVariableSerDe;
import utils.rpc.restful.process.RESTfulCommandExecutionServer;


/**
 * {@link RESTfulCommandExecutionServer} 동작 테스트.
 * <p>
 * 외부 명령 실행은 Linux sub-process({@code /bin/sh})를 사용하므로 Linux 환경에서만 수행된다.
 * createExecution이 반환하는 연산 객체는 비공개 내부 타입이므로, 서버의 동작은 공개 수명주기
 * 메소드({@code start}/{@code status}/{@code cancel})를 통해 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulCommandExecutionServerTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;
	private static final CommandVariableSerDe SERDE = new DefaultCommandVariableSerDe(MAPPER);

	@TempDir File m_workDir;

	@BeforeEach
	public void setup() {
		Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
							"Linux 환경에서만 동작");
	}

	private File writeDescriptor(String descJson) throws IOException {
		File descFile = new File(m_workDir, "op.json");
		Files.writeString(descFile.toPath(), descJson, StandardCharsets.UTF_8);
		return descFile;
	}

	private JsonNode json(String literal) throws IOException {
		return MAPPER.readTree(literal);
	}

	// ----- 생성자 -----

	@Test
	public void testNullOpDescFileRejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new RESTfulCommandExecutionServer(null, SERDE));
	}

	@Test
	public void testMissingDescriptorFileThrowsIOException() {
		File missing = new File(m_workDir, "no-such-file.json");
		Assertions.assertThrows(IOException.class,
								() -> new RESTfulCommandExecutionServer(missing, SERDE));
	}

	// ----- timeout 재정의 -----

	@Test
	public void testStartTimeoutReflectsDescriptor() throws IOException {
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/true\"],\"outputVariables\":[],\"startTimeout\":\"7s\"}");
		RESTfulCommandExecutionServer server = new RESTfulCommandExecutionServer(descFile, SERDE);

		Assertions.assertEquals(Duration.ofSeconds(7), server.getStartTimeout());
	}

	@Test
	public void testSessionRetainTimeoutReflectsDescriptor() throws IOException {
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/true\"],\"outputVariables\":[],\"sessionRetainTimeout\":\"3s\"}");
		RESTfulCommandExecutionServer server = new RESTfulCommandExecutionServer(descFile, SERDE);

		Assertions.assertEquals(Duration.ofSeconds(3), server.getSessionRetainTimeout());
	}

	// ----- start() end-to-end -----

	@Test
	public void testEndToEndCompletedWithOutput() throws Exception {
		// 입력 파일(payload)을 출력 파일(result)로 복사하는 명령.
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/sh\",\"-c\",\"cat ${payload:path} > ${result:path}\"],"
				+ "\"outputVariables\":[\"result\"],\"timeout\":\"30s\"}");
		RESTfulCommandExecutionServer server = new RESTfulCommandExecutionServer(descFile, SERDE);

		RpcRequestMessage reqMsg
				= new RpcRequestMessage(Map.of("payload", json("{\"v\":7}")));
		RpcResponseMessage resp = waitForTermination(server, server.start(reqMsg));

		Assertions.assertEquals(RpcState.COMPLETED, resp.getState());
		Assertions.assertEquals(json("{\"v\":7}"), resp.getOutputs().get("result"));
	}

	@Test
	public void testEndToEndInOutVariableWithRequestOutputs() throws Exception {
		// 'data'는 입력이자 출력 변수이다. 요청의 outputs에도 'data'가 지정되어 있으나,
		// 중복 등록으로 실패하지 않고 입력 값이 그대로 사용되어야 한다.
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/sh\",\"-c\",\"cat ${data:path} > ${result:path}\"],"
				+ "\"outputVariables\":[\"data\",\"result\"],\"timeout\":\"30s\"}");
		RESTfulCommandExecutionServer server = new RESTfulCommandExecutionServer(descFile, SERDE);

		RpcRequestMessage reqMsg
				= new RpcRequestMessage(Map.of("data", json("{\"v\":1}")),
										Map.of("data", json("{\"v\":999}"), "result", json("null")));
		RpcResponseMessage resp = waitForTermination(server, server.start(reqMsg));

		Assertions.assertEquals(RpcState.COMPLETED, resp.getState());
		Assertions.assertEquals(json("{\"v\":1}"), resp.getOutputs().get("data"));
		Assertions.assertEquals(json("{\"v\":1}"), resp.getOutputs().get("result"));
	}

	@Test
	public void testEndToEndFailedOnNonZeroExit() throws Exception {
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/sh\",\"-c\",\"exit 3\"],"
				+ "\"outputVariables\":[],\"timeout\":\"30s\"}");
		RESTfulCommandExecutionServer server = new RESTfulCommandExecutionServer(descFile, SERDE);

		RpcRequestMessage reqMsg = new RpcRequestMessage(Map.of());
		RpcResponseMessage resp = waitForTermination(server, server.start(reqMsg));

		Assertions.assertEquals(RpcState.FAILED, resp.getState());
		Assertions.assertNotNull(resp.getError());
	}

	@Test
	public void testEndToEndCancelled() throws Exception {
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/sleep\",\"10\"],"
				+ "\"outputVariables\":[],\"timeout\":\"30s\"}");
		RESTfulCommandExecutionServer server = new RESTfulCommandExecutionServer(descFile, SERDE);

		RpcRequestMessage reqMsg = new RpcRequestMessage(Map.of());
		RpcResponseMessage started = server.start(reqMsg);
		Assertions.assertEquals(RpcState.RUNNING, started.getState());

		RpcResponseMessage resp = server.cancel(started.getSessionEndpoint());
		Assertions.assertEquals(RpcState.CANCELLED, resp.getState());
	}

	// ----- 디스크립터·세션 조회 -----

	@Test
	public void testGetDescriptorAndHasSessions() throws Exception {
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/sleep\",\"10\"],\"outputVariables\":[\"x\"],\"timeout\":\"30s\"}");
		RESTfulCommandExecutionServer server = new RESTfulCommandExecutionServer(descFile, SERDE);
		Assertions.assertEquals(List.of("x"), server.getDescriptor().getOutputVariables());
		Assertions.assertFalse(server.hasSessions());

		RpcResponseMessage started = server.start(new RpcRequestMessage(Map.of()));
		Assertions.assertTrue(server.hasSessions());

		// 취소 결과를 회수하면(read-once) 세션이 제거된다.
		server.cancel(started.getSessionEndpoint());
		Assertions.assertFalse(server.hasSessions());
	}

	@Test
	public void testCancelAllSessions() throws Exception {
		// 서버 종료 시: 수행 중인 연산을 모두 취소하고 세션 디렉토리를 정리한다.
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/sleep\",\"30\"],\"outputVariables\":[],\"timeout\":\"1m\"}");
		RESTfulCommandExecutionServer server = new RESTfulCommandExecutionServer(descFile, SERDE);
		Assertions.assertEquals(m_workDir, server.getWorkingDirectory());

		RpcResponseMessage s1 = server.start(new RpcRequestMessage(Map.of()));
		RpcResponseMessage s2 = server.start(new RpcRequestMessage(Map.of()));
		Assertions.assertEquals(RpcState.RUNNING, s1.getState());
		Assertions.assertEquals(RpcState.RUNNING, s2.getState());

		long started = System.currentTimeMillis();
		server.cancelAllSessions(Duration.ofSeconds(5));
		Assertions.assertTrue(System.currentTimeMillis() - started < 5000);

		Assertions.assertEquals(RpcState.CANCELLED, server.status(s1.getSessionEndpoint()).getState());
		Assertions.assertEquals(RpcState.CANCELLED, server.status(s2.getSessionEndpoint()).getState());
		// 세션 디렉토리는 종료 처리(releaseSession)에서 지워진다.
		long deadline = System.currentTimeMillis() + 2000;
		File[] left;
		while ( (left = m_workDir.listFiles((d, n) -> n.startsWith("session-"))).length > 0
				&& System.currentTimeMillis() < deadline ) {
			Thread.sleep(50);
		}
		Assertions.assertEquals(0, left.length);
	}

	// ----- 출력 변수 준비 -----

	@Test
	public void testOutputVariablesUseDeserializeOutput() throws Exception {
		// 입력 변수는 deserialize, 출력 변수는 deserializeOutput으로 만든다(요청에 지정된 출력이든 아니든).
		// 입력과 이름이 같은 출력 변수는 입력 변수를 그대로 쓴다.
		List<String> inputCalls = new CopyOnWriteArrayList<>();
		List<String> outputCalls = new CopyOnWriteArrayList<>();
		CommandVariableSerDe recording = new CommandVariableSerDe() {
			@Override
			public CommandVariable deserialize(String id, File cvDir, JsonNode jnode) throws IOException {
				inputCalls.add(id);
				return SERDE.deserialize(id, cvDir, jnode);
			}
			@Override
			public CommandVariable deserializeOutput(String id, File cvDir, JsonNode jnode) throws IOException {
				outputCalls.add(id);
				return SERDE.deserialize(id, cvDir, jnode);
			}
			@Override
			public KeyValue<String,JsonNode> serialize(CommandVariable var) throws IOException {
				return SERDE.serialize(var);
			}
		};
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/sh\",\"-c\",\"cat ${data:path} > ${a:path}; cat ${data:path} > ${b:path}\"],"
				+ "\"outputVariables\":[\"data\",\"a\",\"b\"],\"timeout\":\"30s\"}");
		RESTfulCommandExecutionServer server = new RESTfulCommandExecutionServer(descFile, recording);

		RpcRequestMessage reqMsg = new RpcRequestMessage(Map.of("data", json("{\"v\":1}")),
														Map.of("a", json("null")));
		RpcResponseMessage resp = waitForTermination(server, server.start(reqMsg));

		Assertions.assertEquals(RpcState.COMPLETED, resp.getState());
		Assertions.assertEquals(List.of("data"), inputCalls);
		Assertions.assertEquals(List.of("a", "b"), outputCalls);
	}

	// ----- 세션 식별자 -----

	@Test
	public void testSessionIdCarriesServerUuid() throws Exception {
		// 세션 endpoint는 '/api/v1/sessions/<UUID>-<seq>'이고, 세션 디렉토리는 같은 식별자를 쓴다.
		// 서버 객체를 새로 만들면(프로세스 재시작에 해당) UUID가 달라져 이전 endpoint와 겹치지 않는다.
		File descFile = writeDescriptor(
				"{\"commandLine\":[\"/bin/sleep\",\"10\"],"
				+ "\"outputVariables\":[],\"timeout\":\"30s\"}");
		RESTfulCommandExecutionServer server1 = new RESTfulCommandExecutionServer(descFile, SERDE);
		RESTfulCommandExecutionServer server2 = new RESTfulCommandExecutionServer(descFile, SERDE);

		RpcResponseMessage started1 = server1.start(new RpcRequestMessage(Map.of()));
		RpcResponseMessage started2 = server2.start(new RpcRequestMessage(Map.of()));
		try {
			String prefix = "/api/v1/sessions/";
			String id1 = started1.getSessionEndpoint().substring(prefix.length());
			String id2 = started2.getSessionEndpoint().substring(prefix.length());
			String uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
			Assertions.assertTrue(id1.matches(uuid + "-\\d+"), id1);
			Assertions.assertTrue(id2.matches(uuid + "-\\d+"), id2);
			Assertions.assertNotEquals(id1.substring(0, 36), id2.substring(0, 36));

			Assertions.assertTrue(new File(m_workDir, "session-" + id1).isDirectory());
		}
		finally {
			server1.cancel(started1.getSessionEndpoint());
			server2.cancel(started2.getSessionEndpoint());
		}
	}

	/**
	 * 연산이 종료 상태에 도달할 때까지 {@code status()}로 폴링한다.
	 * 종료 상태는 read-once로 세션에서 제거되므로, 종료를 관측하면 즉시 반환한다.
	 */
	private RpcResponseMessage waitForTermination(RESTfulCommandExecutionServer server,
														RpcResponseMessage resp) throws InterruptedException {
		String sessionUrl = resp.getSessionEndpoint();
		long deadline = System.currentTimeMillis() + 5000;
		while ( resp.getState() == RpcState.RUNNING && System.currentTimeMillis() < deadline ) {
			Thread.sleep(50);
			resp = server.status(sessionUrl);
		}
		return resp;
	}
}
