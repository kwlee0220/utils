package utils.rpc.restful.process;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

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
