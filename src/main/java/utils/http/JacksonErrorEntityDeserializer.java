package utils.http;

import java.io.IOException;

import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.http.HttpRESTfulClient.ErrorEntityDeserializer;

/**
 * 에러 응답 본문을 Jackson으로 {@link RESTfulErrorEntity}로 역직렬화하는 {@link ErrorEntityDeserializer} 구현.
 * <p>
 * {@link HttpRESTfulClient}의 기본 에러 deserializer로 사용된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonErrorEntityDeserializer implements ErrorEntityDeserializer {
	private final JsonMapper m_mapper;

	/**
	 * 주어진 {@link JsonMapper}로 동작하는 deserializer를 생성한다.
	 *
	 * @param mapper 역직렬화에 사용할 JSON 매퍼.
	 */
	public JacksonErrorEntityDeserializer(JsonMapper mapper) {
		m_mapper = mapper;
	}

	@Override
	public RESTfulErrorEntity deserialize(String respBody) throws IOException {
		return m_mapper.readValue(respBody, RESTfulErrorEntity.class);
	}
}
