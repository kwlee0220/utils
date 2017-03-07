package utils.config;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import utils.Duration;
import utils.Tuple2;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ConfigNode {
	public Configuration getConfiguration();
	
	public ConfigNode getParent();
	public String getPath();
	
	public default ConfigNode getRoot() {
		ConfigNode conf = (ConfigNode)this;
		while ( true ) {
			ConfigNode parent = conf.getParent();
			if ( parent == null ) {
				return conf;
			}
			else {
				conf = parent;
			}
		}
	}
	
	public boolean isMap();
	public boolean isArray();
	public boolean isPrimitive();
	public default boolean isMissing() {
		return false;
	}
	
	public Object getValue();
	
	public int asInt();
	public default int asInt(int defValue) {
		return isMissing() ? defValue : asInt();
	}
	
	public long asLong();
	public default long asLong(int defValue) {
		return isMissing() ? defValue : asLong();
	}
	
	public float asFloat();
	public default float asFloat(float defValue) {
		return isMissing() ? defValue : asFloat();
	}
	
	public double asDouble();
	public default double asDouble(double defValue) {
		return isMissing() ? defValue : asDouble();
	}
	
	public short asShort();
	public default short asShort(short defValue) {
		return isMissing() ? defValue : asShort();
	}
	
	public byte asByte();
	public default byte asByte(byte defValue) {
		return isMissing() ? defValue : asByte();
	}
	
	public boolean asBoolean();
	public default boolean asBoolean(boolean defValue) {
		return isMissing() ? defValue : asBoolean();
	}
	
	public String asString();
	public default String asString(String defValue) {
		return isMissing() ? defValue : asString();
	}
	
	public ConfigNode asReference();
	
	public default Duration asDuration() {
		Preconditions.checkState(isPrimitive(), "Not primitive node: node=" + this);
		
		Object obj = getValue();
		if ( obj instanceof String ) {
			return Duration.parseDuration((String)obj);
		}
		else if ( obj instanceof Number ) {
			return Duration.milliseconds(((Number)obj).longValue());
		}
		else {
			throw new IllegalStateException("Cannot convert to Duration: node=" + this);
		}
	}
	public default Duration asDuration(Duration defValue) {
		return isMissing() ? defValue : asDuration();
	}
	public default Duration asDuration(long defMillis) {
		return isMissing() ? Duration.milliseconds(defMillis) : asDuration();
	}
	
	public default Object[] getAsArray() {
		Object[] values = new Object[size()];
		for ( int i =0; i < values.length; ++i ) {
			values[i] = get(i);
		}
		
		return values;
	}

	public default int[] getAsIntArray() {
		int[] values = new int[size()];
		for ( int i =0; i < values.length; ++i ) {
			Object elm = get(i);
			if ( !(elm instanceof Number) ) {
				throw new IllegalStateException();
			}
			
			values[i] = ((Number)elm).intValue();
		}
		
		return values;
	}

	public default long[] getAsLongArray() {
		long[] values = new long[size()];
		for ( int i =0; i < values.length; ++i ) {
			Object elm = get(i);
			if ( !(elm instanceof Number) ) {
				throw new IllegalStateException();
			}
			
			values[i] = ((Number)elm).longValue();
		}
		
		return values;
	}

	public default float[] getAsFloatArray() {
		float[] values = new float[size()];
		for ( int i =0; i < values.length; ++i ) {
			Object elm = get(i);
			if ( !(elm instanceof Number) ) {
				throw new IllegalStateException();
			}
			
			values[i] = ((Number)elm).floatValue();
		}
		
		return values;
	}

	public default double[] getAsDoubleArray() {
		if ( !isArray() ) {
			throw new IllegalStateException(String.format("not ARRAY, path=%s", getPath()));
		}
		
		double[] values = new double[size()];
		for ( int i =0; i < values.length; ++i ) {
			Object elm = get(i);
			if ( !(elm instanceof Number) ) {
				throw new IllegalStateException(getPath());
			}
			
			values[i] = ((Number)elm).doubleValue();
		}
		
		return values;
	}

	public default boolean[] getAsBooleanArray() {
		boolean[] values = new boolean[size()];
		for ( int i =0; i < values.length; ++i ) {
			Object elm = get(i);
			if ( !(elm instanceof Boolean) ) {
				throw new IllegalStateException();
			}
			
			values[i] = ((Boolean)elm).booleanValue();
		}
		
		return values;
	}
	
	public Map<String,Object> getAsMap();
	public ConfigNode get(String name);
	public boolean has(String name);
	public Set<String> names();
	
	public int size();
	public ConfigNode get(int index);
	
	public default File getAsFile() {
		return new File(asString());
	}
	
	public default Set<ConfigNode> findConfigByName(String name) {
		Set<ConfigNode> found = Sets.newHashSet();
		
		List<Tuple2<String,ConfigNode>> remains = Lists.newArrayList();
		remains.add(new Tuple2<>("", getRoot()));
		
		while ( !remains.isEmpty() ) {
			Tuple2<String,ConfigNode> tuple = remains.remove(0);
			if ( tuple._1.equals(name) ) {
				found.add(tuple._2);
			}
			
			if ( tuple._2.isMap() ) {
				ConfigNode map = tuple._2;
				tuple._2.names().stream()
					.forEach(n -> remains.add(new Tuple2<>(n,map.get(n))));
			}
		}
		
		return found;
	}
	
	public default ConfigNode traverse(String path) {
		String[] parts = Arrays.stream(path.split("/"))
								.map(String::trim)
								.toArray(sz -> new String[sz]);

		ConfigNode current = (ConfigNode)this;
		int idx = 0;
		String head = parts[0];
		if ( head.length() == 0 ) {
			current = getRoot();
			++idx;
		}
		else if ( head.startsWith("@") ) {
			Set<ConfigNode> founds = findConfigByName(head.substring(1));
			if ( founds.size() == 1 ) {
				current = founds.iterator().next();
				++idx;
			}
			else if ( founds.size() == 0 ) {
				return new MissingConfigNode(getConfiguration(), path);
			}
			else {
				throw new IllegalArgumentException("ambiguous state id=" + head);
			}
		}

		for (; idx < parts.length; ++idx ) {
			if ( parts[idx].equals("..") ) {
				current = current.getParent();
				if ( current == null ) {
					return new MissingConfigNode(getConfiguration(), path);
				}
			}
			else if ( parts[idx].equals(".") ) { }
			else {
				String linkExpr = parts[idx];
				
				while ( linkExpr.length() > 0 ) {
					int begin = linkExpr.indexOf('[');
					if ( begin < 0 ) {
						current = current.get(linkExpr);
						if ( current == null ) {
							return new MissingConfigNode(getConfiguration(), path);
						}
						break;
					}
					else {
						String member = linkExpr.substring(0, begin);
						current = current.get(member);
						if ( current == null ) {
							return new MissingConfigNode(getConfiguration(), path);
						}
						
						int end = linkExpr.indexOf(']');
						if ( end < 0 ) {
							throw new IllegalArgumentException("unmatched []: path=" + path);
						}
						String idxStr = linkExpr.substring(begin+1, end);
						current = current.get(Integer.parseInt(idxStr));
						linkExpr = linkExpr.substring(end+1);
					}
				}
			}
		}
		
		return current;
	}
	
	public static String toPath(String parentPath, String memberName) {
		return (parentPath.length() > 0)
				? parentPath + "." + memberName
				: memberName;
	}
}
