package utils;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import io.vavr.Lazy;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class CIString implements Comparable<CIString>, Serializable {
	private static final long serialVersionUID = 1239412058632797493L;
	
	private final String m_name;
	private final Lazy<String> m_lowerName;
	
	public static CIString of(String name) {
		return new CIString(name);
	}
	
	private CIString(String name) {
		Objects.requireNonNull(name, "column name");
		
		m_name = name;
		m_lowerName = Lazy.of(() -> m_name.toLowerCase());
	}
	
	public String get() {
		return m_name;
	}
	
	public static List<CIString> fromNameList(List<String> colNames) {
		return FStream.from(colNames).map(CIString::of).toList();
	}

	@Override
	public String toString() {
		return m_name;
	}
	
	public boolean matches(String name) {
		return m_name.equalsIgnoreCase(name);
	}

	@Override
	public int compareTo(CIString other) {
		return m_name.compareToIgnoreCase(other.m_name);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || obj.getClass() != CIString.class ) {
			return false;
		}
		
		CIString other = (CIString)obj;
		return m_name.equalsIgnoreCase(other.m_name);
	}
	
	@Override
	public int hashCode() {
		return m_lowerName.get().hashCode();
	}
	
	private Object writeReplace() {
		return new SerializationProxy(this);
	}
	
	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
		throw new InvalidObjectException("Use Serialization Proxy instead.");
	}

	private static class SerializationProxy implements Serializable {
		private static final long serialVersionUID = -4183428723712485003L;
		
		private transient final String m_strRep;
		
		private SerializationProxy(CIString mcKey) {
			m_strRep = mcKey.toString();
		}
		
		private Object readResolve() {
			return CIString.of(m_strRep);
		}
	}
}