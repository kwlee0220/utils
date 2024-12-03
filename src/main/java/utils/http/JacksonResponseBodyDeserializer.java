package utils.http;

import java.io.IOException;

import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.http.HttpRESTfulClient.ResponseBodyDeserializer;

import okhttp3.Headers;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonResponseBodyDeserializer<T> implements ResponseBodyDeserializer<T> {
	private final JsonMapper m_mapper;
	private final Class<T> m_type;
	
	public JacksonResponseBodyDeserializer(JsonMapper mapper, Class<T> cls) {
		m_mapper = mapper;
		m_type = cls;
	}
	
	@Override
	public T deserialize(Headers headers, String respBody) throws IOException {
		return m_mapper.readValue(respBody, m_type);
	}
}
