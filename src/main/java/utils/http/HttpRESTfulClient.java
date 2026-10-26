package utils.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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


/**
 * OkHttp 기반의 동기 RESTful 클라이언트.
 * <p>
 * {@link Builder}를 통해 생성하며, 등록된 default 헤더는 모든 요청에 자동으로 적용된다.
 * 응답 본문 파싱은 호출 시 전달하는 {@link ResponseBodyDeserializer}로 위임하고,
 * 에러 응답은 {@link ErrorEntityDeserializer}로 파싱하여 적절한 예외로 변환해 던진다.
 *
 * <h2>사용 예</h2>
 * <pre>{@code
 * HttpRESTfulClient client = HttpRESTfulClient.builder()
 *         .header("Authorization", "Bearer ...")
 *         .build();
 * String body = client.get("https://api.example.com/v1/items", HttpRESTfulClient.STRING_DESER);
 * }</pre>
 *
 * <h2>본문 없는 응답</h2>
 * HEAD 요청의 응답과 204(No Content)/205(Reset Content)/304(Not Modified)는 본문을 읽지 않는다.
 * 204/205는 성공이므로 본문 자리에 {@code null}이 반환되며 {@link ResponseBodyDeserializer}도
 * 호출되지 않는다. 304는 성공 응답이 아니므로 에러 처리 경로를 탄다.
 *
 * <h2>요청 본문의 미디어 타입</h2>
 * 요청 본문은 {@link RequestBody}로 전달하며, 미디어 타입은 본문을 만들 때 명시한다.
 * 문자열로부터 만들 때는 {@link #newJsonRequestBody(String)} /
 * {@link #newTextRequestBody(String)}를 쓰거나
 * {@link RequestBody#create(String, MediaType)}로 직접 지정한다.
 * <p>
 * 본문을 {@code text/plain}으로 조용히 전송하던 {@code post}/{@code put}의 {@code String}
 * 오버로드는 제거되었다. JSON을 넘겼을 때 서버가 415로 거부하는 함정이었다.
 *
 * <h2>예외</h2>
 * 모든 호출 메서드는 다음 unchecked 예외를 던질 수 있다.
 * <ul>
 *   <li>{@link RESTfulIOException} – 네트워크 오류, 응답 파싱 실패, 또는 알 수 없는 서버 에러</li>
 *   <li>{@link RESTfulRemoteException} – 서버가 구조화된 에러 엔티티를 반환한 경우</li>
 * </ul>
 * 두 예외 모두 공통 부모 {@link RESTfulException}을 가지며, {@link RESTfulIOException}의 메시지에는
 * {@code [<status> <url>]} 형태의 HTTP 컨텍스트가 앞에 붙는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class HttpRESTfulClient implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(HttpRESTfulClient.class);

	/** {@code text/plain; charset=utf-8} 미디어 타입 상수. */
	public static final MediaType MEDIA_TYPE_TEXT = MediaType.get("text/plain; charset=utf-8");
	/** {@code application/json; charset=utf-8} 미디어 타입 상수. */
	public static final MediaType MEDIA_TYPE_JSON = MediaType.get("application/json; charset=utf-8");

	/**
	 * 응답 본문을 변환 없이 문자열 그대로 반환하는 deserializer.
	 */
	public static final ResponseBodyDeserializer<String> STRING_DESER = (headers, respBody) -> respBody;

	/**
	 * 응답 본문을 {@link JsonNode}로 파싱하는 deserializer.
	 * <p>
	 * static 상수이므로 전용 {@link JsonMapper} 인스턴스를 사용한다. 즉
	 * {@link Builder#jsonMapper(JsonMapper)}로 지정한 매퍼는 <b>반영되지 않는다</b>.
	 * 클라이언트에 설정된 매퍼를 사용하려면 {@link #jsonDeserializer()}를 사용한다.
	 */
	public static final ResponseBodyDeserializer<JsonNode> JSON_DESER = newJsonDeserializer(new JsonMapper());

	private final OkHttpClient m_client;
	private final LinkedHashMap<String,String> m_headers;
	private final ErrorEntityDeserializer m_errorEntityDeser;
	private final JsonMapper m_mapper;
	// 에러 code → 클라이언트 측 예외 생성기. 등록된 code만 해당 예외로 복원한다 (Builder#remoteException).
	private final Map<String,Function<String,? extends RuntimeException>> m_remoteExceptions;
	private volatile Logger m_logger = null;
	
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
	
	/**
	 * 주어진 JSON 문자열을 {@link #MEDIA_TYPE_JSON}으로 전송하는 {@link RequestBody}를 생성한다.
	 * <p>
	 * JSON 문자열을 본문으로 하는 {@code post}/{@code put}/{@code patch} 호출의 표준 진입점이다.
	 * {@link #callAndGetHeaders(Request, ResponseBodyDeserializer)}에 넘길 {@link Request}를
	 * 직접 구성할 때도 사용한다.
	 *
	 * @param json 요청 본문 JSON 문자열.
	 * @return {@code application/json; charset=utf-8} 요청 본문.
	 */
	public static RequestBody newJsonRequestBody(String json) {
		return RequestBody.create(json, MEDIA_TYPE_JSON);
	}

	/**
	 * 주어진 문자열을 {@link #MEDIA_TYPE_TEXT}로 전송하는 {@link RequestBody}를 생성한다.
	 * <p>
	 * 서버가 {@code text/plain} 본문을 요구하는 경우에만 사용한다. JSON을 보낼 때 이 메소드를 쓰면
	 * {@code Content-Type}이 어긋나 서버가 415(Unsupported Media Type)로 거부할 수 있다.
	 *
	 * @param text 요청 본문 문자열.
	 * @return {@code text/plain; charset=utf-8} 요청 본문.
	 */
	public static RequestBody newTextRequestBody(String text) {
		return RequestBody.create(text, MEDIA_TYPE_TEXT);
	}

	private HttpRESTfulClient(OkHttpClient client, LinkedHashMap<String,String> headers,
								ErrorEntityDeserializer deser, JsonMapper mapper,
								Map<String,Function<String,? extends RuntimeException>> remoteExceptions) {
		Preconditions.checkNotNullArgument(client, "httpClient is null");
		Preconditions.checkNotNullArgument(deser, "deserializer is null");
		Preconditions.checkNotNullArgument(mapper, "jsonMapper is null");

		m_client = client;
		m_headers = Maps.newLinkedHashMap(headers);
		m_errorEntityDeser = deser;
		m_mapper = mapper;
		m_remoteExceptions = Map.copyOf(remoteExceptions);
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
	 * 이 클라이언트에 설정된 {@link JsonMapper}로 응답 본문을 {@link JsonNode}로 파싱하는
	 * deserializer를 반환한다.
	 * <p>
	 * {@link #JSON_DESER}는 static 상수라 {@link Builder#jsonMapper(JsonMapper)}로 지정한 매퍼를
	 * 사용할 수 없다. 커스텀 매퍼 설정(모듈 등록, 역직렬화 기능 조정 등)을 응답 파싱에도 반영하려면
	 * 이 메소드가 반환하는 deserializer를 사용한다.
	 *
	 * @return JSON deserializer.
	 */
	public ResponseBodyDeserializer<JsonNode> jsonDeserializer() {
		return newJsonDeserializer(m_mapper);
	}

	private static ResponseBodyDeserializer<JsonNode> newJsonDeserializer(JsonMapper mapper) {
		return (headers, respBody) -> {
			try {
				return mapper.readTree(respBody);
			}
			catch ( JsonProcessingException e ) {
				throw new IOException("Failed to parse JSON response: response=" + respBody, e);
			}
		};
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
		Response resp = call(req);
		return parseResponse(resp, deser)._2;
	}

	/**
	 * 주어진 URL에 GET 요청을 보내고 응답 문자열을 반환한다.
	 *
	 * @param url 요청 URL.
	 * @return 응답 본문 문자열 (없으면 null).
	 */
	public String get(String url) {
		getLogger().debug("sending: (GET) {}", url);
		Request req = newRequestBuilder(url).get().build();
		Response resp = call(req);
		return parseResponse(resp)._2;
	}

	/**
	 * 주어진 {@link HttpUrl}에 GET 요청을 보내고 응답 문자열을 반환한다.
	 *
	 * @param url 요청 URL.
	 * @return 응답 본문 문자열 (없으면 null).
	 */
	public String get(HttpUrl url) {
		getLogger().debug("sending: (GET) {}", url);
		Request req = newRequestBuilder(url).get().build();
		Response resp = call(req);
		return parseResponse(resp)._2;
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
		Response resp = call(req);
		return parseResponse(resp, deser)._2;
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
	public <T> Tuple<Headers, T> callAndGetHeaders(Request req, ResponseBodyDeserializer<T> deser) {
		getLogger().debug("sending: ({}) {}, body={}", req.method(), req.url(), describeBody(req.body()));
		Response resp = call(applyDefaultHeaders(req));
		return parseResponse(resp, deser);
	}

	/**
	 * 주어진 URL에 POST 요청을 보내고 응답 문자열을 반환한다.
	 *
	 * @param url     요청 URL.
	 * @param reqBody 요청 본문.
	 * @return 응답 본문 문자열 (없으면 null).
	 */
	public String post(String url, RequestBody reqBody) {
		getLogger().debug("sending: (POST) {}, body={}", url, describeBody(reqBody));
		Request req = newRequestBuilder(url).post(reqBody).build();
		Response resp = call(req);
		return parseResponse(resp)._2;
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
		getLogger().debug("sending: (POST) {}, body={}", url, describeBody(reqBody));
		Request req = newRequestBuilder(url).post(reqBody).build();
		Response resp = call(req);
		return parseResponse(resp, deser)._2;
	}

	/**
	 * 주어진 URL에 PUT 요청을 보내고 응답 문자열을 반환한다.
	 *
	 * @param url     요청 URL.
	 * @param reqBody 요청 본문.
	 * @return 응답 본문 문자열 (없으면 null).
	 */
	public String put(String url, RequestBody reqBody) {
		getLogger().debug("sending: (PUT) {}, body={}", url, describeBody(reqBody));
		Request req = newRequestBuilder(url).put(reqBody).build();
		Response resp = call(req);
		return parseResponse(resp)._2;
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
		getLogger().debug("sending: (PUT) {}, body={}", url, describeBody(reqBody));
		Request req = newRequestBuilder(url).put(reqBody).build();
		Response resp = call(req);
		return parseResponse(resp, deser)._2;
	}
	

	/**
	 * 주어진 URL에 PATCH 요청을 보내고 응답 문자열을 반환한다.
	 *
	 * @param url     요청 URL.
	 * @param reqBody 요청 본문.
	 * @return 응답 본문 문자열 (없으면 null).
	 */
	public String patch(String url, RequestBody reqBody) {
		getLogger().debug("sending: (PATCH) {}, body={}", url, describeBody(reqBody));
		Request req = newRequestBuilder(url).patch(reqBody).build();
		Response resp = call(req);
		return parseResponse(resp)._2;
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
		getLogger().debug("sending: (PATCH) {}, body={}", url, describeBody(reqBody));
		Request req = newRequestBuilder(url).patch(reqBody).build();
		Response resp = call(req);
		return parseResponse(resp, deser)._2;
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
		Response resp = call(req);
		return parseResponse(resp)._1;
	}

	/**
	 * 주어진 URL에 DELETE 요청을 보내고 응답 문자열을 반환한다.
	 *
	 * @param url 요청 URL.
	 * @return 응답 본문 문자열 (없으면 null).
	 */
	public String delete(String url) {
		getLogger().debug("sending: (DELETE) {}", url);
		Request req = newRequestBuilder(url).delete().build();
		Response resp = call(req);
		return parseResponse(resp)._2;
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
		Response resp = call(req);
		return parseResponse(resp, deser)._2;
	}

	// String 오버로드를 HttpUrl 오버로드로 위임하지 않는 이유:
	// Request.Builder.url(String)은 ws://, wss:// 를 http://, https:// 로 바꿔주지만
	// HttpUrl.get(String)은 그대로 거부한다. 두 진입점의 의미가 다르므로 각각 만든다.
	private Request.Builder newRequestBuilder(String url) {
		return addDefaultHeaders(new Request.Builder().url(url));
	}

	private Request.Builder newRequestBuilder(HttpUrl url) {
		return addDefaultHeaders(new Request.Builder().url(url));
	}

	private Request.Builder addDefaultHeaders(Request.Builder builder) {
		m_headers.forEach(builder::addHeader);
		return builder;
	}

	/**
	 * 로그에 남길 요청 본문 요약을 만든다.
	 * <p>
	 * {@link RequestBody}는 {@code toString()}을 재정의하지 않아 그대로 찍으면
	 * {@code okhttp3.RequestBody$Companion$toRequestBody$2@5e8c92f4} 같은 문자열이 남는다.
	 * 대신 미디어 타입과 길이를 남긴다.
	 */
	private static String describeBody(RequestBody reqBody) {
		if ( reqBody == null ) {
			return "none";
		}

		try {
			return String.format("%s(%d bytes)", reqBody.contentType(), reqBody.contentLength());
		}
		catch ( IOException e ) {
			return String.valueOf(reqBody.contentType());
		}
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
		private final Map<String,Function<String,? extends RuntimeException>> m_remoteExceptions
																		= new LinkedHashMap<>();

		private Builder() { }

		/**
		 * 현재 설정으로 새 클라이언트를 생성한다.
		 *
		 * @return 새 {@link HttpRESTfulClient} 인스턴스.
		 */
		public HttpRESTfulClient build() {
			// getOrElse(T, T)는 두 번째 인자를 즉시 평가하므로, 커스텀 클라이언트가 지정된 경우에도
			// 버려질 OkHttpClient가 매번 하나씩 생성된다. Supplier 오버로드로 지연 평가한다.
			OkHttpClient client = Optionals.getOrElse(m_httpClient, OkHttpClientUtils::newClient);
			JsonMapper mapper = (m_mapper != null) ? m_mapper : new JsonMapper();
			ErrorEntityDeserializer deser = (m_deser != null)
											? m_deser
											: new JacksonErrorEntityDeserializer(mapper);
			return new HttpRESTfulClient(client, m_headers, deser, mapper, m_remoteExceptions);
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

		/**
		 * 서버가 보낸 에러 {@code code}를 클라이언트 측 예외로 복원하는 규칙을 등록한다.
		 * <p>
		 * 에러 응답의 {@code code}(일반적으로 서버 측 예외 클래스 이름)가 {@code code}와 같으면
		 * {@link RESTfulRemoteException} 대신 {@code factory}가 만든 예외를 던진다. {@code factory}는
		 * 에러 메시지({@link RESTfulErrorEntity}의 {@code message} 또는 {@link TypedServerErrorMessage}의
		 * {@code text}. 없으면 {@code null})를 받는다.
		 * <p>
		 * 원격 응답이 지정한 임의 클래스를 로딩하지 않으면서, 호출부가 서버 예외를 타입으로 구분할 수 있게
		 * 하는 허용 목록(allowlist)이다. 등록하지 않은 {@code code}는 기존처럼 {@link RESTfulRemoteException}으로
		 * 전달된다. {@code factory}가 실패하거나 {@code null}을 반환해도 기존 처리로 돌아간다.
		 * 같은 {@code code}로 다시 호출하면 덮어쓴다.
		 *
		 * @param code		에러 code. (예: {@code "mdt.model.ResourceNotFoundException"})
		 * @param factory	에러 메시지로부터 예외를 만드는 함수.
		 * @return 이 빌더.
		 */
		public Builder remoteException(String code, Function<String,? extends RuntimeException> factory) {
			Preconditions.checkNotNullArgument(code, "code is null");
			Preconditions.checkNotNullArgument(factory, "factory is null");

			m_remoteExceptions.put(code, factory);
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

	private Response call(Request req) throws RESTfulIOException {
		// 주의: catch 절의 순서가 의미를 갖는다.
		//   SocketTimeoutException extends InterruptedIOException 이므로 순서를 바꾸면
		//   connect/read/write 타임아웃이 아래 InterruptedIOException 절로 흡수된다.
		try {
			return m_client.newCall(req).execute();
		}
		catch ( ConnectException e ) {
			throw new RESTfulIOException("Failed to connect to the server: endpoint=" + req.url(), e);
		}
		catch ( SocketTimeoutException e ) {
			// connect/read/write 타임아웃을 모두 포함한다. 연결 실패로 단정하지 않는다.
			throw new RESTfulIOException("RESTful call timed out: endpoint=" + req.url(), e);
		}
		catch ( InterruptedIOException e ) {
			// OkHttp는 call timeout이 만료된 경우에도 InterruptedIOException("timeout")을 던지기 때문에
			// (RealCall.timeoutExit 참조), 무조건 인터럽트 상태를 복원하면 단순 타임아웃을 인터럽트로
			// 오인하여 호출 스레드에 인터럽트 플래그를 잘못 남기게 된다.
			// OkHttp는 blocking socket I/O 중의 Thread.interrupt()에 반응하지 않으므로, 실제로
			// 인터럽트된 스레드는 여기 도달했을 때에도 인터럽트 상태가 유지되어 있다.
			if ( Thread.currentThread().isInterrupted() ) {
				throw new RESTfulIOException("RESTful call interrupted: endpoint=" + req.url(), e);
			}
			else {
				throw new RESTfulIOException("RESTful call timed out: endpoint=" + req.url(), e);
			}
		}
		catch ( IOException e ) {
			throw new RESTfulIOException("Failed to call", e);
		}
	}

	private Tuple<Headers, String> parseResponse(Response resp) {
		try ( Response cresp = resp ) {
			Headers headers = cresp.headers();

			// 본문을 가질 수 없는 응답은 읽지 않고 본문을 null로 처리한다.
			String respBody = hasNoBody(cresp) ? null : cresp.body().string();
			getLogger().debug("received response: code={}, headers={}, body={}",
								cresp.code(), headers, respBody);

			if ( !cresp.isSuccessful() ) {
				RuntimeException error = toRESTfulClientException(cresp.code(), cresp.request().url(), respBody);
				// 프로토콜상 상태 코드 자체가 의미를 갖는 경우(예: 409 Conflict)를 호출부가 구분할 수 있도록
				// 예외에 상태 코드를 실어 보낸다. toRESTfulClientException()은 항상 RESTfulException을
				// 반환하지만, 재정의된 구현이 다른 예외를 반환하더라도 안전하도록 타입을 확인한다.
				if ( error instanceof RESTfulException rex ) {
					rex.setHttpStatusCode(cresp.code());
				}
				throw error;
			}
			else {
				return Tuple.of(headers, respBody);
			}
		}
		catch ( IOException e ) {
			throw new RESTfulIOException("Failed to read RESTful response", e);
		}
	}

	/**
	 * 정의상 본문을 가질 수 없는 응답인지 여부를 반환한다.
	 * <p>
	 * HEAD 요청의 응답과 204(No Content)/205(Reset Content)/304(Not Modified) 응답이 해당한다.
	 * 304는 성공 응답이 아니므로 여전히 에러로 처리되지만, 읽을 본문이 없다는 점은 동일하다.
	 */
	private static boolean hasNoBody(Response resp) {
		if ( "HEAD".equals(resp.request().method()) ) {
			return true;
		}

		int code = resp.code();
		return code == 204 || code == 205 || code == 304;
	}

	private <T> Tuple<Headers,T> parseResponse(Response resp, ResponseBodyDeserializer<T> deser) {
		Preconditions.checkNotNullArgument(deser, "ResponseBodyDeserializer is null");

		// parseResponse(resp)가 응답을 닫으므로 진단 정보는 미리 확보해 둔다.
		int statusCode = resp.code();
		HttpUrl url = resp.request().url();

		var result = parseResponse(resp);
		if ( result._2 == null ) {
			// 본문을 가질 수 없는 응답(204/205, HEAD)은 deserializer를 호출하지 않는다.
			return Tuple.of(result._1, null);
		}

		try {
			return Tuple.of(result._1, deser.deserialize(result._1, result._2));
		}
		catch ( RESTfulException e ) {
			// deserializer가 이미 이 계층의 예외를 던졌다면 두 번 감싸지 않는다.
			throw e;
		}
		catch ( IOException | RuntimeException e ) {
			// deserializer가 던지는 unchecked 예외(커스텀 람다의 IllegalArgumentException 등)도
			// RESTfulException 계층 안에 가둔다.
			throw new RESTfulIOException(withHttpContext(statusCode, url,
										"Failed to parse RESTful response: response=" + result._2), e);
		}
	}

	/**
	 * 에러 응답을 적절한 예외로 변환하여 반환한다.
	 * <p>
	 * 본문을 한 번만 {@link JsonNode}로 파싱한 뒤, 존재하는 필드로 에러 포맷을 명시적으로 판별한다
	 * (파싱 실패에 의존하던 fallback 체인을 대체).
	 * <ul>
	 *   <li>{@code messageType} 보유 → {@link TypedServerErrorMessage} (커스텀 포맷)</li>
	 *   <li>{@code status} + {@code path} 보유 → {@link SpringBootErrorResponse} (Spring Boot 기본 에러)</li>
	 *   <li>{@code code} 또는 {@code message} 보유 → {@link RESTfulErrorEntity} (주입된 deserializer로 위임)</li>
	 *   <li>본문이 비었거나 그 외 → 본문을 담은 {@link RESTfulIOException}</li>
	 * </ul>
	 * 서버가 구조화된 에러를 반환한 경우({@link RESTfulRemoteException})는 서버가 준 메시지를 그대로
	 * 사용하고, 그 외 {@link RESTfulIOException}에는 {@code [<status> <url>]} 형태의 HTTP 컨텍스트를
	 * 앞에 붙인다. 본문이 없는 404/500 같은 응답도 상태 코드만으로 진단할 수 있게 하기 위함이다.
	 * <p>
	 * {@code code}가 가리키는 클래스를 로딩하여 원래 예외 타입을 복원하는
	 * {@link RESTfulErrorEntity#toClientException()}은 <b>사용하지 않는다</b>. 원격 응답이 지정한
	 * 임의 클래스를 로딩하지 않기 위함이며, 대신 {@link RESTfulErrorEntity#toRemoteException()}으로
	 * {@code code}/{@code message}를 보유한 {@link RESTfulRemoteException}을 반환한다.
	 *
	 * <p>
	 * 이 메소드는 <b>어떤 경우에도 예외를 던지지 않고 반환</b>한다. 본문이 어떤 형태로 망가져 있든
	 * 결과는 {@link RESTfulException}의 하위 타입이다.
	 *
	 * @param statusCode	응답 HTTP 상태 코드.
	 * @param url			요청 URL. 알 수 없는 경우는 {@code null}.
	 * @param respBody		에러 응답 본문.
	 * @return	변환된 unchecked 예외. 호출자는 이 반환값을 {@code throw}한다.
	 */
	protected RuntimeException toRESTfulClientException(int statusCode, HttpUrl url, String respBody) {
		if ( respBody == null || respBody.isBlank() ) {
			// 본문이 없는 에러 응답 (예: 본문 없는 404, HEAD 요청의 에러 응답).
			// 상태 코드만이 유일한 단서이다.
			return new RESTfulIOException(withHttpContext(statusCode, url, "Empty RESTful error response"));
		}

		JsonNode root;
		try {
			root = m_mapper.readTree(respBody);
		}
		catch ( JsonProcessingException e ) {
			// JSON이 아니면 본문 자체를 메시지로 담아 반환한다.
			return new RESTfulIOException(withHttpContext(statusCode, url,
										"Failed to parse RESTful error response: response=" + respBody), e);
		}

		try {
			if ( root == null || root.isMissingNode() ) {
				// 위의 isBlank() 검사를 통과했더라도 파싱 결과가 비어 있을 수 있다.
				// 예를 들어 ALLOW_JAVA_COMMENTS를 켠 매퍼에 주석뿐인 본문이 오면 MissingNode가 된다.
				return new RESTfulIOException(withHttpContext(statusCode, url, "Empty RESTful error response"));
			}
			if ( root.path("error").isObject() ) {
				// 에러 본문을 {"error": {...}}로 한 겹 감싸 보내는 서버를 위해 벗겨낸다.
				// 반드시 object인 경우에만 벗겨야 한다. Spring Boot 기본 포맷의 "error"는
				// reason phrase 문자열("Internal Server Error")이어서, 무조건 벗기면 root가
				// TextNode가 되어 아래 분기가 모두 빗나가고 "Unrecognized"로 떨어진다.
				root = root.get("error");
			}

			if ( root.has("messageType") ) {
				// 커스텀 포맷: TypedServerErrorMessage {messageType, code, text, timestamp}
				TypedServerErrorMessage errorMsg = m_mapper.convertValue(root, TypedServerErrorMessage.class);
				RuntimeException known = toRegisteredException(errorMsg.getCode(), errorMsg.getText());
				return (known != null) ? known : toRemoteException(errorMsg.toException());
			}
			else if ( root.has("status") && root.has("path") ) {
				// Spring Boot 기본 에러 포맷: {timestamp, status, error, message, path}
				SpringBootErrorResponse errorMsg = m_mapper.convertValue(root, SpringBootErrorResponse.class);
				return new RESTfulIOException(withHttpContext(statusCode, url, errorMsg.toString()));
			}
			else if ( root.hasNonNull("code") || root.hasNonNull("message") ) {
				// 우리 포맷: RESTfulErrorEntity {code, message} (주입된 deserializer로 위임)
				try {
					String errorMsg = m_mapper.writeValueAsString(root);
					RESTfulErrorEntity error = m_errorEntityDeser.deserialize(errorMsg);
					RuntimeException known = toRegisteredException(error.getCode(), error.getMessage());
					return (known != null) ? known : error.toRemoteException();
				}
				catch ( IOException e ) {
					return new RESTfulIOException(withHttpContext(statusCode, url,
											"Failed to parse RESTful error response: response=" + respBody), e);
				}
			}
			else {
				return new RESTfulIOException(withHttpContext(statusCode, url,
											"Unrecognized RESTful error response: response=" + respBody));
			}
		}
		catch ( IllegalArgumentException e ) {
			// JsonMapper.convertValue()는 변환에 실패하면 unchecked IllegalArgumentException을 던진다.
			// (messageType의 enum 값 불일치, SpringBootErrorResponse.status의 타입 불일치 등)
			// 여기서 걸러내지 않으면 "예외를 만들어 반환한다"는 이 메소드의 계약이 깨지고,
			// RESTfulException 계층 밖의 예외가 호출자에게 그대로 전파된다.
			return new RESTfulIOException(withHttpContext(statusCode, url,
										"Failed to parse RESTful error response: response=" + respBody), e);
		}
	}

	/**
	 * 에러 메시지 앞에 {@code [<status> <url>]} 형태의 HTTP 컨텍스트를 붙인다.
	 */
	private static String withHttpContext(int statusCode, HttpUrl url, String details) {
		return ( url != null )
				? String.format("[%d %s] %s", statusCode, url, details)
				: String.format("[%d] %s", statusCode, details);
	}

	/**
	 * 복원된 원인 예외를 클라이언트 측 unchecked 예외로 감싼다.
	 * 이미 unchecked이면 그대로, checked이면 {@link RESTfulRemoteException}으로 래핑한다.
	 */
	private RuntimeException toRegisteredException(String code, String message) {
		// Builder#remoteException으로 등록된 code만 복원한다. 규칙이 없거나 예외 생성이 실패하면
		// null을 반환하여 기본 처리(RESTfulRemoteException)를 따르게 한다 — 이 경로도 예외를 던지면 안 된다.
		Function<String,? extends RuntimeException> factory = (code != null) ? m_remoteExceptions.get(code) : null;
		if ( factory == null ) {
			return null;
		}
		try {
			return factory.apply(message);
		}
		catch ( RuntimeException e ) {
			getLogger().warn("failed to create the registered exception: code={}, cause={}", code, e.toString());
			return null;
		}
	}

	private RuntimeException toRemoteException(Throwable cause) {
		return Throwables.toRuntimeException(cause,
									c -> new RESTfulRemoteException("Remote RESTful exception: " + c, c));
	}
}
