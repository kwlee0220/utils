package utils.xml;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

import org.xml.sax.SAXException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface XmlSerializable {
	/**
	 * 본 객체를 XML로 marshall시킨다. 생성된 XML의 최상위 node는
	 * 주어진 {@link FluentElement}의 하위 node가 된다.
	 * 
	 *  @param topElm	생성될 XML 노드가 추가될 상위 노드.
	 */
	public void toXml(FluentElement topElm);
	
	/**
	 * 본 객체를 XML 문서로 변환시킨 문자열을 반환한다.
	 * 생성된 노드의 최상위 노드 이름을 인자인 topElmName에 의해 결정된다.
	 * 계층구조의 XML 문서가 생성되는 경우 하위 노드들을 {@code intent} 값만클 들여쓰기된다.
	 * 
	 * @param topElmName	생성될 XML 문서의 최상위 이름
	 * @param indent		들어쓰기 값.
	 */
	public default String toXmlString(String topElmName, int indent) {
		return toXmlDocument(topElmName).toXmlString(false, indent);
	}
	
	/**
	 * 본 객체를 XML 문서로 변환시킨 문자열을 반환한다.
	 * 생성된 노드의 최상위 노드 이름을 인자인 topElmName에 의해 결정된다.
	 * 계층구조의 XML 문서가 생성되는 경우 하위 노드들을 2만클 들여쓰기된다.
	 * 
	 * @param topElmName	생성될 XML 문서의 최상위 이름
	 */
	public default String toXmlString(String topElmName) {
		return toXmlDocument(topElmName).toXmlString(false, 2);
	}
	
	public default void toXmlFile(String topElmName, File xmlFile, int indent) throws IOException {
		toXmlDocument(topElmName).toXmlFile(xmlFile, false, indent);
	}
	
	public default FluentDocument toXmlDocument(String topElmName) {
		FluentDocument doc = FluentDocument.newInstance();
		doc.appendTypedChild(topElmName, this);
		
		return doc;
	}
	
	public static FluentElement serialize2(FluentElement parent, String childName, Object obj) {
		FluentElement elm = parent.appendChild(childName).attr("class", obj.getClass().getName());
		if ( obj instanceof XmlSerializable ) {
			((XmlSerializable)obj).toXml(elm);
		}
		
		return elm;
	}
	
	public static Object fromXmlString(String xmlString) throws SAXException {
		return loadXmlSerializable2(FluentDocument.from(xmlString).getDocumentElement());
	}
	
	public static Object loadXmlSerializable2(FluentElement elm) {
		Optional<String> clsName = elm.attr("class");
		if ( !clsName.isPresent() ) {
			throw new XmlSerializationException("No 'class' attribute, Element=" + elm);
		}
		
		Class<?> cls =null;
		try {
			cls = Class.forName(clsName.get());
			if ( LoadableXmlSerializable.class.isAssignableFrom(cls) ) {
				return loadLoadableXmlSerializable((Class<? extends LoadableXmlSerializable>)cls, elm);
			}
			
			Method method = cls.getMethod("fromXml", FluentElement.class);
			if ( Modifier.isStatic(method.getModifiers()) ) {
				return method.invoke(null, elm);
			}
			else {
				throw new XmlSerializationException("class does not have 'fromXml' static method: class="
													+ cls.getName());
			}
		}
		catch ( ClassNotFoundException e ) {
			throw new XmlSerializationException("fails to load class from Element: class="
													+ clsName.get(), e);
		}
		catch ( NoSuchMethodException e ) {
			try {
				return cls.newInstance();
			}
			catch ( Throwable e1 ) {
				throw new XmlSerializationException("fails to load class from Element: class="
						+ clsName.get(), e);
			}
		}
		catch ( InvocationTargetException e ) {
			throw new XmlSerializationException("fails to load class from Element: class="
												+ clsName.get(), e.getTargetException());
		}
		catch ( Throwable e ) {
			throw new XmlSerializationException("fails to load class from Element: class="
												+ clsName.get(), e);
		}
	}
	
	public static Object loadXmlSerializable2(FluentElement elm, Class<?> cls) {
		try {
			if ( LoadableXmlSerializable.class.isAssignableFrom(cls) ) {
				return loadLoadableXmlSerializable((Class<? extends LoadableXmlSerializable>)cls, elm);
			}
			
			Method method = cls.getMethod("fromXml", FluentElement.class);
			if ( Modifier.isStatic(method.getModifiers()) ) {
				return method.invoke(null, elm);
			}
			else {
				throw new XmlSerializationException("class does not have 'fromXml' static method: class="
													+ cls.getName());
			}
		}
		catch ( NoSuchMethodException e ) {
			try {
				return cls.newInstance();
			}
			catch ( Throwable e1 ) {
				throw new XmlSerializationException("fails to load class from Element: class="
						+ cls.getName(), e);
			}
		}
		catch ( InvocationTargetException e ) {
			throw new XmlSerializationException("fails to load class from Element: class="
												+ cls.getName(), e.getTargetException());
		}
		catch ( Throwable e ) {
			throw new XmlSerializationException("fails to load class from Element: class="
												+ cls.getName(), e);
		}
	}

/*
	public FluentElement toChildXml(FluentNode parent);
	public FluentElement toChildXml(FluentNode parent, String childElmName);
	
	public static FluentElement serialize(FluentElement parent, String childName, Object obj) {
		FluentElement elm = parent.appendChild(childName).attr("class", obj.getClass().getName());
		if ( obj instanceof XmlSerializable ) {
			((XmlSerializable)obj).toChildXml(elm);
		}
		
		return elm;
	}
	
	public static Object loadXmlSerializable(FluentElement elm) {
		Optional<String> clsName = elm.attr("class");
		if ( !clsName.isPresent() ) {
			throw new XmlSerializationException("No 'class' attribute, Element=" + elm);
		}
		
		Class<?> cls =null;
		try {
			cls = Class.forName(clsName.get());
			if ( LoadableXmlSerializable.class.isAssignableFrom(cls) ) {
				return loadLoadableXmlSerializable((Class<? extends LoadableXmlSerializable>)cls, elm);
			}
			
			Method method = cls.getMethod("fromXml", FluentElement.class);
			if ( Modifier.isStatic(method.getModifiers()) ) {
				return method.invoke(null, elm);
			}
			else {
				Object obj = cls.newInstance();
				return method.invoke(obj, elm);
			}
		}
		catch ( ClassNotFoundException e ) {
			throw new XmlSerializationException("fails to load class from Element: class="
													+ clsName.get(), e);
		}
		catch ( NoSuchMethodException e ) {
			try {
				return cls.newInstance();
			}
			catch ( Throwable e1 ) {
				throw new XmlSerializationException("fails to load class from Element: class="
						+ clsName.get(), e);
			}
		}
		catch ( InvocationTargetException e ) {
			throw new XmlSerializationException("fails to load class from Element: class="
												+ clsName.get(), e.getTargetException());
		}
		catch ( Throwable e ) {
			throw new XmlSerializationException("fails to load class from Element: class="
												+ clsName.get(), e);
		}
	}
*/
	
	public static <T extends LoadableXmlSerializable> T loadLoadableXmlSerializable(Class<T> cls, FluentElement elm) {
		try {
			Constructor<T> ctor = cls.getDeclaredConstructor();
			ctor.setAccessible(true);
			T obj = ctor.newInstance();
			obj.loadFromXml(elm);
			
			return obj;
		}
		catch ( Throwable e ) {
			throw new XmlSerializationException("fails to load a LoadableXmlSerializable: class="
												+ cls.getName(), e);
		}
	}
}
