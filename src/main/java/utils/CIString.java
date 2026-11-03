package utils;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;

import utils.func.Lazy;
import utils.stream.FStream;


/**
 * 대소문자를 구분하지 않고 비교되는 문자열 래퍼.
 * <p>
 * 원래 문자열은 그대로 보존하며({@link #get()}, {@link #toString()}), {@link #equals(Object)},
 * {@link #hashCode()}, {@link #compareTo(CIString)}는 대소문자를 무시한다. 직렬화할 때는 원래 문자열만
 * 담은 serialization proxy를 쓴다.
 * <p>
 * <b>주의:</b> {@link #equals(Object)}는 같은 문자열(대소문자 무시)인 {@link String}과도 {@code true}를
 * 돌려주지만, {@code String.equals(CIString)}은 항상 {@code false}이므로 대칭성이 깨진다.
 * 또 {@link #hashCode()}는 기본 로케일의 {@link String#toLowerCase()}를, {@code equals}는
 * {@link String#equalsIgnoreCase(String)}를 쓰므로 터키어 로케일 등에서는 같다고 판정된 두 객체의
 * 해시 값이 다를 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class CIString implements Comparable<CIString>, Serializable {
	private static final long serialVersionUID = 1239412058632797493L;
	
	/**
	 * 원래(대소문자를 보존한) 문자열.
	 */
	private final String m_name;
	/**
	 * {@link #hashCode()} 계산용 소문자 문자열. 처음 필요할 때 계산한다.
	 */
	private final Lazy<String> m_lowerName;
	
	/**
	 * 주어진 문자열로 {@link CIString}을 만든다.
	 *
	 * @param name	문자열.
	 * @return	새 {@link CIString} 객체.
	 * @throws IllegalArgumentException	{@code name}이 {@code null}인 경우.
	 */
	public static CIString of(String name) {
		return new CIString(name);
	}
	
	private CIString(String name) {
		Preconditions.checkNotNullArgument(name, "column name cannot be null");
		
		m_name = name;
		m_lowerName = Lazy.of(() -> m_name.toLowerCase());
	}
	
	/**
	 * 대소문자를 보존한 원래 문자열을 반환한다.
	 *
	 * @return	원래 문자열.
	 */
	public String get() {
		return m_name;
	}
	
	/**
	 * 문자열 목록의 각 원소를 {@link CIString}으로 바꾼 새 목록을 만든다. 순서는 유지된다.
	 *
	 * @param colNames	문자열 목록.
	 * @return	변환된 {@link CIString} 목록.
	 * @throws IllegalArgumentException	{@code colNames}가 {@code null}이거나 {@code null} 원소를 포함하는 경우.
	 */
	public static List<CIString> fromNameList(List<String> colNames) {
		return FStream.from(colNames).map(CIString::of).toList();
	}

	@Override
	public String toString() {
		return m_name;
	}
	
	/**
	 * 주어진 문자열과 대소문자를 무시하고 같은지 여부를 반환한다.
	 *
	 * @param name	비교할 문자열. {@code null}이면 {@code false}를 반환한다.
	 * @return	대소문자를 무시하고 같으면 {@code true}.
	 */
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
		else if ( obj == null ) {
			return false;
		}
		else if ( obj.getClass() == CIString.class ) {
			CIString other = (CIString)obj;
			return m_name.equalsIgnoreCase(other.get());
		}
		else if ( obj.getClass() == String.class ) {
			String other = (String)obj;
			return m_name.equalsIgnoreCase(other);
		}
		else {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return m_lowerName.get().hashCode();
	}
	
	/**
	 * 직렬화할 때 이 객체 대신 원래 문자열만 담은 serialization proxy를 쓰도록 한다.
	 *
	 * @return	serialization proxy 객체.
	 */
	private Object writeReplace() {
		return new SerializationProxy(this);
	}
	
	/**
	 * 이 클래스를 직접 역직렬화하는 것을 막는다. 역직렬화는 serialization proxy를 통해서만 가능하다.
	 *
	 * @param stream	입력 스트림.
	 * @throws InvalidObjectException	항상 던진다.
	 */
	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
		throw new InvalidObjectException("Use Serialization Proxy instead.");
	}

	private static class SerializationProxy implements Serializable {
		private static final long serialVersionUID = -4183428723712485003L;
		
		private final String m_strRep;
		
		private SerializationProxy(CIString mcKey) {
			m_strRep = mcKey.toString();
		}
		
		private Object readResolve() {
			return CIString.of(m_strRep);
		}
	}
}