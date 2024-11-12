package utils.http;

import java.io.IOException;

import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.http.HttpRESTfulClient.ErrorEntityDeserializer;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonErrorEntityDeserializer implements ErrorEntityDeserializer {
	private final JsonMapper m_mapper;
	
	public JacksonErrorEntityDeserializer(JsonMapper mapper) {
		m_mapper = mapper;
	}

	@Override
	public RESTfulErrorEntity deserialize(String respBody) throws IOException {
		return m_mapper.readValue(respBody, RESTfulErrorEntity.class);
	}
}
