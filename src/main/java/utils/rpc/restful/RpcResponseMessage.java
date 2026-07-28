package utils.rpc.restful;

import java.io.IOException;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import okhttp3.Headers;

import utils.Preconditions;
import utils.http.HttpRESTfulClient.ResponseBodyDeserializer;
import utils.http.RESTfulErrorEntity;
import utils.http.RESTfulRemoteException;
import utils.stream.FStream;


/**
 * 원격 연산(operation) 호출에 대한 서버의 응답 메시지를 표현하는 DTO.
 * <p>
 * 응답은 연산의 수행 상태({@link RpcState})에 따라 구성이 달라진다.
 * <ul>
 *   <li>{@code COMPLETED} - {@code outputs}에 연산 출력이 담긴다.</li>
 *   <li>{@code FAILED} - {@code error}에 실패 정보({@link RESTfulErrorEntity})가 담긴다.</li>
 *   <li>{@code RUNNING} - 비동기 수행 중이며 {@code sessionEndpoint}로 세션을 식별한다.</li>
 *   <li>{@code CANCELLED} - 연산이 취소되었다.</li>
 * </ul>
 * JSON 직렬화 시 {@code null} 필드는 생략된다({@link JsonInclude}).
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonInclude(Include.NON_NULL)
public final class RpcResponseMessage {
	/** 다중 세션으로 수행하는 경우 할당된 세션의 엔드포인트 */
	@JsonProperty("sessionEndpoint") @Nullable private final String m_sessionEndpoint;
	/** 연산의 수행 상태 */
	@JsonProperty("state") @NotNull private final RpcState m_state;
	/** 연산 수행 결과 (성공적으로 연산이 완료된 경우) */
	@JsonProperty("outputs") @NotNull private final Map<String,JsonNode> m_outputs;
	/** 연산 수행 결과 메시지 (연산 수행이 성공적으로 완료되지 못한 경우) */
	@JsonProperty("error") @Nullable private final RESTfulErrorEntity m_error;
	
	/**
	 * 응답 메시지를 생성한다. (JSON 역직렬화용)
	 *
	 * @param sessionEndpoint 세션 식별자. (다중 세션 비동기 수행이 아니면 {@code null})
	 * @param status    연산 수행 상태. ({@code null} 불가)
	 * @param outputs   연산 출력. ({@code COMPLETED}인 경우 {@code null} 불가)
	 * @param error     실패 정보. ({@code FAILED}인 경우 {@code null} 불가)
	 * @throws IllegalArgumentException {@code status}가 {@code null}인 경우.
	 * @throws IllegalStateException    상태와 필드 구성이 일치하지 않는 경우.
	 *                                  ({@code COMPLETED}인데 {@code outputs}가 없거나,
	 *                                  {@code FAILED}인데 {@code error}가 없는 경우)
	 */
	@JsonCreator
	public RpcResponseMessage(@JsonProperty("sessionEndpoint") String sessionEndpoint,
									@JsonProperty("state") RpcState status,
									@JsonProperty("outputs") Map<String,JsonNode> outputs,
									@JsonProperty("error") RESTfulErrorEntity error) {
		Preconditions.checkNotNullArgument(status, "'status' is null");
		Preconditions.checkState(status != RpcState.COMPLETED || outputs != null,
								"status is COMPLETED but 'outputs' is null");
		Preconditions.checkState(status != RpcState.FAILED || error != null, 
								"status is FAILED but 'error' is null");
		
		m_sessionEndpoint = sessionEndpoint;
		m_state = status;
		m_outputs = outputs;
		m_error = error;
	}
	
	private RpcResponseMessage(Builder builder) {
		Preconditions.checkNotNullArgument(builder.m_state, "'status' is null");
		Preconditions.checkState(builder.m_state != RpcState.COMPLETED || builder.m_outputs != null,
								"status is COMPLETED but 'outputs' is null");
		Preconditions.checkState(builder.m_state != RpcState.FAILED || builder.m_error != null, 
								"status is FAILED but 'error' is null");
								
		m_sessionEndpoint = builder.m_sessionEndpoint;
		m_state = builder.m_state;
		m_outputs = builder.m_outputs;
		m_error = builder.m_error;
	}
	
	/**
	 * 다중 세션으로 수행 중인 연산의 세션 엔드포인트를 반환한다.
	 *
	 * @return 세션 엔드포인트. 비동기 수행이 아니면 {@code null}.
	 */
	public String getSessionEndpoint() {
		return m_sessionEndpoint;
	}

	/**
	 * 연산의 수행 상태를 반환한다.
	 *
	 * @return 연산 수행 상태.
	 */
	public RpcState getState() {
		return m_state;
	}

	/**
	 * 연산 출력을 반환한다. (성공적으로 완료된 경우)
	 *
	 * @return 연산 출력. (출력 이름 → 값) 출력이 없으면 {@code null}.
	 */
	public Map<String,JsonNode> getOutputs() {
		return m_outputs;
	}

	/**
	 * 연산 실패 정보를 반환한다. (실패한 경우)
	 *
	 * @return 실패 정보. 실패가 아니면 {@code null}.
	 */
	public RESTfulErrorEntity getError() {
		return m_error;
	}

	/**
	 * 실패 정보({@link #getError()})로부터 복원된 Java 예외 객체를 반환한다.
	 *
	 * @return 복원된 예외. ({@link RESTfulErrorEntity#toRemoteException()} 참조)
	 * @throws IllegalStateException 연산 상태가 {@code FAILED}가 아니거나 실패 정보가 없는 경우.
	 */
	@JsonIgnore
	@Nullable public RESTfulRemoteException getJavaException() {
		Preconditions.checkState(m_state == RpcState.FAILED, "status is not FAILED: %s", m_state);
		Preconditions.checkState(m_error != null, "error is null");

		return m_error.toRemoteException();
	}
	
	@Override
	public String toString() {
		String valuesStr = (m_outputs != null)? FStream.from(m_outputs.keySet()).join(", ") : "";
		String errorMsg = (m_error != null) ? m_error.toString() : "";
		return String.format("[session=%s] status=%s, outputs={%s}%s",
							this.m_sessionEndpoint, this.m_state, valuesStr, errorMsg);
	}
	
	/**
	 * 비동기 수행 중({@code RUNNING}) 상태의 응답 메시지를 생성한다.
	 *
	 * @param sessionEndpoint 세션 엔드포인트.
	 * @return 생성된 응답 메시지.
	 */
	public static RpcResponseMessage running(String sessionEndpoint) {
		return RpcResponseMessage.builder()
								.session(sessionEndpoint)
								.status(RpcState.RUNNING)
								.build();
	}

	/**
	 * 수행 완료({@code COMPLETED}) 상태의 응답 메시지를 생성한다.
	 *
	 * @param sessionEndpoint 세션 엔드포인트.
	 * @param outputArguments 연산 출력. (출력 이름 → 값)
	 * @return 생성된 응답 메시지.
	 */
	public static RpcResponseMessage completed(String sessionEndpoint, Map<String,JsonNode> outputArguments) {
		return RpcResponseMessage.builder()
								.session(sessionEndpoint)
								.status(RpcState.COMPLETED)
								.outputArguments(outputArguments)
								.build();
	}

	/**
	 * 수행 실패({@code FAILED}) 상태의 응답 메시지를 생성한다.
	 *
	 * @param sessionEndpoint 세션 엔드포인트.
	 * @param cause     실패 원인 예외. {@link RESTfulErrorEntity}로 변환되어 저장된다.
	 * @return 생성된 응답 메시지.
	 */
	public static RpcResponseMessage failed(String sessionEndpoint, Throwable cause) {
		RESTfulErrorEntity error = RESTfulErrorEntity.of(cause);
		return RpcResponseMessage.builder()
								.session(sessionEndpoint)
								.status(RpcState.FAILED)
								.outputArguments(null)
								.error(error)
								.build();
	}

	/**
	 * 수행 취소({@code CANCELLED}) 상태의 응답 메시지를 생성한다.
	 *
	 * @param sessionEndpoint 세션 엔드포인트.
	 * @return 생성된 응답 메시지.
	 */
	public static RpcResponseMessage cancelled(String sessionEndpoint) {
		return RpcResponseMessage.builder()
								.session(sessionEndpoint)
								.status(RpcState.CANCELLED)
								.outputArguments(null)
								.build();
	}

	/**
	 * 응답 본문(JSON)을 {@code RpcResponseMessage}로 역직렬화하는 deserializer를 생성한다.
	 *
	 * @param mapper 역직렬화에 사용할 {@link JsonMapper}.
	 * @return 생성된 deserializer.
	 */
	public static ResponseBodyDeserializer<RpcResponseMessage> newDeserializer(JsonMapper mapper) {
		return new ResponseBodyDeserializer<>() {
			@Override
			public RpcResponseMessage deserialize(Headers headers, String respBody) throws IOException {
				return mapper.readValue(respBody, RpcResponseMessage.class);
			}
		};
	}
	
	/**
	 * {@code RpcResponseMessage}를 생성하기 위한 빌더를 반환한다.
	 *
	 * @return 빌더.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * {@link RpcResponseMessage} 빌더.
	 */
	public static class Builder {
		/** 다중 세션으로 수행하는 경우 할당된 세션의 식별자 */
		private String m_sessionEndpoint;
		/** 연산의 수행 상태 */
		private RpcState m_state;
		/** 연산 수행 결과 (성공적으로 연산이 완료된 경우) */
		private @Nullable Map<String,JsonNode> m_outputs = Map.of();
		/** 연산 수행 결과 메시지 (연산 수행이 성공적으로 완료되지 못한 경우) */
		private @Nullable RESTfulErrorEntity m_error;
		
		/**
		 * 설정된 값으로 {@link RpcResponseMessage}를 생성한다.
		 *
		 * @return 생성된 응답 메시지.
		 */
		public RpcResponseMessage build() {
			return new RpcResponseMessage(this);
		}

		/**
		 * 세션 엔드포인트를 설정한다.
		 *
		 * @param endpoint 세션 엔드포인트.
		 * @return 이 빌더.
		 */
		public Builder session(String endpoint) {
			m_sessionEndpoint = endpoint;
			return this;
		}

		/**
		 * 연산 수행 상태를 설정한다.
		 *
		 * @param status 연산 수행 상태.
		 * @return 이 빌더.
		 */
		public Builder status(RpcState status) {
			m_state = status;
			return this;
		}

		/**
		 * 연산 출력을 설정한다.
		 *
		 * @param outputs 연산 출력. (출력 이름 → 값)
		 * @return 이 빌더.
		 */
		public Builder outputArguments(Map<String,JsonNode> outputs) {
			m_outputs = outputs;
			return this;
		}

		/**
		 * 연산 실패 정보를 설정한다.
		 *
		 * @param error 실패 정보.
		 * @return 이 빌더.
		 */
		public Builder error(RESTfulErrorEntity error) {
			m_error = error;
			return this;
		}
	}
}
