package utils.async.command;


import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.async.command.ProgramServiceConfig.RestartPolicy;
import utils.io.FileUtils;


/**
 * {@link ProgramServiceConfig}의 기본값/setter/직렬화 검증.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ProgramServiceConfigTest {

	// ---------- 기본값 ----------

	@Test
	public void default_values_after_construction() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();

		Assertions.assertNull(cfg.getCommandLine());
		Assertions.assertEquals(FileUtils.getCurrentWorkingDirectory(), cfg.getWorkingDirectory());
		Assertions.assertNull(cfg.getEnvironmentFile());
		Assertions.assertNotNull(cfg.getEnvironments());
		Assertions.assertTrue(cfg.getEnvironments().isEmpty());
		Assertions.assertEquals(RestartPolicy.ALWAYS, cfg.getRestartPolicy());
		Assertions.assertEquals(ProgramServiceConfig.DEFAULT_START_TIMEOUT, cfg.getStartTimeout());
		Assertions.assertEquals(ProgramServiceConfig.DEFAULT_RESTART_DELAY, cfg.getRestartDelay());
	}

	// ---------- setCommandLine ----------

	@Test
	public void setCommandLine_stores_value() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		List<String> cmd = Arrays.asList("/bin/echo", "hello");
		cfg.setCommandLine(cmd);

		Assertions.assertEquals(cmd, cfg.getCommandLine());
	}

	@Test
	public void setCommandLine_null_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new ProgramServiceConfig().setCommandLine(null);
			});
	}

	// ---------- setWorkingDirectory ----------

	@Test
	public void setWorkingDirectory_stores_value() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		File dir = new File("/tmp");
		cfg.setWorkingDirectory(dir);

		Assertions.assertEquals(dir, cfg.getWorkingDirectory());
	}

	@Test
	public void setWorkingDirectory_null_falls_back_to_current() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setWorkingDirectory(new File("/tmp"));  // 일단 세팅
		cfg.setWorkingDirectory(null);              // null로 리셋

		Assertions.assertEquals(FileUtils.getCurrentWorkingDirectory(), cfg.getWorkingDirectory());
	}

	// ---------- setEnvironmentFile ----------

	@Test
	public void setEnvironmentFile_accepts_value_and_null() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		File envFile = new File("/tmp/env");
		cfg.setEnvironmentFile(envFile);
		Assertions.assertEquals(envFile, cfg.getEnvironmentFile());

		cfg.setEnvironmentFile(null);
		Assertions.assertNull(cfg.getEnvironmentFile());
	}

	// ---------- setEnvironments ----------

	@Test
	public void setEnvironments_stores_value() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		Map<String,String> env = new HashMap<>();
		env.put("KEY", "VAL");
		cfg.setEnvironments(env);

		Assertions.assertEquals(env, cfg.getEnvironments());
	}

	@Test
	public void setEnvironments_null_falls_back_to_empty_map() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		Map<String,String> env = new HashMap<>();
		env.put("KEY", "VAL");
		cfg.setEnvironments(env);

		cfg.setEnvironments(null);
		Assertions.assertNotNull(cfg.getEnvironments());
		Assertions.assertTrue(cfg.getEnvironments().isEmpty());
	}

	// ---------- setRestartPolicy ----------

	@Test
	public void setRestartPolicy_stores_value() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setRestartPolicy(RestartPolicy.ON_FAILED);

		Assertions.assertEquals(RestartPolicy.ON_FAILED, cfg.getRestartPolicy());
	}

	@Test
	public void setRestartPolicy_null_falls_back_to_ALWAYS() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setRestartPolicy(RestartPolicy.NO);
		cfg.setRestartPolicy(null);

		Assertions.assertEquals(RestartPolicy.ALWAYS, cfg.getRestartPolicy());
	}

	// ---------- setStartTimeout ----------

	@Test
	public void setStartTimeout_stores_value() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setStartTimeout("1m");

		Assertions.assertEquals("1m", cfg.getStartTimeout());
	}

	@Test
	public void setStartTimeout_null_falls_back_to_default() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setStartTimeout("1m");
		cfg.setStartTimeout(null);

		Assertions.assertEquals(ProgramServiceConfig.DEFAULT_START_TIMEOUT, cfg.getStartTimeout());
	}

	@Test
	public void getStartTimeoutAsDuration_parses_value() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setStartTimeout("1m");

		Assertions.assertEquals(Duration.ofMinutes(1), cfg.getStartTimeoutAsDuration());
	}

	// ---------- setRestartDelay ----------

	@Test
	public void setRestartDelay_duration_stores_value() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setRestartDelay(Duration.ofSeconds(10));

		Assertions.assertEquals(Duration.ofSeconds(10), cfg.getRestartDelay());
	}

	@Test
	public void setRestartDelay_duration_null_falls_back_to_default() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setRestartDelay(Duration.ofSeconds(10));
		cfg.setRestartDelay((Duration) null);

		Assertions.assertEquals(ProgramServiceConfig.DEFAULT_RESTART_DELAY, cfg.getRestartDelay());
	}

	@Test
	public void setRestartDelayString_parses_value() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setRestartDelayString("3s");

		Assertions.assertEquals(Duration.ofSeconds(3), cfg.getRestartDelay());
	}

	@Test
	public void setRestartDelayString_null_falls_back_to_default() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setRestartDelayString("10s");
		cfg.setRestartDelayString(null);

		Assertions.assertEquals(ProgramServiceConfig.DEFAULT_RESTART_DELAY, cfg.getRestartDelay());
	}

	// ---------- RestartPolicy ----------

	@Test
	public void RestartPolicy_toString_returns_kebab_name() {
		Assertions.assertEquals("always", RestartPolicy.ALWAYS.toString());
		Assertions.assertEquals("on-completed", RestartPolicy.ON_COMPLETED.toString());
		Assertions.assertEquals("on-failed", RestartPolicy.ON_FAILED.toString());
		Assertions.assertEquals("no", RestartPolicy.NO.toString());
	}

	@Test
	public void RestartPolicy_fromString_matches_case_insensitive() {
		Assertions.assertEquals(RestartPolicy.ALWAYS, RestartPolicy.fromString("always"));
		Assertions.assertEquals(RestartPolicy.ALWAYS, RestartPolicy.fromString("ALWAYS"));
		Assertions.assertEquals(RestartPolicy.ON_COMPLETED, RestartPolicy.fromString("on-completed"));
		Assertions.assertEquals(RestartPolicy.ON_FAILED, RestartPolicy.fromString("on-failed"));
		Assertions.assertEquals(RestartPolicy.NO, RestartPolicy.fromString("no"));
	}

	@Test
	public void RestartPolicy_fromString_unknown_throws() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			RestartPolicy.fromString("unknown-policy");
			});
	}

	// ---------- toString ----------

	@Test
	public void toString_contains_key_fields() {
		ProgramServiceConfig cfg = new ProgramServiceConfig();
		cfg.setCommandLine(Arrays.asList("/bin/echo", "hello"));
		cfg.setRestartPolicy(RestartPolicy.ON_FAILED);

		String s = cfg.toString();
		Assertions.assertTrue(s.contains("/bin/echo"));
		Assertions.assertTrue(s.contains("on-failed"));
	}

	// ---------- JSON round-trip ----------

	/**
	 * 모든 필드의 JSON 라운드트립 검증.
	 * <p>
	 * {@link ProgramServiceConfig#getRestartDelay()}에 {@code @JsonFormat(STRING)}이 부착되어
	 * Duration이 ISO-8601 형식으로 직렬화되고, {@code setRestartDelayString}은 {@code UnitUtils}를
	 * 통해 동일 형식을 파싱한다.
	 */
	@Test
	public void json_round_trip_preserves_fields() throws Exception {
		ProgramServiceConfig original = new ProgramServiceConfig();
		original.setCommandLine(Arrays.asList("/bin/echo", "hi"));
		original.setWorkingDirectory(new File("/tmp"));
		Map<String,String> env = new HashMap<>();
		env.put("FOO", "bar");
		original.setEnvironments(env);
		original.setRestartPolicy(RestartPolicy.ON_FAILED);
		original.setStartTimeout("45s");
		original.setRestartDelay(Duration.ofSeconds(7));

		JsonMapper mapper = JsonMapper.builder()
									.addModule(new JavaTimeModule())
									.findAndAddModules()
									.build();
		String json = mapper.writeValueAsString(original);
		ProgramServiceConfig restored = mapper.readValue(json, ProgramServiceConfig.class);

		Assertions.assertEquals(original.getCommandLine(), restored.getCommandLine());
		Assertions.assertEquals(original.getWorkingDirectory(), restored.getWorkingDirectory());
		Assertions.assertEquals(original.getEnvironments(), restored.getEnvironments());
		Assertions.assertEquals(original.getRestartPolicy(), restored.getRestartPolicy());
		Assertions.assertEquals(original.getStartTimeout(), restored.getStartTimeout());
		Assertions.assertEquals(original.getRestartDelay(), restored.getRestartDelay());
	}

	@Test
	public void json_restart_delay_accepts_string_form() throws Exception {
		String json = "{\"commandLine\":[\"/bin/echo\"],\"restartDelay\":\"15s\"}";
		JsonMapper mapper = JsonMapper.builder()
									.addModule(new JavaTimeModule())
									.findAndAddModules()
									.build();
		ProgramServiceConfig cfg = mapper.readValue(json, ProgramServiceConfig.class);

		Assertions.assertEquals(Duration.ofSeconds(15), cfg.getRestartDelay());
	}
}
