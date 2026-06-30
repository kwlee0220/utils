package utils.json;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Preconditions;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonUtils {
	public static final JsonMapper MAPPER = JsonMapper.builder()
														.addModule(new JavaTimeModule())
														.findAndAddModules()
														.build();
	private JacksonUtils() {
		throw new AssertionError("Should not be invoked!!: class=" + JacksonUtils.class.getName());
	}
	
	public static final JsonMapper newJsonMapper(boolean prettyPrint) {
		JsonMapper.Builder builder = JsonMapper.builder()
												.addModule(new JavaTimeModule())
												.findAndAddModules();
		if ( prettyPrint ) {
			builder = builder.enable(SerializationFeature.INDENT_OUTPUT);
		}
		return builder.build();
	}
	
	public static JsonNode getFieldOrThrow(JsonNode node, String fieldName, @Nullable String nodeName)
		throws IOException {
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			String msg = String.format("JsonNode%s has missing field: field=%s",
										(nodeName != null) ? " '" + nodeName + "'" : "", fieldName);
			throw new IOException(msg);
		}
		return field;
	}

	public static JsonNode getFieldOrNull(JsonNode node, String fieldName) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return null;
		}
		else {
			return field;
		}
	}
	public static JsonNode getField(JsonNode node, String fieldName) {
		JsonNode field = getFieldOrNull(node, fieldName);
		if ( field == null ) {
			throw new IllegalStateException("Invalid (empty) field value: field=" + fieldName);
		}
		return field;
	}

	public static String getStringFieldOrDefault(JsonNode node, String fieldName, String defaultValue) {
		JsonNode field = getFieldOrNull(node, fieldName);
		return (field != null) ? field.asText() : defaultValue;
	}
	@Nullable public static String getStringFieldOrNull(JsonNode node, String fieldName) {
		return getStringFieldOrDefault(node, fieldName, null);
	}
	public static String getStringField(JsonNode node, String fieldName) {
		return getField(node, fieldName).asText();
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

	public static int getIntField(JsonNode node, String fieldName, int defaultValue) {
		Preconditions.checkNotNull(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return defaultValue;
		}
		else {
			return field.asInt();
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
