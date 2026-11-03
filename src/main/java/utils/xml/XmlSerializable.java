package utils.xml;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.xml.sax.SAXException;

import utils.ReflectionUtils;
import utils.Throwables;

/**
 * {@link FluentElement}를 이용하여 XML로 serialize될 수 있는 객체의 인터페이스.
 * <p>
 * XML로부터 객체를 다시 생성하는 static 메소드({@link #loadXmlSerializable(FluentElement)} 등)도 함께 제공한다.
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
	
	/**
	 * 본 객체를 XML 문서로 변환하여 주어진 파일에 기록한다.
	 * <p>
	 * 최상위 요소의 이름은 {@code topElmName}이고 {@code class} 속성에 본 객체의 클래스 이름이 기록된다.
	 * XML 선언은 포함되며, 파일은 플랫폼 기본 문자셋으로 기록된다.
	 *
	 * @param topElmName	생성될 XML 문서의 최상위 요소 이름.
	 * @param xmlFile		기록할 파일. 기존 내용은 덮어쓴다.
	 * @param indent		들여쓰기 크기. 0이면 들여쓰기를 하지 않는다.
	 * @throws IOException	파일을 열 수 없는 경우.
	 */
	public default void toXmlFile(String topElmName, File xmlFile, int indent) throws IOException {
		toXmlDocument(topElmName).toXmlFile(xmlFile, false, indent);
	}
	
	/**
	 * 본 객체를 XML 문서 객체로 변환한다.
	 * <p>
	 * 새 문서에 {@code topElmName} 이름의 최상위 요소를 만들고, {@code class} 속성에 본 객체의
	 * 클래스 이름을 기록한 뒤 {@link #toXml(FluentElement)}를 호출하여 내용을 채운다.
	 *
	 * @param topElmName	생성될 XML 문서의 최상위 요소 이름.
	 * @return	생성된 XML 문서.
	 */
	public default FluentDocument toXmlDocument(String topElmName) {
		FluentDocument doc = FluentDocument.newInstance();
		doc.appendTypedChild(topElmName, this);
		
		return doc;
	}
	
	/**
	 * 주어진 객체를 {@code parent}의 하위 요소로 serialize한다.
	 * <p>
	 * {@code childName} 이름의 하위 요소를 추가하고 {@code class} 속성에 객체의 클래스 이름을 기록한다.
	 * 객체가 {@link XmlSerializable}인 경우에는 {@link #toXml(FluentElement)}를 호출하여 내용을 채우고,
	 * 그렇지 않은 경우에는 {@code class} 속성만 기록된다.
	 *
	 * @param parent	하위 요소를 추가할 상위 요소.
	 * @param childName	추가할 하위 요소 이름.
	 * @param obj		serialize할 객체.
	 * @return	추가된 하위 요소.
	 * @throws NullPointerException	{@code obj}가 {@code null}인 경우.
	 */
	public static FluentElement serialize(FluentElement parent, String childName, Object obj) {
		FluentElement elm = parent.appendChild(childName).attr("class", obj.getClass().getName());
		if ( obj instanceof XmlSerializable ) {
			((XmlSerializable)obj).toXml(elm);
		}
		
		return elm;
	}
	
	/**
	 * 주어진 XML 문자열을 파싱하여 문서의 최상위 요소에 해당하는 객체를 생성한다.
	 * 객체 생성 방식은 {@link #loadXmlSerializable(FluentElement)}를 따른다.
	 * <p>
	 * <b>주의:</b> XML 파싱에 실패하여 {@link XmlSerializationException}을 발생시킬 때
	 * 원인이 된 {@link SAXException}은 cause로 포함되지 않는다.
	 *
	 * @param xmlString	XML 문자열.
	 * @return	생성된 객체.
	 * @throws XmlSerializationException	XML 파싱 또는 객체 생성에 실패한 경우.
	 */
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
	 * @throws XmlSerializationException	'class' attribute가 없거나, 클래스 적재 또는 객체 생성·초기화에
	 * 									실패한 경우.
	 */
	@SuppressWarnings("unchecked")
	public static Object loadXmlSerializable(FluentElement topElm) {
		String clsName = topElm.attr("class")
								.getOrThrow(()->new XmlSerializationException(
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
				return ReflectionUtils.newInstance(cls);
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
	
	/**
	 * 주어진 {@link LoadableXmlSerializable} 클래스의 객체를 인자 없는 생성자로 생성하고,
	 * {@link LoadableXmlSerializable#loadFromXml(FluentElement)}를 호출하여 초기화한다.
	 *
	 * @param <T>	생성할 객체의 타입.
	 * @param cls	생성할 객체의 클래스.
	 * @param elm	객체 상태가 기록된 XML 요소.
	 * @return	생성 및 초기화된 객체.
	 * @throws XmlSerializationException	객체 생성 또는 초기화에 실패한 경우.
	 */
	public static <T extends LoadableXmlSerializable> T loadLoadableXmlSerializable(Class<T> cls, FluentElement elm) {
		try {
			T obj = ReflectionUtils.newInstance(cls);
			obj.loadFromXml(elm);
			
			return obj;
		}
		catch ( Throwable e ) {
			throw new XmlSerializationException("fails to load a LoadableXmlSerializable: class="
												+ cls.getName(), e);
		}
	}
}
