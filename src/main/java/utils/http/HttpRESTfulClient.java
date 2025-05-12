package utils.http;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Preconditions;

import utils.LoggerSettable;
import utils.Throwables;
import utils.Tuple;
import utils.func.FOption;
import utils.func.Try;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class HttpRESTfulClient implements LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(HttpRESTfulClient.class);
	public static final MediaType MEDIA_TYPE_TEXT = MediaType.parse("text/plain; charset=utf-8");
	public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
	
	private final OkHttpClient m_client;
	private final ErrorEntityDeserializer m_errorEntityDeser;
	private final JsonMapper m_mapper;
	private Logger m_logger = null;
	
	public static HttpRESTfulClient newDefaultClient() {
		return builder().build();
	}
	
	private HttpRESTfulClient(Builder builder) {
		Preconditions.checkNotNull(builder);
		Preconditions.checkNotNull(builder.m_httpClient);
		Preconditions.checkNotNull(builder.m_deser);
		
		m_client = builder.m_httpClient;
		m_errorEntityDeser = builder.m_deser;
		m_mapper = builder.m_mapper;
	}
	
	public OkHttpClient getHttpClient() {
		return m_client;
	}

	public <T> T get(String url, ResponseBodyDeserializer<T> deser) {
		if ( getLogger().isDebugEnabled() ) {
			getLogger().debug("sending: (GET) {}", url);
		}
		
		Request req = new Request.Builder().url(url).get().build();
		return call(req, deser);
	}
	public void get(String url) {
		if ( getLogger().isDebugEnabled() ) {
			getLogger().debug("sending: (GET) {}", url);
		}
		
		Request req = new Request.Builder().url(url).get().build();
		callVoid(req);
	}
	public <T> T get(HttpUrl url, ResponseBodyDeserializer<T> deser) {
		if ( getLogger().isDebugEnabled() ) {
			getLogger().debug("sending: (GET) {}", url);
		}
		
		Request req = new Request.Builder().url(url).get().build();
		return call(req, deser);
	}
	
	public <T> T post(String url, RequestBody reqBody, ResponseBodyDeserializer<T> deser) {
		if ( getLogger().isDebugEnabled() ) {
			getLogger().debug("sending: (POST) {}, body={}", url, reqBody);
		}
		
		Request req = new Request.Builder().url(url).post(reqBody).build();
		return call(req, deser);
	}
	
	public <T> T put(String url, RequestBody reqBody, ResponseBodyDeserializer<T> deser) {
		if ( getLogger().isDebugEnabled() ) {
			getLogger().debug("sending: (PUT) {}, body={}", url, reqBody);
		}
		
		Request req = new Request.Builder().url(url).put(reqBody).build();
		return call(req, deser);
	}
	public void put(String url, RequestBody reqBody) {
		if ( getLogger().isDebugEnabled() ) {
			getLogger().debug("sending: (PUT) {}, body={}", url, reqBody);
		}
		
		Request req = new Request.Builder().url(url).put(reqBody).build();
		callVoid(req);
	}
	
	public void delete(String url) {
		if ( getLogger().isDebugEnabled() ) {
			getLogger().debug("sending: (DELETE) {}", url);
		}
		Request req = new Request.Builder().url(url).delete().build();
		callVoid(req);
	}
	
	public <T> T delete(String url, ResponseBodyDeserializer<T> deser) {
		if ( getLogger().isDebugEnabled() ) {
			getLogger().debug("sending: (DELETE) {}", url);
		}
		
		Request req = new Request.Builder().url(url).delete().build();
		return call(req, deser);
	}
	
	public <T> Tuple<T, Headers> callAndGetHeaders(Request req, ResponseBodyDeserializer<T> deser)
		throws RESTfulIOException, RESTfulRemoteException {
		Response resp = call(req);
		return parseResponse(resp, deser);
	}

	private <T> T call(Request req, ResponseBodyDeserializer<T> deser) throws RESTfulIOException, RESTfulRemoteException {
		Response resp = call(req);
		return parseResponse(resp, deser)._1;
	}
	
	private void callVoid(Request req) throws RESTfulIOException, RESTfulRemoteException {
		Response resp = call(req);
		getVoidResponse(resp);
	}

	@Override
	public Logger getLogger() {
		return FOption.getOrElse(m_logger, s_logger);
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}

	public static Builder builder() {
		return new Builder();
	}
	public static class Builder {
		private OkHttpClient m_httpClient;
		private ErrorEntityDeserializer m_deser;
		private JsonMapper m_mapper;
		
		private Builder() { }
		
		public HttpRESTfulClient build() {
			m_httpClient = FOption.getOrElse(m_httpClient, OkHttpClientUtils.newClient());
			if ( m_mapper == null ) {
				m_mapper = new JsonMapper();
			}
			if ( m_deser == null ) {
				m_deser = new JacksonErrorEntityDeserializer(m_mapper);
			}
			
			return new HttpRESTfulClient(this);
		}
		
		public Builder httpClient(OkHttpClient client) {
			m_httpClient = client;
			return this;
		}
		
		public Builder errorEntityDeserializer(ErrorEntityDeserializer deser) {
			m_deser = deser;
			return this;
		}
		
		public Builder jsonMapper(JsonMapper mapper) {
			m_mapper = mapper;
			return this;
		}
	}
	
	@FunctionalInterface
	public static interface ResponseBodyDeserializer<T> {
		public T deserialize(Headers headers, String respBody) throws IOException;
	}
	
	@FunctionalInterface
	public static interface ErrorEntityDeserializer {
		public RESTfulErrorEntity deserialize(String respBody) throws IOException;
	}
	
	public static ResponseBodyDeserializer<String> STRING_DESER = new ResponseBodyDeserializer<>() {
		@Override
		public String deserialize(Headers headers, String respBody) throws IOException {
			return respBody;
		}
	};
	
	private Response call(Request req) throws RESTfulIOException {
		try {
			return m_client.newCall(req).execute();
		}
		catch ( IOException e ) {
			throw new RESTfulIOException("Failed to call", e);
		}
	}

	private Headers getVoidResponse(Response resp) {
		try ( Response cresp = resp ) {
			Headers headers = cresp.headers();
			if ( s_logger.isDebugEnabled() ) {
				s_logger.debug("received void response: code={}, headers={}", cresp.code(), headers);
			}
			
			if ( !cresp.isSuccessful() ) {
				throwException(resp.body().string());
				throw new AssertionError();
			}
			else {
				return headers;
			}
		}
		catch ( IOException e ) {
			throw new RESTfulIOException("Failed to read RESTful response, cause=" + e);
		}
	}
	
	private <T> Tuple<T, Headers> parseResponse(Response resp, ResponseBodyDeserializer<T> deser)
		throws RESTfulIOException, RESTfulRemoteException {
		try ( Response cresp = resp ) {
			String respBody = resp.body().string();
			Headers headers = cresp.headers();
			if ( s_logger.isDebugEnabled() ) {
				s_logger.debug("received response: code={}, headers={}, body={}", cresp.code(), headers, respBody);
			}
			
			if ( !cresp.isSuccessful() ) {
				throwException(respBody);
				throw new AssertionError();
			}
			else if ( deser != null ) {
				try {
					return Tuple.of(deser.deserialize(headers, respBody), headers);
				}
				catch ( IOException e ) {
					throw new RESTfulIOException("Failed to parse RESTful response: cause=" + e
														+ ", response=" + respBody);
				}
			}
			else {
				return Tuple.of(null, headers);
			}
		}
		catch ( IOException e ) {
			throw new RESTfulIOException("Failed to read RESTful response, cause=" + e);
		}
	}
	
	private Throwable parseSpringException(String respBody)
		throws JsonMappingException, JsonProcessingException {
		// Web 서버에 자체에서 오류가 발생하여 web 서버가 보내는 오류 메시지의 경우는 따로 처리해야 함.
		SpringExceptionEntity errorMsg = m_mapper.readValue(respBody, SpringExceptionEntity.class);
		return errorMsg.toException();
	}
	
	private Throwable parseRESTfulServerErrorMessage(String respBody)
		throws JsonMappingException, JsonProcessingException {
		// Web 서버에 자체에서 오류가 발생하여 web 서버가 보내는 오류 메시지의 경우는 따로 처리해야 함.
		RESTfulServerErrorMessage errorMsg = m_mapper.readValue(respBody, RESTfulServerErrorMessage.class);
		return new RESTfulIOException(errorMsg.toString());
	}

	protected void throwException(String respBody) {
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
		
		Throwables.sneakyThrow(cause.get());
	}
}
