package utils.json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonDeserializer {
	public static final String FIELD_TYPE = "@type";
	
	private ObjectMapper m_mapper;
	
	public JacksonDeserializer() {
		m_mapper = new ObjectMapper();
		m_mapper.registerModule(new JavaTimeModule());
		m_mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}
	
	public JacksonDeserializer(ObjectMapper mapper) {
		m_mapper = mapper;
	}
	
	public Object parseJsonNode(JsonNode jnode) throws JacksonDeserializationException {
		Class<?> jsonClass = null;
		String clsName = JacksonUtils.getStringFieldOrNull(jnode, FIELD_TYPE);
		try {
			if ( clsName == null ) {
				throw new JacksonDeserializationException(String.format("'%s' field is missingx: json=%s", FIELD_TYPE, jnode));
			}
			jsonClass = Class.forName(clsName);
		}
		catch ( ClassNotFoundException e ) {
			throw new JacksonDeserializationException("Cannot find Deserializer class: " + clsName);
		}

		try {
			Method parseJsonNode = jsonClass.getDeclaredMethod("parseJsonNode", JsonNode.class);
			if ( Modifier.isStatic(parseJsonNode.getModifiers()) ) {
				return parseJsonNode.invoke(null, jnode);
			}
		}
		catch ( InvocationTargetException e ) {
			throw new JacksonDeserializationException("Failed to parse JSON string: json=" + jnode, e.getCause());
		}
		catch ( NoSuchMethodException | SecurityException | IllegalAccessException e ) {
		}

		throw new JacksonDeserializationException("Cannot find parseJsonNode() method: class=" + clsName);
	}
	
	public Object parseJsonString(String jsonStr) throws JacksonDeserializationException {
		JsonNode jnode;
		try {
			jnode = m_mapper.readTree(jsonStr);
		}
		catch ( JsonProcessingException e ) {
			throw new JacksonDeserializationException("Failed to parse JSON string: json=" + jsonStr, e);
		}
		
		return parseJsonNode(jnode);
	}
}
