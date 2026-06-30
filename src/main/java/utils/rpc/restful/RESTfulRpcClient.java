package utils.rpc.restful;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

import utils.Preconditions;
import utils.func.FOption;
import utils.http.HttpRESTfulClient;
import utils.http.JacksonErrorEntityDeserializer;
import utils.http.RESTfulIOException;
import utils.json.JacksonUtils;

/**
 * 원격 연산(operation)을 동기 방식의 HTTP로 호출하는 클라이언트.
 * <p>
 * {@link Builder}로 엔드포인트와 입력을 설정한 뒤 {@link #call()}을 호출하면, 응답이 도착할 때까지
 * 블록하고 연산 상태에 따라 출력을 반환하거나 예외를 던진다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulRpcClient {
	private static final Logger s_logger = LoggerFactory.getLogger(RESTfulRpcClient.class);

	private final String m_operationUrl;
	private final JsonMapper m_jsonMapper;
	private final RpcRequestMessage m_requestMsg;
	
	private final HttpRESTfulClient m_restClient;
	
	private RESTfulRpcClient(Builder builder) {
		m_operationUrl = builder.m_operationUrl;
		m_requestMsg = new RpcRequestMessage(builder.m_inputs);
		m_jsonMapper = FOption.getOrElse(builder.m_jsonMapper, JacksonUtils.newJsonMapper(false));
		
		m_restClient = HttpRESTfulClient.builder()
										.httpClient(builder.m_httpClient)
										.errorEntityDeserializer(new JacksonErrorEntityDeserializer(m_jsonMapper))
										.jsonMapper(m_jsonMapper)
										.build();
	}
	
	/**
	 * 연산을 동기적으로 호출하고 결과 출력을 반환한다.
	 *
	 * @return 연산 출력. (출력 이름 → 값)
	 * @throws IOException                HTTP 통신 또는 JSON 직렬화/역직렬화 중 오류가 발생하거나,
	 *                                    원격 연산 실패가 {@link RESTfulIOException}으로 복원된 경우.
	 * @throws ExecutionException         원격 연산이 {@code FAILED} 상태로 종료된 경우.
	 *                                    (복원된 원인 예외가 cause로 설정된다)
	 * @throws CancellationException      원격 연산이 {@code CANCELLED} 상태로 종료된 경우. (unchecked)
	 * @throws IllegalStateException      예상치 못한 연산 상태가 반환된 경우. (unchecked)
	 */
	public Map<String,JsonNode> call() throws IOException, ExecutionException {
		s_logger.info("calling operation: {} with inputs={}", m_operationUrl, m_requestMsg.getInputs());
		
		String requestJson = m_jsonMapper.writeValueAsString(m_requestMsg);
		RequestBody reqBody = RequestBody.create(requestJson, HttpRESTfulClient.MEDIA_TYPE_JSON);
		RpcResponseMessage resp = m_restClient.post(m_operationUrl, reqBody,
													RpcResponseMessage.newDeserializer(m_jsonMapper));
		switch (resp.getState()) {
			case COMPLETED:
				return resp.getOutputs();
			case FAILED:
				Throwable cause = resp.getJavaException();
				if ( cause instanceof RESTfulIOException rioe ) {
					throw rioe;
				}
				else {
					throw new ExecutionException("operation failed: " + cause.getMessage(), cause);
				}
			case CANCELLED:
				throw new CancellationException("operation is cancelled");
			default:
				throw new IllegalStateException("unexpected status: " + resp.getState());
		}
	}
	
	/**
	 * {@code RESTfulRpcClient}를 생성하기 위한 빌더를 반환한다.
	 *
	 * @return	빌더.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * {@link RESTfulRpcClient} 빌더.
	 * <p>
	 * {@code endpoint}는 필수이며 {@link #build()} 시점에 검증된다. {@code jsonMapper}를 지정하지 않으면
	 * 기본 매퍼({@link JacksonUtils#newJsonMapper(boolean)})가 사용된다.
	 */
	public static final class Builder {
		private OkHttpClient m_httpClient;
		private String m_operationUrl;
		private JsonMapper m_jsonMapper;
		private Map<String,JsonNode> m_inputs;

		/**
		 * 설정된 값으로 {@link RESTfulRpcClient}를 생성한다.
		 *
		 * @return	생성된 클라이언트.
		 * @throws IllegalArgumentException	{@code endpoint}가 설정되지 않은 경우.
		 */
		public RESTfulRpcClient build() {
			Preconditions.checkNotNullArgument(m_operationUrl, "operationUrl is not specified");
			return new RESTfulRpcClient(this);
		}

		/**
		 * HTTP 통신에 사용할 {@link OkHttpClient}를 설정한다.
		 *
		 * @param http	OkHttp 클라이언트.
		 * @return	이 빌더.
		 */
		public Builder setHttpClient(OkHttpClient http) {
			m_httpClient = http;
			return this;
		}

		/**
		 * 연산 호출 URL을 설정한다. (필수)
		 *
		 * @param endpoint	연산 호출 URL.
		 * @return	이 빌더.
		 */
		public Builder setEndpoint(String endpoint) {
			m_operationUrl = endpoint;
			return this;
		}

		/**
		 * JSON 직렬화/역직렬화에 사용할 {@link JsonMapper}를 설정한다.
		 * <p>
		 * 지정하지 않으면 기본 매퍼가 사용된다.
		 *
		 * @param mapper	JSON 매퍼.
		 * @return	이 빌더.
		 */
		public Builder setJsonMapper(JsonMapper mapper) {
			m_jsonMapper = mapper;
			return this;
		}

		/**
		 * 연산 입력 인자를 설정한다.
		 *
		 * @param inputs	입력 인자. (입력 이름 → 값)
		 * @return	이 빌더.
		 */
		public Builder setInputs(Map<String,JsonNode> inputs) {
			m_inputs = inputs;
			return this;
		}
	}
}
