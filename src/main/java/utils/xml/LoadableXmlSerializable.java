package utils.xml;

/**
 * XML 요소로부터 자신의 상태를 적재할 수 있는 {@link XmlSerializable} 객체의 인터페이스.
 * <p>
 * {@link XmlSerializable#loadXmlSerializable(FluentElement)}는 이 인터페이스를 구현한 클래스의
 * 인자 없는 생성자로 객체를 생성한 뒤 {@link #loadFromXml(FluentElement)}를 호출하여 초기화한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface LoadableXmlSerializable extends XmlSerializable {
	/**
	 * 주어진 XML 요소의 내용으로 본 객체의 상태를 초기화한다.
	 *
	 * @param topElm	객체 상태가 기록된 XML 요소.
	 */
	public void loadFromXml(FluentElement topElm);
}
