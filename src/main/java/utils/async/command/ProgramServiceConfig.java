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
 *   <li>{@code startTimeout}: {@link #DEFAULT_START_TIMEOUT} (30초). 0이면 제한하지 않는다.</li>
 *   <li>{@code restartDelay}: {@link #DEFAULT_RESTART_DELAY} (5초).</li>
 *   <li>{@code maxRestartDelay}: {@link #DEFAULT_MAX_RESTART_DELAY} (1분). 짧은 실행이 연달아 일어날 때 늘어나는
 *       재시작 지연의 상한. {@code restartDelay} 이하이면 backoff를 하지 않는다.</li>
 *   <li>{@code stableRunTime}: {@link #DEFAULT_STABLE_RUN_TIME} (10초). 이보다 짧게 끝난 실행을 backoff 대상인
 *       "짧은 실행"으로 본다.</li>
 *   <li>{@code restartLimit}: 0 (제한 없음). 양수이면 {@code restartLimitInterval} 안에 이 횟수를 넘겨 재시작하려 할 때
 *       서비스를 FAILED로 전이한다.</li>
 *   <li>{@code restartLimitInterval}: {@link #DEFAULT_RESTART_LIMIT_INTERVAL} (1분).</li>
 * </ul>
 * 시간 항목은 JSON에서 {@link UnitUtils#parseDuration}이 인식하는 문자열(예: "5s", "1m", "PT5S")로 지정한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonPropertyOrder({ "commandLine", "workingDirectory", "environmentFile", "environments",
					"restartPolicy", "restartDelay", "maxRestartDelay", "stableRunTime",
					"restartLimit", "restartLimitInterval", "startTimeout" })
public class ProgramServiceConfig {
	/** 시작 타임아웃의 기본값 (30초). */
	public static final Duration DEFAULT_START_TIMEOUT = Duration.ofSeconds(30);

	/** 재시작 지연의 기본값 (5초). */
	public static final Duration DEFAULT_RESTART_DELAY = Duration.ofSeconds(5);
	/** 최대 재시작 지연의 기본값 (1분). */
	public static final Duration DEFAULT_MAX_RESTART_DELAY = Duration.ofMinutes(1);
	/** 안정 실행 시간의 기본값 (10초). */
	public static final Duration DEFAULT_STABLE_RUN_TIME = Duration.ofSeconds(10);
	/** 재시작 횟수 제한 구간의 기본값 (1분). */
	public static final Duration DEFAULT_RESTART_LIMIT_INTERVAL = Duration.ofMinutes(1);

	private List<String> m_commandLine;
	private File m_workingDirectory;
	@Nullable private File m_environmentFile;
	private Map<String,String> m_environments = Maps.newHashMap();
	private RestartPolicy m_restartPolicy = RestartPolicy.ALWAYS;
	private Duration m_startTimeout = DEFAULT_START_TIMEOUT;
	private Duration m_restartDelay = DEFAULT_RESTART_DELAY;
	private Duration m_maxRestartDelay = DEFAULT_MAX_RESTART_DELAY;
	private Duration m_stableRunTime = DEFAULT_STABLE_RUN_TIME;
	private int m_restartLimit = 0;
	private Duration m_restartLimitInterval = DEFAULT_RESTART_LIMIT_INTERVAL;

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
	 * 주어진 설정의 복사본을 생성한다. 명령행과 환경 변수 맵은 수정할 수 없는 복사본으로 만든다.
	 * <p>
	 * {@link ProgramService}는 생성 시 설정을 복사해 보관하므로, 서비스 생성 후 원래 설정 객체를 바꿔도 서비스의
	 * 동작(다음 재시작의 명령·정책 등)은 달라지지 않는다.
	 *
	 * @param other	복사할 설정.
	 * @throws IllegalArgumentException	{@code other}가 {@code null}인 경우.
	 */
	public ProgramServiceConfig(ProgramServiceConfig other) {
		Preconditions.checkNotNullArgument(other, "other is null");

		m_commandLine = (other.m_commandLine != null) ? List.copyOf(other.m_commandLine) : null;
		m_workingDirectory = other.m_workingDirectory;
		m_environmentFile = other.m_environmentFile;
		m_environments = Map.copyOf(other.m_environments);
		m_restartPolicy = other.m_restartPolicy;
		m_startTimeout = other.m_startTimeout;
		m_restartDelay = other.m_restartDelay;
		m_maxRestartDelay = other.m_maxRestartDelay;
		m_stableRunTime = other.m_stableRunTime;
		m_restartLimit = other.m_restartLimit;
		m_restartLimitInterval = other.m_restartLimitInterval;
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
	 * 시작 타임아웃을 반환한다.
	 * <p>
	 * {@link ProgramService}는 첫 실행이 이 시간 안에 RUNNING(자식 프로세스 기동 완료)이 되지 못하면 서비스를
	 * FAILED로 전이한다. 0이면 제한하지 않는다.
	 *
	 * @return 시작 타임아웃. 기본값 {@link #DEFAULT_START_TIMEOUT} (30초).
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	public Duration getStartTimeout() {
		return m_startTimeout;
	}

	/**
	 * 시작 타임아웃을 설정한다. {@code null}이면 기본값으로 fallback한다.
	 *
	 * @param startTimeout 시작 타임아웃 (0이면 제한 없음), 또는 {@code null}.
	 * @throws IllegalArgumentException {@code startTimeout}이 음수인 경우.
	 */
	@JsonIgnore
	public void setStartTimeout(@Nullable Duration startTimeout) {
		m_startTimeout = nonNegative(startTimeout, DEFAULT_START_TIMEOUT, "startTimeout");
	}

	@JsonProperty("startTimeout")
	private void setStartTimeoutString(@Nullable String startTimeout) {
		setStartTimeout(parse(startTimeout));
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
	
	/**
	 * 최대 재시작 지연을 반환한다. 짧은 실행이 연달아 일어날 때 backoff로 늘어나는 재시작 지연의 상한이다.
	 *
	 * @return 최대 재시작 지연. 기본값 {@link #DEFAULT_MAX_RESTART_DELAY} (1분).
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	public Duration getMaxRestartDelay() {
		return m_maxRestartDelay;
	}

	/**
	 * 최대 재시작 지연을 설정한다. {@code restartDelay} 이하로 설정하면 backoff를 하지 않는다.
	 * {@code null}이면 기본값으로 fallback한다.
	 *
	 * @param delay 최대 재시작 지연, 또는 {@code null}.
	 * @throws IllegalArgumentException {@code delay}가 음수인 경우.
	 */
	@JsonIgnore
	public void setMaxRestartDelay(@Nullable Duration delay) {
		m_maxRestartDelay = nonNegative(delay, DEFAULT_MAX_RESTART_DELAY, "maxRestartDelay");
	}

	@JsonProperty("maxRestartDelay")
	private void setMaxRestartDelayString(@Nullable String delay) {
		setMaxRestartDelay(parse(delay));
	}

	/**
	 * 안정 실행 시간을 반환한다. 이보다 짧게 끝난 실행이 연달아 일어나면 재시작 지연을 늘린다(backoff).
	 *
	 * @return 안정 실행 시간. 기본값 {@link #DEFAULT_STABLE_RUN_TIME} (10초).
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	public Duration getStableRunTime() {
		return m_stableRunTime;
	}

	/**
	 * 안정 실행 시간을 설정한다. {@code null}이면 기본값으로 fallback한다.
	 *
	 * @param time 안정 실행 시간, 또는 {@code null}.
	 * @throws IllegalArgumentException {@code time}이 음수인 경우.
	 */
	@JsonIgnore
	public void setStableRunTime(@Nullable Duration time) {
		m_stableRunTime = nonNegative(time, DEFAULT_STABLE_RUN_TIME, "stableRunTime");
	}

	@JsonProperty("stableRunTime")
	private void setStableRunTimeString(@Nullable String time) {
		setStableRunTime(parse(time));
	}

	/**
	 * 재시작 횟수 제한을 반환한다.
	 *
	 * @return {@code restartLimitInterval} 안에 허용되는 재시작 횟수. 0이면 제한하지 않는다 (기본값).
	 */
	public int getRestartLimit() {
		return m_restartLimit;
	}

	/**
	 * 재시작 횟수 제한을 설정한다. {@code restartLimitInterval} 안에 이 횟수를 넘겨 재시작하려 하면 서비스를
	 * FAILED로 전이한다.
	 *
	 * @param limit 허용 재시작 횟수. 0이면 제한하지 않는다.
	 * @throws IllegalArgumentException {@code limit}이 음수인 경우.
	 */
	public void setRestartLimit(int limit) {
		Preconditions.checkArgument(limit >= 0, "restartLimit must be non-negative: %s", limit);
		m_restartLimit = limit;
	}

	/**
	 * 재시작 횟수 제한의 시간 구간을 반환한다.
	 *
	 * @return 시간 구간. 기본값 {@link #DEFAULT_RESTART_LIMIT_INTERVAL} (1분).
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	public Duration getRestartLimitInterval() {
		return m_restartLimitInterval;
	}

	/**
	 * 재시작 횟수 제한의 시간 구간을 설정한다. {@code null}이면 기본값으로 fallback한다.
	 *
	 * @param interval 시간 구간, 또는 {@code null}.
	 * @throws IllegalArgumentException {@code interval}이 양수가 아닌 경우.
	 */
	@JsonIgnore
	public void setRestartLimitInterval(@Nullable Duration interval) {
		Preconditions.checkArgument(interval == null || (!interval.isNegative() && !interval.isZero()),
									"restartLimitInterval must be positive: %s", interval);
		m_restartLimitInterval = Optionals.getOrElse(interval, DEFAULT_RESTART_LIMIT_INTERVAL);
	}

	@JsonProperty("restartLimitInterval")
	private void setRestartLimitIntervalString(@Nullable String interval) {
		setRestartLimitInterval(parse(interval));
	}

	private static @Nullable Duration parse(@Nullable String str) {
		return FOption.map(str, UnitUtils::parseDuration);
	}

	private static Duration nonNegative(@Nullable Duration value, Duration defaultValue, String name) {
		Preconditions.checkArgument(value == null || !value.isNegative(), "%s must be non-negative: %s", name, value);
		return Optionals.getOrElse(value, defaultValue);
	}
	
	@Override
	public String toString() {
		String startTimeoutStr = String.format("startTimeout=%s, ", m_startTimeout);
		String limitStr = (m_restartLimit > 0)
						? String.format(", restartLimit=%d/%s", m_restartLimit, m_restartLimitInterval) : "";
		return String.format("command=%s, workingDirectory=%s, restartPolicy=%s, %srestartDelay=%s, "
							+ "maxRestartDelay=%s%s",
							m_commandLine, m_workingDirectory.getAbsolutePath(), m_restartPolicy,
							startTimeoutStr, m_restartDelay, m_maxRestartDelay, limitStr);
	}
}
