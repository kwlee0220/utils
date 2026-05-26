package utils.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.Maps;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import utils.LoggerSettable;
import utils.Preconditions;
import utils.Throwables;
import utils.Tuple;
import utils.func.Optionals;
import utils.func.Try;


/**
 * OkHttp 기반의 동기 RESTful 클라이언트.
 * <p>
 * {@link Builder}를 통해 생성하며, 등록된 default 헤더는 모든 요청에 자동으로 적용된다.
 * 응답 본문 파싱은 호출 시 전달하는 {@link ResponseBodyDeserializer}로 위임하고,
 * 에러 응답은 {@link ErrorEntityDeserializer}로 파싱하여 적절한 예외로 변환해 던진다.
 *
 * <h3>사용 예</h3>
 * <pre>{@code
 * HttpRESTfulClient client = HttpRESTfulClient.builder()
 *         .header("Authorization", "Bearer ...")
 *         .build();
 * String body = client.get("https://api.example.com/v1/items", HttpRESTfulClient.STRING_DESER);
 * }</pre>
 *
 * <h3>예외</h3>
 * 모든 호출 메서드는 다음 unchecked 예외를 던질 수 있다.
 * <ul>
 *   <li>{@link RESTfulIOException} – 네트워크 오류, 응답 파싱 실패, 또는 알 수 없는 서버 에러</li>
 *   <li>{@link RESTfulRemoteException} – 서버가 구조화된 에러 엔티티를 반환한 경우</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class HttpRESTfulClient implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(HttpRESTfulClient.class);

	/** {@code text/plain; charset=utf-8} 미디어 타입 상수. */
	public static final MediaType MEDIA_TYPE_TEXT = MediaType.get("text/plain; charset=utf-8");
	/** {@code application/json; charset=utf-8} 미디어 타입 상수. */
	public static final MediaType MEDIA_TYPE_JSON = MediaType.get("application/json; charset=utf-8");
	
	private final OkHttpClient m_client;
	private final LinkedHashMap<String,String> m_headers;
	private final ErrorEntityDeserializer m_errorEntityDeser;
	private final JsonMapper m_mapper;
	private Logger m_logger = null;
	
	/**
	 * 기본 설정으로 새 클라이언트를 생성한다.
	 * <p>
	 * {@code HttpRESTfulClient.builder().build()}와 동일하며, 헤더와 사용자 정의
	 * 컴포넌트는 모두 default를 사용한다.
	 *
	 * @return 새로 생성된 클라이언트.
	 */
	public static HttpRESTfulClient newDefaultClient() {
		return builder().build();
	}

	private HttpRESTfulClient(OkHttpClient client, LinkedHashMap<String,String> headers,
								ErrorEntityDeserializer deser, JsonMapper mapper) {
		Preconditions.checkNotNullArgument(client, "httpClient is null");
		Preconditions.checkNotNullArgument(deser, "deserializer is null");

		m_client = client;
		m_headers = Maps.newLinkedHashMap(headers);
		m_errorEntityDeser = deser;
		m_mapper = mapper;
	}

	/**
	 * 내부에서 사용 중인 {@link OkHttpClient}를 반환한다.
	 *
	 * @return 빌더에 명시되었거나 default로 생성된 OkHttp 클라이언트.
	 */
	public OkHttpClient getHttpClient() {
		return m_client;
	}

	/**
	 * 주어진 URL에 GET 요청을 보내고 응답 본문을 파싱하여 반환한다.
	 *
	 * @param <T>   응답 본문을 매핑할 타입.
	 * @param url   요청 URL.
	 * @param deser 응답 본문 deserializer.
	 * @return deserialize된 응답 본문.
	 */
	public <T> T get(String url, ResponseBodyDeserializer<T> deser) {
		getLogger().debug("sending: (GET) {}", url);
		Request req = newRequestBuilder(url).get().build();
		return call(req, deser);
	}

	/**
	 * 주어진 URL에 GET 요청을 보내고 응답 본문은 무시한다.
	 *
	 * @param url 요청 URL.
	 */
	public void get(String url) {
		getLogger().debug("sending: (GET) {}", url);
		Request req = newRequestBuilder(url).get().build();
		callVoid(req);
	}

	/**
	 * 주어진 {@link HttpUrl}에 GET 요청을 보내고 응답 본문을 파싱하여 반환한다.
	 *
	 * @param <T>   응답 본문을 매핑할 타입.
	 * @param url   요청 URL.
	 * @param deser 응답 본문 deserializer.
	 * @return deserialize된 응답 본문.
	 */
	public <T> T get(HttpUrl url, ResponseBodyDeserializer<T> deser) {
		getLogger().debug("sending: (GET) {}", url);
		Request req = newRequestBuilder(url).get().build();
		return call(req, deser);
	}

	/**
	 * 사용자가 직접 작성한 {@link Request}를 실행하고, 응답 본문과 헤더를 함께 반환한다.
	 * <p>
	 * 이 메서드는 빌더에 등록된 default 헤더를 자동으로 적용하지만,
	 * {@code req}에 이미 동일한 이름의 헤더가 설정되어 있으면 사용자 값이 우선한다.
	 *
	 * @param <T>   응답 본문을 매핑할 타입.
	 * @param req   실행할 Request.
	 * @param deser 응답 본문 deserializer.
	 * @return deserialize된 응답 본문과 응답 헤더의 튜플.
	 */
	public <T> Tuple<T, Headers> callAndGetHeaders(Request req, ResponseBodyDeserializer<T> deser) {
		Response resp = call(applyDefaultHeaders(req));
		return parseResponse(resp, deser);
	}

	/**
	 * 주어진 URL에 POST 요청을 보내고 응답 본문을 파싱하여 반환한다.
	 *
	 * @param <T>     응답 본문을 매핑할 타입.
	 * @param url     요청 URL.
	 * @param reqBody 요청 본문.
	 * @param deser   응답 본문 deserializer.
	 * @return deserialize된 응답 본문.
	 */
	public <T> T post(String url, RequestBody reqBody, ResponseBodyDeserializer<T> deser) {
		getLogger().debug("sending: (POST) {}, body={}", url, reqBody);
		Request req = newRequestBuilder(url).post(reqBody).build();
		return call(req, deser);
	}
	
	public <T> T post(String url, String reqBodyStr, ResponseBodyDeserializer<T> deser) {
		return post(url, RequestBody.create(reqBodyStr, MEDIA_TYPE_TEXT), deser);
	}

	/**
	 * 주어진 URL에 POST 요청을 보내고 응답 본문은 무시한다.
	 *
	 * @param url     요청 URL.
	 * @param reqBody 요청 본문.
	 */
	public void post(String url, RequestBody reqBody) {
		getLogger().debug("sending: (POST) {}, body={}", url, reqBody);
		Request req = newRequestBuilder(url).post(reqBody).build();
		callVoid(req);
	}

	/**
	 * 주어진 URL에 PUT 요청을 보내고 응답 본문을 파싱하여 반환한다.
	 *
	 * @param <T>     응답 본문을 매핑할 타입.
	 * @param url     요청 URL.
	 * @param reqBody 요청 본문.
	 * @param deser   응답 본문 deserializer.
	 * @return deserialize된 응답 본문.
	 */
	public <T> T put(String url, RequestBody reqBody, ResponseBodyDeserializer<T> deser) {
		getLogger().debug("sending: (PUT) {}, body={}", url, reqBody);
		Request req = newRequestBuilder(url).put(reqBody).build();
		return call(req, deser);
	}
	
	public <T> T put(String url, String reqBodyStr, ResponseBodyDeserializer<T> deser) {
		return put(url, RequestBody.create(reqBodyStr, MEDIA_TYPE_TEXT), deser);
	}

	/**
	 * 주어진 URL에 PUT 요청을 보내고 응답 본문은 무시한다.
	 *
	 * @param url     요청 URL.
	 * @param reqBody 요청 본문.
	 */
	public void put(String url, RequestBody reqBody) {
		getLogger().debug("sending: (PUT) {}, body={}", url, reqBody);
		Request req = newRequestBuilder(url).put(reqBody).build();
		callVoid(req);
	}

	/**
	 * 주어진 URL에 PATCH 요청을 보내고 응답 본문은 무시한다.
	 *
	 * @param url     요청 URL.
	 * @param reqBody 요청 본문.
	 */
	public void patch(String url, RequestBody reqBody) {
		getLogger().debug("sending: (PATCH) {}, body={}", url, reqBody);
		Request req = newRequestBuilder(url).patch(reqBody).build();
		callVoid(req);
	}

	/**
	 * 주어진 URL에 PATCH 요청을 보내고 응답 본문을 파싱하여 반환한다.
	 *
	 * @param <T>     응답 본문을 매핑할 타입.
	 * @param url     요청 URL.
	 * @param reqBody 요청 본문.
	 * @param deser   응답 본문 deserializer.
	 * @return deserialize된 응답 본문.
	 */
	public <T> T patch(String url, RequestBody reqBody, ResponseBodyDeserializer<T> deser) {
		getLogger().debug("sending: (PATCH) {}, body={}", url, reqBody);
		Request req = newRequestBuilder(url).patch(reqBody).build();
		return call(req, deser);
	}

	/**
	 * 주어진 URL에 HEAD 요청을 보내고 응답 헤더를 반환한다.
	 *
	 * @param url 요청 URL.
	 * @return 응답 헤더.
	 */
	public Headers head(String url) {
		getLogger().debug("sending: (HEAD) {}", url);
		Request req = newRequestBuilder(url).head().build();
		return getVoidResponse(call(req));
	}

	/**
	 * 주어진 URL에 DELETE 요청을 보내고 응답 본문은 무시한다.
	 *
	 * @param url 요청 URL.
	 */
	public void delete(String url) {
		getLogger().debug("sending: (DELETE) {}", url);
		Request req = newRequestBuilder(url).delete().build();
		callVoid(req);
	}

	/**
	 * 주어진 URL에 DELETE 요청을 보내고 응답 본문을 파싱하여 반환한다.
	 *
	 * @param <T>   응답 본문을 매핑할 타입.
	 * @param url   요청 URL.
	 * @param deser 응답 본문 deserializer.
	 * @return deserialize된 응답 본문.
	 */
	public <T> T delete(String url, ResponseBodyDeserializer<T> deser) {
		getLogger().debug("sending: (DELETE) {}", url);
		Request req = newRequestBuilder(url).delete().build();
		return call(req, deser);
	}

	private Request.Builder newRequestBuilder(String url) {
		Request.Builder builder = new Request.Builder().url(url);
		m_headers.forEach(builder::addHeader);
		return builder;
	}

	private Request.Builder newRequestBuilder(HttpUrl url) {
		Request.Builder builder = new Request.Builder().url(url);
		m_headers.forEach(builder::addHeader);
		return builder;
	}

	private Request applyDefaultHeaders(Request req) {
		if ( m_headers.isEmpty() ) {
			return req;
		}
		Request.Builder builder = req.newBuilder();
		m_headers.forEach((name, value) -> {
			if ( req.header(name) == null ) {
				builder.addHeader(name, value);
			}
		});
		return builder.build();
	}

	private <T> T call(Request req, ResponseBodyDeserializer<T> deser) {
		Response resp = call(req);
		return parseResponse(resp, deser)._1;
	}

	private void callVoid(Request req) {
		Response resp = call(req);
		getVoidResponse(resp);
	}

	@Override
	public Logger getLogger() {
		return Optionals.getOrElse(m_logger, s_logger);
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}

	/**
	 * 새 {@link Builder}를 반환한다.
	 *
	 * @return 클라이언트 빌더.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * {@link HttpRESTfulClient}의 빌더.
	 * <p>
	 * 같은 인스턴스로 {@link #build()}를 여러 번 호출해도 안전하며, 호출 시마다
	 * default 컴포넌트(OkHttp 클라이언트, JSON 매퍼 등)는 새로 생성된다.
	 */
	public static class Builder {
		private OkHttpClient m_httpClient;
		private LinkedHashMap<String,String> m_headers = Maps.newLinkedHashMap();
		private ErrorEntityDeserializer m_deser;
		private JsonMapper m_mapper;

		private Builder() { }

		/**
		 * 현재 설정으로 새 클라이언트를 생성한다.
		 *
		 * @return 새 {@link HttpRESTfulClient} 인스턴스.
		 */
		public HttpRESTfulClient build() {
			OkHttpClient client = Optionals.getOrElse(m_httpClient, OkHttpClientUtils.newClient());
			JsonMapper mapper = (m_mapper != null) ? m_mapper : new JsonMapper();
			ErrorEntityDeserializer deser = (m_deser != null)
											? m_deser
											: new JacksonErrorEntityDeserializer(mapper);
			return new HttpRESTfulClient(client, m_headers, deser, mapper);
		}

		/**
		 * 사용할 {@link OkHttpClient}를 지정한다.
		 * 미지정 시 {@link OkHttpClientUtils#newClient()}로 생성한 default가 사용된다.
		 *
		 * @param client OkHttp 클라이언트.
		 * @return 이 빌더.
		 */
		public Builder httpClient(OkHttpClient client) {
			m_httpClient = client;
			return this;
		}

		/**
		 * 모든 요청에 자동으로 추가될 default 헤더를 등록한다.
		 * 같은 이름으로 다시 호출하면 기존 값을 덮어쓴다.
		 *
		 * @param name  헤더 이름.
		 * @param value 헤더 값.
		 * @return 이 빌더.
		 */
		public Builder header(String name, String value) {
			m_headers.put(name, value);
			return this;
		}

		/**
		 * 에러 응답 본문을 {@link RESTfulErrorEntity}로 파싱할 deserializer를 지정한다.
		 * 미지정 시 {@link JacksonErrorEntityDeserializer}가 사용된다.
		 *
		 * @param deser 에러 엔티티 deserializer.
		 * @return 이 빌더.
		 */
		public Builder errorEntityDeserializer(ErrorEntityDeserializer deser) {
			m_deser = deser;
			return this;
		}

		/**
		 * 내부 JSON 파싱에 사용할 {@link JsonMapper}를 지정한다.
		 * 미지정 시 default {@code new JsonMapper()}가 사용된다.
		 *
		 * @param mapper JSON 매퍼.
		 * @return 이 빌더.
		 */
		public Builder jsonMapper(JsonMapper mapper) {
			m_mapper = mapper;
			return this;
		}
	}

	/**
	 * 응답 본문을 임의의 타입 {@code T}로 변환하는 함수형 인터페이스.
	 *
	 * @param <T> deserialize 결과 타입.
	 */
	@FunctionalInterface
	public static interface ResponseBodyDeserializer<T> {
		/**
		 * 응답을 deserialize한다.
		 *
		 * @param headers 응답 헤더.
		 * @param respBody 응답 본문 문자열.
		 * @return deserialize된 객체.
		 * @throws IOException 파싱 실패 시.
		 */
		public T deserialize(Headers headers, String respBody) throws IOException;
	}

	/**
	 * 에러 응답 본문을 {@link RESTfulErrorEntity}로 변환하는 함수형 인터페이스.
	 */
	@FunctionalInterface
	public static interface ErrorEntityDeserializer {
		/**
		 * 에러 응답 본문을 deserialize한다.
		 *
		 * @param respBody 에러 응답 본문 문자열.
		 * @return 파싱된 에러 엔티티.
		 * @throws IOException 파싱 실패 시.
		 */
		public RESTfulErrorEntity deserialize(String respBody) throws IOException;
	}

	/**
	 * 응답 본문을 변환 없이 문자열 그대로 반환하는 deserializer.
	 */
	public static final ResponseBodyDeserializer<String> STRING_DESER = (headers, respBody) -> respBody;
	
	private Response call(Request req) throws RESTfulIOException {
		try {
			return m_client.newCall(req).execute();
		}
		catch ( SocketTimeoutException | ConnectException e ) {
			throw new RESTfulIOException("Failed to connect to the server: endpoint=" + req.url(), e);
		}
		catch ( InterruptedIOException e ) {
			Thread.currentThread().interrupt();
			throw new RESTfulIOException("RESTful call interrupted: endpoint=" + req.url(), e);
		}
		catch ( IOException e ) {
			throw new RESTfulIOException("Failed to call", e);
		}
	}

	private Headers getVoidResponse(Response resp) {
		try ( Response cresp = resp ) {
			Headers headers = cresp.headers();
			getLogger().debug("received void response: code={}, headers={}", cresp.code(), headers);

			if ( !cresp.isSuccessful() ) {
				throw toRESTfulClientException(cresp.body().string());
			}
			else {
				return headers;
			}
		}
		catch ( IOException e ) {
			throw new RESTfulIOException("Failed to read RESTful response", e);
		}
	}

	private <T> Tuple<T, Headers> parseResponse(Response resp, ResponseBodyDeserializer<T> deser) {
		try ( Response cresp = resp ) {
			String respBody = cresp.body().string();
			Headers headers = cresp.headers();
			getLogger().debug("received response: code={}, headers={}, body={}",
								cresp.code(), headers, respBody);

			if ( !cresp.isSuccessful() ) {
				throw toRESTfulClientException(respBody);
			}
			else if ( deser != null ) {
				try {
					return Tuple.of(deser.deserialize(headers, respBody), headers);
				}
				catch ( IOException e ) {
					throw new RESTfulIOException("Failed to parse RESTful response: response=" + respBody, e);
				}
			}
			else {
				return Tuple.of(null, headers);
			}
		}
		catch ( IOException e ) {
			throw new RESTfulIOException("Failed to read RESTful response", e);
		}
	}

	/**
	 * 응답 본문을 적절한 예외로 변환하여 던진다.
	 * <p>
	 * 우선 등록된 {@link ErrorEntityDeserializer}로 파싱을 시도하고,
	 * 실패 시 Spring 기본 에러 포맷, 마지막으로 일반 RESTful 서버 에러 메시지 포맷으로 fallback한다.
	 */
	protected RuntimeException toRESTfulClientException(String respBody) {
		Try<Throwable> cause
			= Try.get(() -> {
					try {
						return m_errorEntityDeser.deserialize(respBody).toException();
					}
					catch ( RESTfulRemoteException e ) {
						return e;
					}
				})
				.recover(() -> parseSpringException(respBody))
				.recover(() -> parseRESTfulServerErrorMessage(respBody));

		if ( cause.isFailed() ) {
			throw new RESTfulIOException("Failed to parse RESTful error response: response=" + respBody,
										cause.getCause());
		}
		throw Throwables.toRuntimeException(cause.get(),
									c -> new RESTfulRemoteException("Remote RESTful exception: " + c, c));
	}
		
	private Throwable parseSpringException(String respBody)
		throws JsonMappingException, JsonProcessingException {
		SpringExceptionEntity errorMsg = m_mapper.readValue(respBody, SpringExceptionEntity.class);
		return errorMsg.toException();
	}

	private Throwable parseRESTfulServerErrorMessage(String respBody)
		throws JsonMappingException, JsonProcessingException {
		RESTfulServerErrorMessage errorMsg = m_mapper.readValue(respBody, RESTfulServerErrorMessage.class);
		return new RESTfulIOException(errorMsg.toString());
	}
}
