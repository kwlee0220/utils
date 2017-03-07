package utils.config.json;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import utils.config.ConfigNode;
import utils.config.MissingConfigNode;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JsonConfigNode implements ConfigNode {
	private final JsonConfiguration m_config;
	private final ConfigNode m_parent;
	private final String m_path;
	private final JsonElement m_elm;
	
	JsonConfigNode(JsonConfiguration config, ConfigNode parent, String path, JsonElement elm) {
		m_config = config;
		m_parent = parent;
		m_path = path;
		m_elm = elm;
	}

	@Override
	public final JsonConfiguration getConfiguration() {
		return m_config;
	}

	@Override
	public final ConfigNode getParent() {
		return m_parent;
	}

	@Override
	public final String getPath() {
		return m_path;
	}

	@Override
	public boolean isMap() {
		return m_elm instanceof JsonObject;
	}

	@Override
	public boolean isArray() {
		return m_elm instanceof JsonArray;
	}

	@Override
	public boolean isPrimitive() {
		return m_elm instanceof JsonPrimitive;
	}

	@Override
	public int asInt() {
		return m_elm.getAsInt();
	}

	@Override
	public long asLong() {
		return m_elm.getAsLong();
	}

	@Override
	public String asString() {
		return m_elm.getAsString();
	}

	@Override
	public float asFloat() {
		return m_elm.getAsFloat();
	}

	@Override
	public double asDouble() {
		return m_elm.getAsDouble();
	}

	@Override
	public short asShort() {
		return m_elm.getAsShort();
	}

	@Override
	public byte asByte() {
		return m_elm.getAsByte();
	}

	@Override
	public boolean asBoolean() {
		return m_elm.getAsBoolean();
	}

	@Override
	public ConfigNode asReference() {
		return traverse(asString());
	}

	@Override
	public Object[] getAsArray() {
		Preconditions.checkState(m_elm.isJsonArray(), "Not ARRAY node: node=" + this);
		
		JsonArray arr = (JsonArray)m_elm;
		return IntStream.range(0, arr.size())
						.mapToObj(idx -> arr.get(idx))
						.toArray(sz -> new Object[sz]);
	}

	@Override
	public Map<String,Object> getAsMap() {
		Preconditions.checkState(m_elm.isJsonObject(), "Not MAP node: node=" + this);

		return ((JsonObject)m_elm).entrySet().stream()
									.collect(Collectors.toMap(Map.Entry::getKey,
													ent -> getAsJavaObject(ent.getValue())));
	}

	@Override
	public Set<String> names() {
		Preconditions.checkState(m_elm.isJsonObject(), "Not MAP node: node=" + this);
		
		return ((JsonObject)m_elm).entrySet()
									.stream()
									.map(Map.Entry::getKey)
									.collect(Collectors.toSet());
	}

	@Override
	public ConfigNode get(String name) {
		Preconditions.checkState(m_elm.isJsonObject(), "Not MAP node: node=" + this);
		
		String memberPath = (getPath().length() > 0) ? getPath() + "." + name : name;
		JsonElement member = ((JsonObject)m_elm).get(name);
		return (member != null) ? new JsonConfigNode(m_config, this, memberPath, member)
								: new MissingConfigNode(m_config, memberPath);
	}

	@Override
	public boolean has(String name) {
		Preconditions.checkState(m_elm.isJsonObject(), "Not MAP node: node=" + this);
		
		return ((JsonObject)m_elm).has(name);
	}

	@Override
	public int size() {
		Preconditions.checkState(m_elm.isJsonArray(), "Not ARRAY node: node=" + this);
		
		return ((JsonArray)m_elm).size();
	}

	@Override
	public ConfigNode get(int index) {
		Preconditions.checkState(m_elm.isJsonArray(), "Not ARRAY node: node=" + this);
		
		String memberPath = String.format("%s[%d]", getPath(), index);
		
		JsonElement member = ((JsonArray)m_elm).get(index);
		return new JsonConfigNode(m_config, this, memberPath, member);
	}
	
	@Override
	public String toString() {
		return toString(m_elm);
	}
	
	private static String toString(JsonElement elm) {
		if ( elm.isJsonPrimitive() ) {
			return getAsJavaObject(elm).toString();
		}
		else if ( elm.isJsonObject() ) {
			return ((JsonObject)elm).entrySet().stream()
									.map(ent -> String.format("%s=%s", ent.getKey(),
																toString(ent.getValue())))
									.collect(Collectors.joining(", ", "{", "}"));
		}
		else if ( elm.isJsonArray() ) {
			JsonArray arr = (JsonArray)elm;
			return IntStream.range(0, arr.size())
							.mapToObj(idx -> toString(arr.get(idx)))
							.collect(Collectors.joining(",", "[", "]"));
		}
		
		throw new AssertionError();
	}
	
	public Object getValue() {
		return getAsJavaObject(m_elm);
	}
	
	private static Object getAsJavaObject(JsonElement elm) {
		if ( elm.isJsonPrimitive() ) {
			JsonPrimitive node = (JsonPrimitive)elm;
			if ( node.isString() ) {
				return node.getAsString();
			}
			else if ( node.isNumber() ) {
				return node.getAsNumber();
			}
			else if ( node.isBoolean() ) {
				return node.getAsBoolean();
			}
			else {
				throw new AssertionError();
			}
		}
		else if ( elm.isJsonObject() ) {
			return ((JsonObject)elm).entrySet().stream()
										.collect(Collectors.toMap(Map.Entry::getKey,
														ent -> getAsJavaObject(ent.getValue())));
		}
		else if ( elm.isJsonArray() ) {
			JsonArray arr = (JsonArray)elm;
			return IntStream.range(0, arr.size())
							.mapToObj(idx -> arr.get(idx))
							.toArray(sz -> new Object[sz]);
		}
		
		throw new AssertionError();
	}
}
