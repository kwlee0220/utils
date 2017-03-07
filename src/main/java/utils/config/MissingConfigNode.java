package utils.config;

import java.util.Map;
import java.util.Set;

import edu.emory.mathcs.backport.java.util.Collections;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MissingConfigNode implements ConfigNode {
	private final Configuration m_config;
	private final String m_path;
	
	public MissingConfigNode(Configuration config, String path) {
		m_config = config;
		m_path = path;
	}

	@Override
	public Configuration getConfiguration() {
		return m_config;
	}

	@Override
	public ConfigNode getParent() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public String getPath() {
		return m_path;
	}

	@Override
	public boolean isMap() {
		return false;
	}

	@Override
	public boolean isArray() {
		return false;
	}

	@Override
	public boolean isPrimitive() {
		return false;
	}

	@Override
	public boolean isMissing() {
		return true;
	}

	@Override
	public Object getValue() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public int asInt() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public long asLong() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public float asFloat() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public double asDouble() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public short asShort() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public byte asByte() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public boolean asBoolean() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public String asString() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public Map<String, Object> getAsMap() {
		return Collections.emptyMap();
	}

	@Override
	public ConfigNode get(String name) {
		return new MissingConfigNode(m_config, ConfigNode.toPath(m_path, name));
	}

	@Override
	public Set<String> names() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public boolean has(String name) {
		return false;
	}

	@Override
	public int size() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public ConfigNode get(int index) {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}

	@Override
	public ConfigNode asReference() {
		throw new IllegalStateException("non-existent Config: path=" + m_path);
	}
}
