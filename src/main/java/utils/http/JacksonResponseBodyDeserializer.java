package utils.http;

import java.io.IOException;

import com.fasterxml.jackson.databind.json.JsonMapper;

import okhttp3.Headers;

import utils.http.HttpRESTfulClient.ResponseBodyDeserializer;


/**
 * 응답 본문을 Jackson으로 지정한 타입 {@code T}로 역직렬화하는 {@link ResponseBodyDeserializer} 구현.
 *
 * @param <T> 역직렬화 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonResponseBodyDeserializer<T> implements ResponseBodyDeserializer<T> {
	private final JsonMapper m_mapper;
	private final Class<T> m_type;

	/**
	 * 주어진 {@link JsonMapper}와 대상 타입으로 동작하는 deserializer를 생성한다.
	 *
	 * @param mapper 역직렬화에 사용할 JSON 매퍼.
	 * @param cls    역직렬화 대상 타입.
	 */
	public JacksonResponseBodyDeserializer(JsonMapper mapper, Class<T> cls) {
		m_mapper = mapper;
		m_type = cls;
	}

	@Override
	public T deserialize(Headers headers, String respBody) throws IOException {
		return m_mapper.readValue(respBody, m_type);
	}
}
