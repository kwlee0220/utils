package utils.json;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface JsonSerDe<T> {
	public JsonNode serialize(T obj) throws IOException;
	public T deserialize(JsonNode jnode) throws IOException;
}
