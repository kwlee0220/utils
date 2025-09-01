package utils.json;

import java.util.Iterator;
import java.util.Map;

import org.checkerframework.checker.nullness.qual.NonNull;

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

	public static JsonNode getFieldOrNull(@NonNull JsonNode node, String fieldName) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return null;
		}
		else {
			return field;
		}
	}
	public static JsonNode getField(@NonNull JsonNode node, String fieldName) {
		JsonNode field = getFieldOrNull(node, fieldName);
		if ( field == null ) {
			throw new IllegalStateException("Invalid (empty) field value: field=" + fieldName);
		}
		return field;
	}

	public static String getStringFieldOrDefault(@NonNull JsonNode node, String fieldName, String defaultValue) {
		JsonNode field = getFieldOrNull(node, fieldName);
		return (field != null) ? field.asText() : defaultValue;
	}
	public static String getStringFieldOrNull(@NonNull JsonNode node, String fieldName) {
		return getStringFieldOrDefault(node, fieldName, null);
	}
	public static String getStringField(@NonNull JsonNode node, String fieldName) {
		return getField(node, fieldName).asText();
	}

	public static Boolean getBooleanField(@NonNull JsonNode node, String fieldName, Boolean defaultValue) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return defaultValue;
		}
		else {
			return field.asBoolean();
		}
	}

	public static int getIntField(@NonNull JsonNode node, String fieldName, int defaultValue) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return defaultValue;
		}
		else {
			return field.asInt();
		}
	}

	public static Iterator<JsonNode> getArrayFieldOrNull(@NonNull JsonNode node, String fieldName) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return null;
		}
		else {
			return field.elements();
		}
	}

	public static Iterator<Map.Entry<String, JsonNode>> getFieldsOrNull(@NonNull JsonNode node, String fieldName) {
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
