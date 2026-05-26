package utils.async.command;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.Maps;

import utils.Preconditions;
import utils.UnitUtils;
import utils.func.FOption;
import utils.func.Optionals;
import utils.io.FileUtils;


/**
 * {@link ProgramService}의 실행 설정을 담는 값 객체.
 * <p>
 * 자식 프로세스의 명령행, 작업 디렉터리, 환경 변수, 재시작 정책, 시작 타임아웃 등을 기술한다.
 * JSON으로 직렬화/역직렬화되어 외부 설정 파일에서 로드할 수 있으며, 코드에서 직접 setter로
 * 구성할 수도 있다.
 * <p>
 * 필드별 기본값:
 * <ul>
 *   <li>{@code commandLine}: 없음 (필수).</li>
 *   <li>{@code workingDirectory}: 현재 작업 디렉터리 ({@link FileUtils#getCurrentWorkingDirectory()}).</li>
 *   <li>{@code environmentFile}: 없음 (선택).</li>
 *   <li>{@code environments}: 빈 맵.</li>
 *   <li>{@code restartPolicy}: {@link RestartPolicy#ALWAYS}.</li>
 *   <li>{@code startTimeout}: {@link #DEFAULT_START_TIMEOUT} ({@value #DEFAULT_START_TIMEOUT}).</li>
 *   <li>{@code restartDelay}: {@link #DEFAULT_RESTART_DELAY} (5초).</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonPropertyOrder({ "commandLine", "workingDirectory", "environmentFile", "environments",
					"restartPolicy", "restartDelay", "startTimeout" })
public class ProgramServiceConfig {
	/** 시작 타임아웃의 기본값 (문자열 형태, {@link UnitUtils#parseDuration}로 파싱). */
	public static final String DEFAULT_START_TIMEOUT = "30s";

	/** 재시작 지연의 기본값 (5초). */
	public static final Duration DEFAULT_RESTART_DELAY = Duration.ofSeconds(5);

	private List<String> m_commandLine;
	private File m_workingDirectory;
	@Nullable private File m_environmentFile;
	private Map<String,String> m_environments = Maps.newHashMap();
	private RestartPolicy m_restartPolicy = RestartPolicy.ALWAYS;
	private String m_startTimeout = DEFAULT_START_TIMEOUT;
	private Duration m_restartDelay = DEFAULT_RESTART_DELAY;

	/**
	 * 기본 설정 객체를 생성한다.
	 * <p>
	 * 작업 디렉터리는 현재 프로세스의 작업 디렉터리로 초기화되며, 나머지 필드는 클래스
	 * Javadoc의 기본값 표를 따른다. {@code commandLine}은 사용 전에 반드시
	 * {@link #setCommandLine(List)}로 지정해야 한다.
	 */
	public ProgramServiceConfig() {
		m_workingDirectory = FileUtils.getCurrentWorkingDirectory();
	}

	/**
	 * 실행할 명령행을 반환한다.
	 *
	 * @return 명령행 토큰 리스트(첫 원소는 실행 파일 경로).
	 */
	public List<String> getCommandLine() {
		return m_commandLine;
	}

	/**
	 * 실행할 명령행을 설정한다.
	 *
	 * @param command 명령행 토큰 리스트 (non-null).
	 * @throws IllegalArgumentException {@code command}가 {@code null}인 경우.
	 */
	public void setCommandLine(List<String> command) {
		Preconditions.checkNotNullArgument(command, "commandLine must not be null");
		m_commandLine = command;
	}

	/**
	 * 자식 프로세스의 작업 디렉터리를 반환한다.
	 *
	 * @return 작업 디렉터리. 기본값은 현재 프로세스의 작업 디렉터리.
	 */
	public File getWorkingDirectory() {
		return m_workingDirectory;
	}

	/**
	 * 자식 프로세스의 작업 디렉터리를 설정한다.
	 * {@code null}을 전달하면 현재 프로세스의 작업 디렉터리로 fallback한다.
	 *
	 * @param workingDirectory 작업 디렉터리, 또는 {@code null}.
	 */
	public void setWorkingDirectory(@Nullable File workingDirectory) {
		m_workingDirectory = Optionals.getOrElse(workingDirectory,
												FileUtils::getCurrentWorkingDirectory);
	}

	/**
	 * 자식 프로세스에 적용할 환경 변수 파일을 반환한다.
	 *
	 * @return 환경 변수 파일, 또는 {@code null}(설정되지 않은 경우).
	 */
	@Nullable
	public File getEnvironmentFile() {
		return m_environmentFile;
	}

	/**
	 * 자식 프로세스에 적용할 환경 변수 파일을 설정한다.
	 *
	 * @param envFile 환경 변수 파일, 또는 {@code null}(해제).
	 */
	public void setEnvironmentFile(@Nullable File envFile) {
		m_environmentFile = envFile;
	}

	/**
	 * 자식 프로세스에 추가로 전달할 환경 변수 맵을 반환한다.
	 *
	 * @return 환경 변수 맵. 기본값은 빈 맵.
	 */
	public Map<String,String> getEnvironments() {
		return m_environments;
	}

	/**
	 * 자식 프로세스에 추가로 전달할 환경 변수 맵을 설정한다.
	 * {@code null}을 전달하면 빈 맵으로 초기화된다.
	 *
	 * @param environments 환경 변수 맵, 또는 {@code null}.
	 */
	public void setEnvironments(@Nullable Map<String,String> environments) {
		m_environments = Optionals.getOrElse(environments, Maps::newHashMap);
	}

	/**
	 * 재시작 정책을 반환한다.
	 *
	 * @return 재시작 정책. 기본값 {@link RestartPolicy#ALWAYS}.
	 */
	public RestartPolicy getRestartPolicy() {
		return m_restartPolicy;
	}

	/**
	 * 재시작 정책을 설정한다. {@code null}을 전달하면 {@link RestartPolicy#ALWAYS}로 fallback한다.
	 *
	 * @param restartPolicy 재시작 정책, 또는 {@code null}.
	 */
	public void setRestartPolicy(RestartPolicy restartPolicy) {
		m_restartPolicy = Optionals.getOrElse(restartPolicy, RestartPolicy.ALWAYS);
	}

	/**
	 * 시작 타임아웃을 문자열 형태로 반환한다(예: "30s", "1m").
	 *
	 * @return 시작 타임아웃 문자열. 기본값 {@value #DEFAULT_START_TIMEOUT}.
	 */
	public String getStartTimeout() {
		return m_startTimeout;
	}

	/**
	 * 시작 타임아웃을 {@link Duration}으로 변환하여 반환한다.
	 * <p>
	 * Jackson에는 별도 property로 노출되지 않도록 {@link JsonIgnore}로 표시한다.
	 *
	 * @return 파싱된 {@link Duration}.
	 */
	@JsonIgnore
	public Duration getStartTimeoutAsDuration() {
		return FOption.map(m_startTimeout, UnitUtils::parseDuration);
	}

	/**
	 * 시작 타임아웃을 설정한다. {@code null}이면 기본값으로 fallback한다.
	 *
	 * @param startTimeout {@link UnitUtils#parseDuration}이 인식하는 문자열(예: "30s", "1m"), 또는 {@code null}.
	 */
	public void setStartTimeout(String startTimeout) {
		m_startTimeout = Optionals.getOrElse(startTimeout, DEFAULT_START_TIMEOUT);
	}

	/**
	 * 재시작 지연 시간을 반환한다.
	 * <p>
	 * JSON 직렬화 시 {@link JsonFormat.Shape#STRING}으로 강제되어 ISO-8601 형식("PT5S" 등)으로
	 * 출력된다. 이는 {@link #setRestartDelayString(String)}의 역직렬화와 라운드트립이 가능하도록
	 * 보장한다.
	 *
	 * @return 재시작 지연. 기본값 {@link #DEFAULT_RESTART_DELAY} (5초).
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	public Duration getRestartDelay() {
		return m_restartDelay;
	}

	/**
	 * 재시작 지연 시간을 설정한다. {@code null}을 전달하면 기본값으로 fallback한다.
	 * <p>
	 * JSON 역직렬화는 {@link #setRestartDelayString(String)}을 사용하므로 본 메소드는
	 * {@link JsonIgnore}로 표시한다.
	 *
	 * @param restartDelay 재시작 지연, 또는 {@code null}.
	 */
	@JsonIgnore
	public void setRestartDelay(Duration restartDelay) {
		Preconditions.checkArgument(restartDelay == null || !restartDelay.isNegative(),
									"restartDelay must be non-negative");
		m_restartDelay = Optionals.getOrElse(restartDelay, DEFAULT_RESTART_DELAY);
	}

	/**
	 * 재시작 지연 시간을 문자열로 설정한다 (JSON 역직렬화용).
	 * 입력 문자열은 {@link UnitUtils#parseDuration}으로 파싱되며, {@code null}이면 기본값으로 fallback한다.
	 *
	 * @param restartDelay 지연 시간 문자열(예: "5s", "1m"), 또는 {@code null}.
	 */
	@JsonProperty("restartDelay")
	public void setRestartDelayString(String restartDelay) {
		Duration parsedDelay = FOption.mapOrElse(restartDelay, UnitUtils::parseDuration,
												DEFAULT_RESTART_DELAY);
		Preconditions.checkArgument(parsedDelay == null || !parsedDelay.isNegative(),
									"restartDelay must be non-negative");
		m_restartDelay = parsedDelay;
	}
	
	@Override
	public String toString() {
		String startTimeoutStr = (m_startTimeout != null)
								? String.format("startTimeout=%s, ", m_startTimeout) : "";
		return String.format("command=%s, workingDirectory=%s, restartPolicy=%s, %srestartDelay=%s",
							m_commandLine, m_workingDirectory.getAbsolutePath(), m_restartPolicy,
							startTimeoutStr, m_restartDelay);
	}
}
