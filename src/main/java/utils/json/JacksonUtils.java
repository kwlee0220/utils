package utils.json;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonUtils {
	private JacksonUtils() {
		throw new AssertionError("Should not be invoked!!: class=" + JacksonUtils.class.getName());
	}

	public static JsonNode getNullableField(JsonNode node, String fieldName) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return null;
		}
		else {
			return field;
		}
	}

	public static String getStringField(JsonNode node, String fieldName) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			throw new IllegalStateException("Invalid field value: field=" + fieldName);
		}
		else {
			return field.asText();
		}
	}

	public static String getStringFieldOrNull(JsonNode node, String fieldName) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return null;
		}
		else {
			return field.asText();
		}
	}

	public static Boolean getBooleanField(JsonNode node, String fieldName, Boolean defaultValue) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return defaultValue;
		}
		else {
			return field.asBoolean();
		}
	}

	public static Iterator<JsonNode> getArrayFieldOrNull(JsonNode node, String fieldName) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return null;
		}
		else {
			return field.elements();
		}
	}

	public static Iterator<Map.Entry<String, JsonNode>> getFieldsOrNull(JsonNode node, String fieldName) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return null;
		}
		else {
			return field.fields();
		}
	}
}
