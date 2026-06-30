package utils.rpc.restful.process;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.json.JacksonUtils;
import utils.rpc.restful.process.CommandExecutionDescriptor;


/**
 * {@link CommandExecutionDescriptor} JSON 로딩/파싱 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandExecutionDescriptorTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;

	@TempDir File m_dir;

	private CommandExecutionDescriptor load(String json) throws IOException {
		File descFile = new File(m_dir, "op.json");
		Files.writeString(descFile.toPath(), json, StandardCharsets.UTF_8);
		return CommandExecutionDescriptor.load(descFile, MAPPER);
	}

	@Test
	public void testLoadFullDescriptor() throws IOException {
		CommandExecutionDescriptor desc = load(
				"{"
				+ "\"commandLine\":[\"/bin/echo\",\"hello\"],"
				+ "\"workingDirectory\":\"/tmp/work\","
				+ "\"outputVariables\":[\"a\",\"b\"],"
				+ "\"timeout\":\"30s\","
				+ "\"startTimeout\":\"5s\","
				+ "\"sessionRetainTimeout\":\"10s\""
				+ "}");

		Assertions.assertEquals(List.of("/bin/echo", "hello"), desc.getCommandLine());
		Assertions.assertEquals(new File("/tmp/work"), desc.getWorkingDirectory());
		Assertions.assertEquals(List.of("a", "b"), desc.getOutputVariables());
		Assertions.assertEquals(Duration.ofSeconds(30), desc.getTimeout());
		Assertions.assertEquals(Duration.ofSeconds(5), desc.getStartTimeout());
		Assertions.assertEquals(Duration.ofSeconds(10), desc.getSessionRetainTimeout());
	}

	@Test
	public void testDurationStringUnitsParsed() throws IOException {
		CommandExecutionDescriptor desc = load(
				"{\"commandLine\":[\"/bin/true\"],\"timeout\":\"5m\",\"startTimeout\":\"100ms\"}");

		Assertions.assertEquals(Duration.ofMinutes(5), desc.getTimeout());
		Assertions.assertEquals(Duration.ofMillis(100), desc.getStartTimeout());
	}

	@Test
	public void testMinimalDescriptorDefaults() throws IOException {
		// commandLine만 지정한 경우, 나머지는 기본값(null/빈 목록)이다.
		CommandExecutionDescriptor desc = load("{\"commandLine\":[\"/bin/true\"]}");

		Assertions.assertEquals(List.of("/bin/true"), desc.getCommandLine());
		Assertions.assertNull(desc.getWorkingDirectory());
		Assertions.assertTrue(desc.getOutputVariables().isEmpty());
		Assertions.assertNull(desc.getTimeout());
		Assertions.assertNull(desc.getStartTimeout());
		Assertions.assertNull(desc.getSessionRetainTimeout());
	}

	@Test
	public void testEmptyDescriptorHasEmptyCommandLine() throws IOException {
		// 필드가 전혀 없으면 commandLine은 빈 목록 기본값을 유지한다.
		CommandExecutionDescriptor desc = load("{}");

		Assertions.assertTrue(desc.getCommandLine().isEmpty());
	}

	@Test
	public void testSetCommandLineNullRejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> new CommandExecutionDescriptor().setCommandLine(null));
	}

	@Test
	public void testToStringContainsCommandAndOutputs() throws IOException {
		CommandExecutionDescriptor desc = load(
				"{\"commandLine\":[\"/bin/echo\",\"x\"],\"outputVariables\":[\"r\"]}");

		String str = desc.toString();
		Assertions.assertTrue(str.contains("/bin/echo"), str);
		Assertions.assertTrue(str.contains("r"), str);
	}
}
