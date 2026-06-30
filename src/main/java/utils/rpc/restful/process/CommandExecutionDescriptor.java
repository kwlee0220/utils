package utils.rpc.restful.process;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.Preconditions;
import utils.UnitUtils;
import utils.func.FOption;
import utils.stream.FStream;


/**
 * {@link RESTfulCommandExecutionServer}가 실행할 외부 명령(command)의 구성을 기술하는 디스크립터.
 * <p>
 * JSON 파일로 작성되어 {@link #load(File, JsonMapper)}로 읽어 들이며, 다음 항목으로 구성된다.
 * <ul>
 *   <li>{@code commandLine} - 실행할 명령줄 token 목록.</li>
 *   <li>{@code workingDirectory} - 작업 디렉토리. 생략 시 디스크립터 파일이 위치한 디렉토리가 사용된다.</li>
 *   <li>{@code outputVariables} - 명령이 출력 파일로 기록하는 출력 변수 이름 목록.</li>
 *   <li>{@code timeout} - 명령 실행 제한 시간.</li>
 *   <li>{@code startTimeout} - 연산 시작(=명령 실행 시작) 대기 제한 시간.</li>
 *   <li>{@code sessionRetainTimeout} - 종료된 세션을 결과 회수를 위해 보관하는 시간.</li>
 * </ul>
 * 세 가지 시간 항목({@code timeout}/{@code startTimeout}/{@code sessionRetainTimeout})은 JSON에서
 * {@code "30s"}, {@code "5m"}와 같은 사람이 읽기 쉬운 문자열로 표기할 수 있으며, 이 경우
 * {@code setXXXAsString} 세터가 {@link UnitUtils#parseDuration(String)}으로 파싱한다.
 * JSON 직렬화 시 {@code null} 필드는 생략된다({@link JsonInclude}).
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonIncludeProperties({"commandLine", "workingDirectory", "outputVariables",
						"timeout", "startTimeout", "sessionRetainTimeout"})
public final class CommandExecutionDescriptor {
	@JsonProperty("commandLine") private List<String> m_commandLine = Collections.emptyList();
	@JsonProperty("workingDirectory") private @Nullable File m_workingDirectory;
	@JsonProperty("outputVariables") private List<String> m_outputVariables = Collections.emptyList();
	@JsonProperty("timeout") private @Nullable Duration m_timeout;
	@JsonProperty("startTimeout") private @Nullable Duration m_startTimeout;
	@JsonProperty("sessionRetainTimeout") private @Nullable Duration m_sessionRetainTimeout;

	/**
	 * 디스크립터 JSON 파일을 읽어 {@code CommandExecutionDescriptor}를 생성한다.
	 *
	 * @param descFile	디스크립터 JSON 파일.
	 * @param parser	역직렬화에 사용할 {@link JsonMapper}.
	 * @return	생성된 디스크립터.
	 * @throws IOException	파일을 읽거나 파싱하는 중 오류가 발생한 경우.
	 */
	public static CommandExecutionDescriptor load(File descFile, JsonMapper parser) throws IOException {
		return parser.readValue(descFile, CommandExecutionDescriptor.class);
	}

	/**
	 * 실행할 명령줄 token 목록을 반환한다.
	 *
	 * @return	명령줄 token 목록.
	 */
	public List<String> getCommandLine() {
		return m_commandLine;
	}
	/**
	 * 실행할 명령줄 token 목록을 설정한다.
	 *
	 * @param cmdLine	명령줄 token 목록. ({@code null} 불가)
	 */
	public void setCommandLine(List<String> cmdLine) {
		Preconditions.checkNotNullArgument(cmdLine, "commandLine is null");

		m_commandLine = cmdLine;
	}

	/**
	 * 작업 디렉토리를 반환한다.
	 *
	 * @return	작업 디렉토리. 지정되지 않았으면 {@code null}.
	 */
	public File getWorkingDirectory() {
		return m_workingDirectory;
	}
	/**
	 * 작업 디렉토리를 설정한다.
	 *
	 * @param dir	작업 디렉토리.
	 */
	public void setWorkingDirectory(File dir) {
		m_workingDirectory = dir;
	}

	/**
	 * 출력 변수 이름 목록을 반환한다.
	 *
	 * @return	출력 변수 이름 목록.
	 */
	public List<String> getOutputVariables() {
		return m_outputVariables;
	}
	/**
	 * 출력 변수 이름 목록을 설정한다.
	 *
	 * @param outputVars	출력 변수 이름 목록.
	 */
	public void setOutputVariables(List<String> outputVars) {
		Preconditions.checkNotNullArgument(outputVars, "outputVariables is null");
		
		m_outputVariables = outputVars;
	}

	/**
	 * 명령 실행 제한 시간을 반환한다.
	 *
	 * @return	제한 시간. 지정되지 않았으면 {@code null}.
	 */
	public Duration getTimeout() {
		return m_timeout;
	}
	/**
	 * 명령 실행 제한 시간을 설정한다.
	 *
	 * @param to	제한 시간.
	 */
	public void setTimeout(Duration to) {
		m_timeout = to;
	}
	/**
	 * 명령 실행 제한 시간을 문자열({@code "30s"} 등)로 설정한다. (JSON 역직렬화용)
	 *
	 * @param timeoutStr	{@link UnitUtils#parseDuration(String)}이 파싱 가능한 제한 시간 문자열.
	 */
	@JsonProperty("timeout")
	public void setTimeoutAsString(String timeoutStr) {
		m_timeout = UnitUtils.parseDuration(timeoutStr);
	}

	/**
	 * 연산 시작 대기 제한 시간을 반환한다.
	 *
	 * @return	시작 대기 제한 시간. 지정되지 않았으면 {@code null}.
	 */
	public Duration getStartTimeout() {
		return m_startTimeout;
	}
	/**
	 * 연산 시작 대기 제한 시간을 설정한다.
	 *
	 * @param to	시작 대기 제한 시간.
	 */
	public void setStartTimeout(Duration to) {
		m_startTimeout = to;
	}
	/**
	 * 연산 시작 대기 제한 시간을 문자열({@code "5s"} 등)로 설정한다. (JSON 역직렬화용)
	 *
	 * @param timeoutStr	{@link UnitUtils#parseDuration(String)}이 파싱 가능한 제한 시간 문자열.
	 */
	@JsonProperty("startTimeout")
	public void setStartTimeoutAsString(String timeoutStr) {
		m_startTimeout = UnitUtils.parseDuration(timeoutStr);
	}

	/**
	 * 세션 보존 시간을 반환한다.
	 *
	 * @return	세션 보존 시간. 지정되지 않았으면 {@code null}.
	 */
	public Duration getSessionRetainTimeout() {
		return m_sessionRetainTimeout;
	}
	/**
	 * 세션 보존 시간을 설정한다.
	 *
	 * @param to	세션 보존 시간.
	 */
	public void setSessionRetainTimeout(Duration to) {
		m_sessionRetainTimeout = to;
	}
	/**
	 * 세션 보존 시간을 문자열({@code "30s"} 등)로 설정한다. (JSON 역직렬화용)
	 *
	 * @param timeoutStr	{@link UnitUtils#parseDuration(String)}이 파싱 가능한 시간 문자열.
	 */
	@JsonProperty("sessionRetainTimeout")
	public void setSessionRetainTimeoutAsString(String timeoutStr) {
		m_sessionRetainTimeout = UnitUtils.parseDuration(timeoutStr);
	}
	
	@Override
	public String toString() {
		String workingDirStr = FOption.mapOrElse(getWorkingDirectory(),
												f -> String.format(", working-dir=%s", f), "");
		String outputsCsv = FStream.from(m_outputVariables).join(", ", "[", "]");
		return String.format("command=%s%s, outputs=%s", m_commandLine, workingDirStr, outputsCsv);
	}
}
