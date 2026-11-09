package utils.rpc.restful;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

import utils.Preconditions;
import utils.async.AbstractPeriodicPoller;
import utils.async.CancellableWork;
import utils.async.IntervalMode;
import utils.func.FOption;
import utils.http.HttpRESTfulClient;
import utils.http.JacksonErrorEntityDeserializer;
import utils.http.RESTfulException;

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
 * 폴링 주기와 타임아웃은 {@link Builder#setPollInterval(Duration)} / {@link Builder#setTimeout(Duration)}로
 * 설정한다.
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
	
	// 세션 URL과 세션 확보 전에 접수한 취소 요청. 폴러 스레드(initializePoller)와 취소하는 스레드가 함께 쓴다.
	private final Object m_sessionLock = new Object();
	private @Nullable String m_sessionUrl;		// m_sessionLock
	private boolean m_cancelPending;			// m_sessionLock
	private boolean m_finishedAtStart;			// m_sessionLock. 시작 응답이 곧 종료 상태였다(세션 없음).
	// 이미 시작된 세션에 붙을 때의 세션 endpoint. 있으면 시작 POST를 보내지 않는다.
	private final @Nullable String m_attachedEndpoint;
	// 시작 응답이 곧 종료 상태(COMPLETED/CANCELLED)였을 때의 응답. 폴러 스레드만 쓴다.
	private @Nullable RpcResponseMessage m_terminalStart;
	
	RESTfulAsyncRpcClient(Builder builder) {
		super(builder.m_pollInterval, IntervalMode.FIXED_RATE);

		Preconditions.checkNotNullArgument(builder.m_httpClient, "httpClient is not specified");
		Preconditions.checkNotNullArgument(builder.m_baseUrl, "baseUrl is not specified");
		if ( builder.m_sessionEndpoint == null ) {
			Preconditions.checkNotNullArgument(builder.m_opEndpoint, "operationUrl is not specified");
			Preconditions.checkNotNullArgument(builder.m_requestMsg, "requestMsg is not specified");
		}
		Preconditions.checkNotNullArgument(builder.m_jsonMapper, "jsonMapper is not specified");
		
		m_baseUrl = builder.m_baseUrl;
		m_attachedEndpoint = builder.m_sessionEndpoint;
		m_opEndpoint = builder.m_opEndpoint != null ? concatUrl(m_baseUrl, builder.m_opEndpoint)
													: concatUrl(m_baseUrl, builder.m_sessionEndpoint);
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
	 * 응답이 곧 종료 상태({@code COMPLETED}/{@code CANCELLED})이면(동기 연산을 비동기로 호출한 경우) 그 응답을
	 * 결과로 삼고 폴링하지 않는다. 세션이 없으므로 이후의 취소는 거부된다.
	 * {@link Builder#setSessionEndpoint(String)}로 기존 세션에 붙은 경우에는 POST 없이 그 세션을 폴링한다.
	 *
	 * @throws ExecutionException	연산이 {@code FAILED}로 시작 실패한 경우.
	 * @throws Exception			HTTP 통신 또는 JSON 처리 중 오류가 발생한 경우.
	 */
	@Override
	protected void initializePoller() throws Exception {
		if ( m_attachedEndpoint != null ) {
			s_logger.info("attaching to rpc session: {}", m_opEndpoint);
			boolean deliver;
			synchronized ( m_sessionLock ) {
				m_sessionUrl = m_opEndpoint;
				deliver = m_cancelPending;
			}
			if ( deliver ) { deliverPendingCancel(); }
			return;
		}
		s_logger.info("calling rpc: {}, inputs={}", m_opEndpoint, m_requestMsg.getInputs());
		
		String requestJson = m_jsonMapper.writeValueAsString(m_requestMsg);
		RequestBody reqBody = RequestBody.create(requestJson, HttpRESTfulClient.MEDIA_TYPE_JSON);
		String respStr = m_restClient.post(m_opEndpoint, reqBody);
		RpcResponseMessage resp = m_jsonMapper.readValue(respStr, RpcResponseMessage.class);

		switch ( resp.getState() ) {
			case RUNNING:
				boolean deliver;
				synchronized ( m_sessionLock ) {
					m_sessionUrl = concatUrl(m_baseUrl, resp.getSessionEndpoint());
					deliver = m_cancelPending;
				}
				if ( deliver ) {
					// POST 응답을 기다리는 동안 접수한 취소를 세션이 생기자마자 보낸다. 폴링은 계속하여
					// 원격 연산의 최종 상태(CANCELLED 등)를 확인한다.
					deliverPendingCancel();
				}
				break;
			case FAILED:
				Throwable cause = resp.getJavaException();
				throw new ExecutionException("operation failed: " + cause.getMessage(), cause);
			case COMPLETED:
			case CANCELLED:
				// 동기 연산을 비동기로 호출하면 POST 응답이 곧 종료 상태다. 첫 tryPoll()이 이 응답으로 끝낸다.
				s_logger.warn("rpc finished at start (synchronous operation?): {}, status={}", m_opEndpoint,
								resp.getState());
				m_terminalStart = resp;
				synchronized ( m_sessionLock ) {
					m_cancelPending = false;
					m_finishedAtStart = true;
				}
				break;
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
		if ( m_terminalStart != null ) {
			RpcResponseMessage start = m_terminalStart;
			if ( start.getState() == RpcState.CANCELLED ) {
				throw new CancellationException("rpc is cancelled at start: " + m_opEndpoint);
			}
			return FOption.of(start.getOutputs());
		}
		String respStr = m_restClient.get(m_sessionUrl);
		RpcResponseMessage resp;
		try {
			resp = m_jsonMapper.readValue(respStr, RpcResponseMessage.class);
		}
		catch ( IOException e ) {
			throw new ExecutionException("rpc failed: " + e.getMessage(), e);
		}
		
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
	 * 원격 연산 세션의 URL을 반환한다.
	 *
	 * @return	세션 URL. 연산 시작 POST의 응답을 아직 받지 못했거나 시작이 실패했으면 {@code null}.
	 */
	public @Nullable String getSessionUrl() {
		synchronized ( m_sessionLock ) {
			return m_sessionUrl;
		}
	}

	private void deliverPendingCancel() {
		try {
			boolean accepted = cancelWork();
			getLogger().info("delivered a cancel requested before the session existed: session={}, accepted={}",
							getSessionUrl(), accepted);
		}
		catch ( RuntimeException e ) {
			// 취소 전달 실패로 폴링을 멈추지 않는다. 원격 연산의 최종 상태는 폴링으로 확인한다.
			getLogger().warn("failed to deliver a pending cancel: session={}, cause={}", getSessionUrl(), e.toString());
		}
	}

	/**
	 * 원격 세션에 취소를 요청한다.
	 * <p>
	 * 세션 URL로 HTTP DELETE를 보내 원격 연산을 취소시키고, 그 응답으로 취소 접수 여부를 판단한다.
	 * 취소가 접수된 응답은 두 가지이다.
	 * <ul>
	 *   <li>{@code CANCELLED} — 취소가 즉시 확정된 경우. ({@code 200 OK})</li>
	 *   <li>{@code RUNNING} — 정지에 시간이 걸리는 서버(예: 로봇 트윈)가 취소 접수만 알리는 경우.
	 *       ({@code 202 Accepted}, {@code phase: CANCELING}) 원격 연산은 아직 정지 중일 수 있으며,
	 *       최종 {@code CANCELLED}는 이후의 세션 조회로만 확인된다.</li>
	 * </ul>
	 * <p>
	 * 연산 시작 POST의 응답을 아직 받지 못해 세션이 없으면, 취소를 기록하고 {@code true}를 반환한다.
	 * 세션을 받는 즉시 폴러 스레드가 DELETE를 보내고 폴링을 이어 가 최종 상태를 확인한다. 그 사이 시작이
	 * 실패하면(POST 거부) 보낼 대상이 없으므로 기록은 버려진다.
	 * 시작 응답이 곧 종료 상태였으면(세션 없음) {@code false}를 반환한다.
	 * 대상 세션이 이미 종료 상태여서 취소가 허용되지 않으면 {@code 409 Conflict}가 온다.
	 * 이 경우 취소할 대상이 없다는 뜻이므로 예외 대신 {@code false}를 반환한다.
	 * <p>
	 * 세션이 존재하지 않거나 보존 기간이 지나 회수된 경우({@code 404 Not Found})는 취소 거부와 구분되는
	 * 상황이므로 {@link utils.http.RESTfulException}이 그대로 전파된다.
	 *
	 * @return	취소가 접수되었으면 {@code true}, 이미 종료되어 취소할 수 없으면 {@code false}.
	 * 			{@code true}가 원격 연산의 정지 완료를 뜻하지는 않는다.
	 * @throws utils.http.RESTfulException	세션이 존재하지 않는 등 취소 요청 자체가 실패한 경우.
	 */
	@Override
	public boolean cancelWork() {
		String sessionUrl;
		synchronized ( m_sessionLock ) {
			sessionUrl = m_sessionUrl;
			if ( m_finishedAtStart ) {
				return false;	// 시작 응답에서 이미 종료되어 취소할 세션이 없다.
			}
			if ( sessionUrl == null ) {
				// 연산 시작 POST의 응답(세션)을 아직 받지 못했다. 취소를 기록해 두고, 세션을 받는 즉시
				// initializePoller()가 DELETE를 보낸다.
				m_cancelPending = true;
				return true;
			}
		}
		RpcResponseMessage resp;
		try {
			resp = m_restClient.delete(sessionUrl, RpcResponseMessage.newDeserializer(m_jsonMapper));
		}
		catch ( RESTfulException e ) {
			if ( e.getHttpStatusCode() == HttpURLConnection.HTTP_CONFLICT ) {
				// 대상 세션이 이미 종료 상태여서 취소가 허용되지 않는 경우.
				getLogger().info("rpc is already finished, cannot cancel: session={}", sessionUrl);
				return false;
			}
			throw e;
		}
		
		// 취소가 접수된 응답은 CANCELLED(확정) 또는 RUNNING(정지 중)뿐이다. 그 외의 종료 상태는 409로 전달된다.
		switch ( resp.getState() ) {
			case CANCELLED:
				return true;
			case RUNNING:
				getLogger().info("rpc cancel is accepted, remote operation is still stopping: session={}",
								sessionUrl);
				return true;
			default:
				throw new AssertionError("unexpected rpc status: " + resp.getState());
		}
	}
	
	/**
	 * 기본 URL과 경로를 정확히 하나의 '/' 구분자로 결합한다.
	 * <p>
	 * {@code base}의 끝이나 {@code path}의 앞에 붙은 '/'의 유무와 무관하게 동일한 결과를 만든다.
	 *
	 * @param base	기본 URL.
	 * @param path	결합할 경로.
	 * @return	결합된 URL.
	 */
	private static String concatUrl(String base, String path) {
		if ( base.endsWith("/") ) {
			base = base.substring(0, base.length() - 1);
		}
		return path.startsWith("/") ? base + path : base + "/" + path;
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
		private String m_sessionEndpoint;

		/**
		 * 이미 시작된 원격 연산 세션에 붙는다. 설정하면 시작 POST를 보내지 않고 이 세션을 폴링하며,
		 * {@code operationEndpoint}와 {@code requestMessage}는 필요 없다.
		 * <p>
		 * 동기 호출이 {@code RUNNING}을 받은 경우({@link UnexpectedRpcStateException})처럼 세션 endpoint만 아는
		 * 연산의 결과를 기다리거나 취소할 때 쓴다.
		 *
		 * @param endpoint	서버가 돌려준 {@code session_endpoint}. 기본 URL에 붙여 폴링 URL을 만든다.
		 * @return	이 빌더.
		 */
		public Builder setSessionEndpoint(String endpoint) {
			m_sessionEndpoint = endpoint;
			return this;
		}

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
