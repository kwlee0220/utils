package utils.async.command;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.Maps;

import utils.UnitUtils;
import utils.func.FOption;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonPropertyOrder({ "commandLine", "workingDirectory", "environmentFile", "environments",
					"restartPolicy", "startTimeout" })
public class ProgramServiceConfig {
	public static final String DEFAULT_START_TIMEOUT = "30s";
	public static final Duration DEFAULT_RESTART_DELAY = Duration.ofSeconds(0);
	
	public static enum RestartPolicy {
		ALWAYS("always"),
		ON_COMPLETED("on-completed"),
		ON_FAILED("on-failed"),
		NO("no");
		
		private final String m_name;
		
		private RestartPolicy(String name) {
			m_name = name;
		}
		
		@Override
		public String toString() {
			return m_name;
		}
		
		public static RestartPolicy fromString(String name) {
			for ( RestartPolicy policy: values() ) {
				if ( policy.m_name.equalsIgnoreCase(name) ) {
					return policy;
				}
			}
			
			throw new IllegalArgumentException("unknown restart policy: " + name);
		}
	}
	
	private List<String> m_commandLine;
	private File m_workingDirectory;
	private File m_environmentFile;
	private Map<String,String> m_environments = Maps.newHashMap();
	private RestartPolicy m_restartPolicy = RestartPolicy.ALWAYS;
	private String m_startTimeout = null;		// default: DEFAULT_START_TIMEOUT
	private Duration m_restartDelay = null;		// default: DEFAULT_RESTART_DELAY
	
	public List<String> getCommandLine() {
		return m_commandLine;
	}
	public void setCommandLine(List<String> command) {
		m_commandLine = command;
	}
	
	public File getWorkingDirectory() {
		return m_workingDirectory;
	}
	public void setWorkingDirectory(File workingDirectory) {
		m_workingDirectory = workingDirectory;
	}
	
	public File getEnvironmentFile() {
		return m_environmentFile;
	}
	public void setEnvironmentFile(File envFile) {
		m_environmentFile = envFile;
	}
	
	public Map<String,String> getEnvironmentVariables() {
		return m_environments;
	}
	public void setEnvironments(Map<String,String> environments) {
		m_environments = FOption.getOrElse(environments, Maps.newHashMap());
	}
	
	public RestartPolicy getRestartPolicy() {
		return m_restartPolicy;
	}
	public void setRestartPolicy(RestartPolicy restartPolicy) {
		m_restartPolicy = restartPolicy;
	}
	
	public String getStartTimeout() {
		return m_startTimeout;
	}
	public Duration getStartTimeoutAsDuration() {
		return FOption.map(m_startTimeout, UnitUtils::parseSecondDuration);
	}
	public void setStartTimeout(String startTimeout) {
		m_startTimeout = FOption.getOrElse(startTimeout, DEFAULT_START_TIMEOUT);
	}
	
	public Duration getRestartDelay() {
		return m_restartDelay;
	}
	public void setRestartDelayString(Duration restartDelay) {
		m_restartDelay = FOption.getOrElse(restartDelay, DEFAULT_RESTART_DELAY);
	}
	@JsonProperty("restartDelay")
	public void setRestartDelayString(String restartDelay) {
		m_restartDelay = FOption.mapOrElse(restartDelay, UnitUtils::parseSecondDuration, DEFAULT_RESTART_DELAY);
	}
	
	@Override
	public String toString() {
		String startTimeoutStr = (m_startTimeout != null)
								? String.format("startTimeout=%s, ", m_startTimeout) : "";
		return String.format("command=%s, workingDirectory=%s, restartPolicy=%s, "
							+ "%srestartDelay=%s",
							m_commandLine, m_workingDirectory, m_restartPolicy,
							startTimeoutStr, m_restartDelay);
	}
}
