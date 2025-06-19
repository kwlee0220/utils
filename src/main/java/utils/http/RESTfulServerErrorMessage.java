package utils.http;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonInclude(Include.NON_NULL)
public class RESTfulServerErrorMessage {
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
