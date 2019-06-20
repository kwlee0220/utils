package utils.xml;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.xml.sax.SAXException;

import utils.Throwables;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface XmlSerializable {
	/**
	 * 본 객체를 XML로 serialize시킨다. 생성된 XML의 최상위 node는
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
	 * @return	변환된 XML 문자열
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
	 * @return	변환된 XML 문자열
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
	
	public static FluentElement serialize(FluentElement parent, String childName, Object obj) {
		FluentElement elm = parent.appendChild(childName).attr("class", obj.getClass().getName());
		if ( obj instanceof XmlSerializable ) {
			((XmlSerializable)obj).toXml(elm);
		}
		
		return elm;
	}
	
	public static Object fromXmlString(String xmlString) {
		try {
			return loadXmlSerializable(FluentDocument.from(xmlString).getDocumentElement());
		}
		catch ( SAXException e ) {
			throw new XmlSerializationException(String.format("fails to load %s from XML: xml=%s",
												XmlSerializable.class.getSimpleName(), xmlString));
		}
	}
	
	/**
	 * 주어진 {@link FluentElement} 'topElm' 에 해당하는 객체를 생성한다.
	 * <p>
	 * 입력 'topElm'은 반드시 'class' attribute를 갖고 있어야 하고, 이 값은 생성할 객체의
	 * 클래스 이름이 기록되어야 한다.
	 * 'class' attribute에 기술된 클래스에 따라 다음과 같은 방식으로 객체를 생성한다.
	 * <ul>
	 * 	<li> 만일 클래스가 {@link LoadableXmlSerializable}를 구현한 경우는, 클래스의
	 * 		default constructor를 호출하여 객체를 생성한다.
	 * 		생성된 객체의 {@link LoadableXmlSerializable#loadFromXml(FluentElement)}를
	 * 		호출하여 해당 객체를 초기화시키되 입력 인자로 'topElm'을 사용한다.
	 * 	<li> 만일 클래스에 {@link FluentElement}를 인자로 갖는 'fromXml'라는 이름의 static 메소드가
	 * 		정의된 경우에는, 이 메소드를 호출여여 객체를 생성 및 초기화를 수행한다.
	 * 		만일 'fromXml'이라는 메소드는 존재하지만 static 메소드가 아닌 경우는 예외를 발생시킨다.
	 * 	<li> 나머지 경우는 클래스의 default constructor를 호출하여 객체를 생성하고, 별도의 초기화 작업을
	 * 		수행하지 않는다.
	 * </ul>
	 * 
	 * @param topElm	변환시킬 XML 문서의 최상위 FluentElement
	 * @return	'topElm' 에 해당하는 객체
	 */
	public static Object loadXmlSerializable(FluentElement topElm) {
		String clsName = topElm.attr("class")
								.getOrElseThrow(()->new XmlSerializationException(
														"No 'class' attribute, Element=" + topElm));
		
		Class<?> cls =null;
		try {
			cls = Class.forName(clsName);
			if ( LoadableXmlSerializable.class.isAssignableFrom(cls) ) {
				return loadLoadableXmlSerializable((Class<? extends LoadableXmlSerializable>)cls, topElm);
			}
			
			Method method = cls.getMethod("fromXml", FluentElement.class);
			if ( Modifier.isStatic(method.getModifiers()) ) {
				return method.invoke(null, topElm);
			}
			else {
				throw new XmlSerializationException("class does not have 'fromXml' static method: class="
													+ cls.getName());
			}
		}
		catch ( ClassNotFoundException e ) {
			throw new XmlSerializationException("fails to load class from Element: class="
													+ clsName, e);
		}
		catch ( NoSuchMethodException e ) {
			try {
				return Utilities.callPrivateConstructor(cls);
			}
			catch ( Throwable e1 ) {
				throw new XmlSerializationException("fails to load class from Element: class="
						+ clsName, Throwables.unwrapThrowable(e1));
			}
		}
		catch ( Throwable e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			throw new XmlSerializationException("fails to load class from Element: class="
												+ clsName, cause);
		}
	}
	
	public static <T extends LoadableXmlSerializable> T loadLoadableXmlSerializable(Class<T> cls, FluentElement elm) {
		try {
			T obj = Utilities.callPrivateConstructor(cls);
			obj.loadFromXml(elm);
			
			return obj;
		}
		catch ( Throwable e ) {
			throw new XmlSerializationException("fails to load a LoadableXmlSerializable: class="
												+ cls.getName(), e);
		}
	}
}
