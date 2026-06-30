package utils.rpc.restful;

import java.time.Duration;
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
import utils.async.AbstractPeriodicPoller;
import utils.async.CancellableWork;
import utils.func.FOption;
import utils.http.HttpRESTfulClient;
import utils.http.JacksonErrorEntityDeserializer;

/**
 * 원격 연산(operation)을 비동기 방식의 HTTP로 호출하는 클라이언트.
 * <p>
 * {@link Builder}로 엔드포인트와 입력을 설정한 뒤 실행하면, 연산을 시작시키는 최초 POST 요청으로
 * 세션을 얻고({@code RUNNING}), 이후 {@link AbstractPeriodicPoller}의 주기적 폴링으로 세션 상태를
 * 조회하여 완료를 기다린다. 연산 상태에 따라 다음과 같이 동작한다.
 * <ul>
 *   <li>{@code COMPLETED} - 연산 출력을 결과로 반환한다.</li>
 *   <li>{@code FAILED} - 복원된 원인 예외를 cause로 갖는 {@link ExecutionException}을 던진다.</li>
 *   <li>{@code CANCELLED} - {@link CancellationException}을 던진다.</li>
 * </ul>
 * 폴링 주기와 타임아웃은 {@link Builder#setPollInterval(Duration)} / {@link Builder#setTimeout(Duration)}로 설정한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulAsyncRpcClient extends AbstractPeriodicPoller<Map<String,JsonNode>>
									implements CancellableWork {
	private static final Logger s_logger = LoggerFactory.getLogger(RESTfulAsyncRpcClient.class);
	
	private final HttpRESTfulClient m_restClient;
	private final String m_baseUrl;
	private final String m_opEndpoint;
	private final RpcRequestMessage m_requestMsg;
	private final JsonMapper m_jsonMapper;
	
	private String m_sessionUrl;
	
	RESTfulAsyncRpcClient(Builder builder) {
		super(builder.m_pollInterval, true);

		Preconditions.checkNotNullArgument(builder.m_httpClient, "httpClient is not specified");
		Preconditions.checkNotNullArgument(builder.m_baseUrl, "baseUrl is not specified");
		Preconditions.checkNotNullArgument(builder.m_opEndpoint, "operationUrl is not specified");
		Preconditions.checkNotNullArgument(builder.m_requestMsg, "requestMsg is not specified");
		Preconditions.checkNotNullArgument(builder.m_jsonMapper, "jsonMapper is not specified");
		
		m_baseUrl = builder.m_baseUrl;
		m_opEndpoint = m_baseUrl + "/" + builder.m_opEndpoint;
		m_requestMsg = builder.m_requestMsg;
		m_jsonMapper = builder.m_jsonMapper;
		setTimeout(builder.m_timeout);
		
		m_restClient = HttpRESTfulClient.builder()
										.httpClient(builder.m_httpClient)
										.errorEntityDeserializer(new JacksonErrorEntityDeserializer(m_jsonMapper))
										.jsonMapper(m_jsonMapper)
										.build();
		
		setLogger(s_logger);
	}

	/**
	 * 폴링을 시작하기 전에 연산을 호출하여 세션을 확보한다.
	 * <p>
	 * 연산 시작 POST 요청을 보내고, 응답이 {@code RUNNING}이면 세션 endpoint로부터 세션 상태 조회 URL을
	 * 구성하여 이후 폴링 대상으로
	 * 삼는다. 응답이 {@code FAILED}이면 복원된 원인을 cause로 갖는 {@link ExecutionException}을 던진다.
	 *
	 * @throws ExecutionException	연산이 {@code FAILED}로 시작 실패한 경우.
	 * @throws Exception			HTTP 통신 또는 JSON 처리 중 오류가 발생한 경우.
	 */
	@Override
	protected void initializePoller() throws Exception {
		s_logger.info("calling rpc: {}, inputs={}", m_opEndpoint, m_requestMsg.getInputs());
		
		String requestJson = m_jsonMapper.writeValueAsString(m_requestMsg);
		RequestBody reqBody = RequestBody.create(requestJson, HttpRESTfulClient.MEDIA_TYPE_JSON);
		RpcResponseMessage resp = m_restClient.post(m_opEndpoint, reqBody,
													RpcResponseMessage.newDeserializer(m_jsonMapper));

		// 비동기 연산인 경우에는 RUNNING, FAILED 상태만 가능함.											
		switch ( resp.getState() ) {
			case RUNNING:
				m_sessionUrl = m_baseUrl + resp.getSessionEndpoint();
				break;
			case FAILED:
				Throwable cause = resp.getJavaException();
				throw new ExecutionException("operation failed: " + cause.getMessage(), cause);
			default:
				throw new AssertionError("unexpected status: " + resp.getState());
		}
	}
	
	/**
	 * 세션 상태를 한 번 조회하여 완료 여부를 판단한다.
	 * <p>
	 * 상태가 {@code RUNNING}이면 아직 완료되지 않았으므로 {@link FOption#empty()}를 반환하여 폴링을
	 * 계속하고, {@code COMPLETED}이면 연산 출력을 담아 반환한다. {@code FAILED}/{@code CANCELLED}는
	 * 각각 {@link ExecutionException}/{@link CancellationException}으로 종료시킨다.
	 *
	 * @return	완료 시 연산 출력, 진행 중이면 {@link FOption#empty()}.
	 * @throws ExecutionException	연산이 {@code FAILED}로 종료된 경우.
	 * @throws CancellationException	연산이 {@code CANCELLED}로 종료된 경우.
	 * @throws InterruptedException	조회 대기 중 인터럽트된 경우.
	 */
	@Override
	protected FOption<Map<String,JsonNode>> tryPoll() throws InterruptedException, CancellationException,
															ExecutionException {
		RpcResponseMessage resp = m_restClient.get(m_sessionUrl + "/state",
														RpcResponseMessage.newDeserializer(m_jsonMapper));
		switch ( resp.getState() ) {
			case RUNNING:
				getLogger().info("rpc is running, session={}", m_opEndpoint);
				return FOption.empty();
			case COMPLETED:
				getLogger().info("rpc is completed, session={}, outputs={}", m_opEndpoint, resp.getOutputs());
				return FOption.of(resp.getOutputs());
			case FAILED:
				Throwable cause = resp.getJavaException();
				throw new ExecutionException("rpc failed: " + cause.getMessage(), cause);
			case CANCELLED:
				throw new CancellationException("rpc is cancelled: session=" + m_opEndpoint);
			default:
				throw new AssertionError("unexpected rpc status: " + resp.getState());
		}
	}

	/**
	 * 원격 세션에 취소를 요청한다.
	 * <p>
	 * 세션 URL로 HTTP DELETE를 보내 원격 연산을 취소시키고, 그 응답 상태로 취소 접수 여부를 판단한다.
	 * 아직 수행 중({@code RUNNING})이거나 이미 취소({@code CANCELLED})된 경우는 취소가 유효하므로
	 * {@code true}를, 이미 완료({@code COMPLETED})된 경우는 {@code false}를 반환한다.
	 *
	 * @return	취소가 접수되었으면 {@code true}, 이미 완료되어 취소할 수 없으면 {@code false}.
	 * @throws utils.http.RESTfulRemoteException	원격 연산이 {@code FAILED}로 응답한 경우.
	 */
	@Override
	public boolean cancelWork() {
		RpcResponseMessage resp = m_restClient.delete(m_sessionUrl,
													RpcResponseMessage.newDeserializer(m_jsonMapper));
		switch ( resp.getState() ) {
			case RUNNING:
				return true;
			case COMPLETED:
				return false;
			case FAILED:
				throw resp.getJavaException();
			case CANCELLED:
				return true;
			default:
				throw new AssertionError("unexpected rpc status: " + resp.getState());
		}
	}
	
	/**
	 * {@code RESTfulAsyncRpcClient}를 생성하기 위한 빌더를 반환한다.
	 *
	 * @return	빌더.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * {@link RESTfulAsyncRpcClient} 빌더.
	 * <p>
	 * {@code httpClient}, {@code baseUrl}, {@code operationEndpoint}, {@code requestBody},
	 * {@code jsonMapper}는 필수이며 {@link #build()} 시점에 검증된다.
	 * 폴링 주기({@code pollInterval})의 기본값은 3초이다.
	 */
	public static final class Builder {
		private OkHttpClient m_httpClient;
		private String m_baseUrl;
		private String m_opEndpoint;
		private JsonMapper m_jsonMapper;
		private RpcRequestMessage m_requestMsg;
		private Duration m_pollInterval = Duration.ofSeconds(3);
		private Duration m_timeout;

		/**
		 * 설정된 값으로 {@link RESTfulAsyncRpcClient}를 생성한다.
		 *
		 * @return	생성된 클라이언트.
		 * @throws IllegalArgumentException	필수 항목이 설정되지 않은 경우.
		 */
		public RESTfulAsyncRpcClient build() {
			return new RESTfulAsyncRpcClient(this);
		}

		/**
		 * HTTP 통신에 사용할 {@link OkHttpClient}를 설정한다. (필수)
		 *
		 * @param http	OkHttp 클라이언트.
		 * @return	이 빌더.
		 */
		public Builder setHttpClient(OkHttpClient http) {
			m_httpClient = http;
			return this;
		}

		/**
		 * 연산 서버의 기본 URL을 설정한다. (필수)
		 * <p>
		 * 연산 호출 URL과 세션 상태 조회 URL의 접두어로 사용된다.
		 *
		 * @param baseUrl	기본 URL.
		 * @return	이 빌더.
		 */
		public Builder setBaseUrl(String baseUrl) {
			m_baseUrl = baseUrl;
			return this;
		}

		/**
		 * 연산 엔드포인트를 설정한다. (필수)
		 * <p>
		 * 최종 연산 호출 URL은 {@code baseUrl + "/" + endpoint}로 구성된다.
		 *
		 * @param endpoint	연산 엔드포인트.
		 * @return	이 빌더.
		 */
		public Builder setOperationEndpoint(String endpoint) {
			m_opEndpoint = endpoint;
			return this;
		}

		/**
		 * JSON 직렬화/역직렬화에 사용할 {@link JsonMapper}를 설정한다. (필수)
		 *
		 * @param mapper	JSON 매퍼.
		 * @return	이 빌더.
		 */
		public Builder setJsonMapper(JsonMapper mapper) {
			m_jsonMapper = mapper;
			return this;
		}

		/**
		 * 연산 호출 요청 메시지를 설정한다. (필수)
		 *
		 * @param body	요청 메시지.
		 * @return	이 빌더.
		 */
		public Builder setRequestMessage(RpcRequestMessage body) {
			m_requestMsg = body;
			return this;
		}

		/**
		 * 세션 상태 폴링 주기를 설정한다. (기본값 3초)
		 *
		 * @param interval	폴링 주기.
		 * @return	이 빌더.
		 */
		public Builder setPollInterval(Duration interval) {
			m_pollInterval = interval;
			return this;
		}

		/**
		 * 연산 완료를 기다리는 전체 제한 시간을 설정한다.
		 *
		 * @param timeout	제한 시간. {@code null}이면 무제한.
		 * @return	이 빌더.
		 */
		public Builder setTimeout(Duration timeout) {
			m_timeout = timeout;
			return this;
		}
	}
}
