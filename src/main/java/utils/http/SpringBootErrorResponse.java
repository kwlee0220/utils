package utils.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jetbrains.annotations.Nullable;

/**
 * Spring Boot의 기본 에러 응답 포맷을 표현하는 DTO.
 * <p>
 * JSON 형태는 {@code timestamp}, {@code status}, {@code error}, {@code message}, {@code path} 필드로
 * 구성된다(Spring Boot {@code BasicErrorController}의 기본 출력). {@code messageType} 기반의 커스텀
 * 포맷은 이 클래스가 아니라 {@link TypedServerErrorMessage}가 표현한다.
 * <p>
 * 서버의 {@code server.error.include-stacktrace}/{@code include-exception} 등의 설정에 따라
 * {@code trace}, {@code exception} 같은 부가 필드가 추가될 수 있으므로, 알 수 없는 필드는 무시한다
 * ({@link JsonIgnoreProperties}).
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class SpringBootErrorResponse {
	private String m_path;
	private int m_status;
	private String m_error;
	private String m_message;
	private String m_timestamp;

	@JsonProperty("path")
	public String getPath() {
		return m_path;
	}

	@JsonProperty("path")
	public void setPath(String path) {
		m_path = path;
	}

	@JsonProperty("status")
	public int getStatus() {
		return m_status;
	}

	@JsonProperty("status")
	public void setStatus(int status) {
		m_status = status;
	}

	@JsonProperty("error")
	public String getError() {
		return m_error;
	}

	@JsonProperty("error")
	public void setError(String error) {
		m_error = error;
	}

	@JsonProperty("message")
	public String getMessage() {
		return m_message;
	}

	@JsonProperty("message")
	public void setMessage(String msg) {
		m_message = msg;
	}

	@JsonProperty("timestamp")
	public String getTimestamp() {
		return m_timestamp;
	}
	@JsonProperty("timestamp")
	public void setTimestamp(@Nullable String ts) {
		m_timestamp = ts;
	}

	@Override
	public String toString() {
		return String.format("%s (%d): %s, path=%s", m_error, m_status, m_message, m_path);
	}
}
