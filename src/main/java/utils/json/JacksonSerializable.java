package utils.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonSerialize(using = JacksonSerializables.Serializer.class)
@JsonDeserialize(using = JacksonSerializables.Deserializer.class)
public interface JacksonSerializable {
	public String getSerializationType();
	public void serializeFields(JsonGenerator gen) throws IOException;
}
