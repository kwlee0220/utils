package utils.json;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonSerializables {
	private static final String FIELD_TYPE = "@type";
	
	private JacksonSerializables() {
		throw new AssertionError("Should not be invoked!!: class=" + JacksonSerializables.class.getName());
	}
	
	private static final BiMap<String,Class<? extends JacksonSerializable>> SERIALIZABLES = HashBiMap.create();
	public static void registerSerializer(String type, Class<? extends JacksonSerializable> serializableCls) {
		Class<? extends JacksonSerializable> prev = SERIALIZABLES.put(type, serializableCls);
		if ( prev != null ) {
			SERIALIZABLES.put(type, prev);
			throw new IllegalStateException("JacksonSerializable alreay exists: type=" + type);
		}
	}
	
	public static JacksonSerializable parseTypedJsonNode(JsonNode jnode) {
		return parseTypedJsonNode(jnode, JacksonSerializable.class);
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends JacksonSerializable> T parseTypedJsonNode(JsonNode jnode, Class<T> targetCls) {
		String type = JacksonUtils.getStringFieldOrNull(jnode, FIELD_TYPE);
		if ( type == null ) {
			throw new JacksonDeserializationException(String.format("'%s' field is missing: json=%s",
																	FIELD_TYPE, jnode));
		}
		Class<? extends JacksonSerializable> serdeCls = SERIALIZABLES.get(type);
		if ( serdeCls == null ) {
			throw new JacksonDeserializationException("Unregistered JacksonSerializable type: " + type);
		}

		try {
			Method deserialize = serdeCls.getDeclaredMethod("deserializeFields", JsonNode.class);
			if ( !Modifier.isStatic(deserialize.getModifiers()) ) {
				throw new JacksonDeserializationException("'deserializeFields' must be a static: "
															+ "class=" + serdeCls.getName());
			}
			if ( !JacksonSerializable.class.isAssignableFrom(deserialize.getReturnType()) ) {
				throw new JacksonDeserializationException("'deserializeFields' must return an Object "
														+ "of 'JacksonSerializable': class=" + serdeCls.getName());
			}
			
			return (T)deserialize.invoke(null, jnode);
		}
		catch ( InvocationTargetException e ) {
			throw new JacksonDeserializationException("Failed to parse JSON string: json=" + jnode, e.getCause());
		}
		catch ( NoSuchMethodException | SecurityException | IllegalAccessException e ) {
			throw new JacksonDeserializationException("Failed to invoke deserializeFields() method: class="
														+ serdeCls.getName());
		}
	}

	@SuppressWarnings("serial")
	public static class Deserializer extends StdDeserializer<JacksonSerializable> {
		public Deserializer() {
			this(null);
		}
		public Deserializer(Class<?> vc) {
			super(vc);
		}
	
		@Override
		public JacksonSerializable deserialize(JsonParser parser, DeserializationContext ctxt)
			throws IOException, JacksonException {
			JsonNode jnode = parser.getCodec().readTree(parser);
			return parseTypedJsonNode(jnode, JacksonSerializable.class);
		}
	}
	
	@SuppressWarnings("serial")
	public static class Serializer extends StdSerializer<JacksonSerializable> {
		private Serializer() {
			this(null);
		}
		private Serializer(Class<JacksonSerializable> cls) {
			super(cls);
		}
		
		@Override
		public void serialize(JacksonSerializable serde, JsonGenerator gen, SerializerProvider provider)
			throws IOException, JsonProcessingException {
			gen.writeStartObject();
			gen.writeStringField(FIELD_TYPE, serde.getSerializationType());
			serde.serializeFields(gen);
			gen.writeEndObject();
		}
	}
}
