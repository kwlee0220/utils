package utils.xml;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import utils.Preconditions;
import utils.func.FOption;


/**
 * DOM({@link Document}, {@link Element}) 기반 XML 문서의 생성, 파싱 및 탐색을 위한 유틸리티 함수들을 정의한다.
 * <p>
 * 파싱과 문서 생성에는 namespace를 인식하도록 설정된 하나의 {@link DocumentBuilder}를 공유한다.
 * <b>주의:</b> {@link DocumentBuilder}는 thread-safe하지 않으므로, 여러 쓰레드에서
 * {@code parse}/{@code createDocument}를 동시에 호출하는 것은 안전하지 않다.
 * 또한 클래스 초기화 시 {@link DocumentBuilder} 생성에 실패하면 오류를 출력만 하므로,
 * 이후 호출은 {@link NullPointerException}을 발생시킨다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class XmlUtils {
	private static final DocumentBuilder s_builder;

	static {
		DocumentBuilder builder = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setIgnoringElementContentWhitespace(true);
			builder = factory.newDocumentBuilder();
		}
		catch ( ParserConfigurationException e ) {
			e.printStackTrace(System.err);
		}
		finally {
			s_builder = builder;
		}
	}
	
	/**
	 * 객체를 생성한다. 본 클래스는 static 메소드만 제공하므로 별도로 생성할 필요가 없다.
	 */
	public XmlUtils() { }
	
	/**
	 * 빈 XML 문서를 생성한다.
	 *
	 * @return	생성된 문서.
	 */
	public static Document createDocument() {
		return s_builder.newDocument();
	}
	
	/**
	 * 주어진 파일의 XML 내용을 파싱한다.
	 *
	 * @param file	XML 파일.
	 * @return	파싱된 문서.
	 * @throws SAXException	XML 파싱에 실패한 경우.
	 * @throws IOException	파일을 읽는 중 오류가 발생한 경우.
	 * @throws IllegalArgumentException	{@code file}이 {@code null}인 경우.
	 */
	public static Document parse(File file) throws SAXException, IOException {
		return s_builder.parse(file);
	}
	
	/**
	 * 주어진 입력 스트림의 XML 내용을 파싱한다. 스트림은 이 메소드에서 닫지 않는다.
	 *
	 * @param is	XML 입력 스트림.
	 * @return	파싱된 문서.
	 * @throws SAXException	XML 파싱에 실패한 경우.
	 * @throws IOException	스트림을 읽는 중 오류가 발생한 경우.
	 * @throws IllegalArgumentException	{@code is}가 {@code null}인 경우.
	 */
	public static Document parse(InputStream is) throws SAXException, IOException {
		return s_builder.parse(is);
	}
	
	/**
	 * 주어진 XML 문자열을 UTF-8로 인코딩하여 파싱한다.
	 *
	 * @param xmlString	XML 문자열.
	 * @return	파싱된 문서.
	 * @throws SAXException	XML 파싱에 실패한 경우.
	 * @throws RuntimeException	파싱 중 {@link IOException}이 발생한 경우(이를 감싼다).
	 * @throws NullPointerException	{@code xmlString}이 {@code null}인 경우.
	 */
	public static Document parse(String xmlString) throws SAXException {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(xmlString.getBytes(Charset.forName("UTF-8")));
			return s_builder.parse(bais);
		}
		catch ( IOException e ) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * 주어진 이름의 (namespace 없는) 하위 요소를 생성하여 {@code parent}의 마지막 자식으로 추가한다.
	 *
	 * @param parent		하위 요소를 추가할 상위 요소.
	 * @param childElmName	추가할 하위 요소 이름.
	 * @return	추가된 하위 요소.
	 */
	public static Element appendChildElement(Element parent, String childElmName) {
		Element child = parent.getOwnerDocument().createElementNS(null, childElmName);
		parent.appendChild(child);
		
		return child;
	}
	
	/**
	 * 주어진 요소의 첫 번째 자식 요소를 반환한다. 텍스트 등 요소가 아닌 자식 노드는 건너뛴다.
	 *
	 * @param parent	상위 요소.
	 * @return	첫 번째 자식 요소. 자식 요소가 없으면 {@link FOption#empty()}.
	 * @throws IllegalArgumentException	{@code parent}가 {@code null}인 경우.
	 */
	public static FOption<Element> getFirstChildElement(Element parent) {
		Preconditions.checkArgument(parent != null, "Parent Element was null");

		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i) {
			Node child = children.item(i);

			if (child.getNodeType() == Node.ELEMENT_NODE) {
				return FOption.of((Element) child);
			}
		}
		
		return FOption.empty();
	}
	
	/**
	 * 주어진 요소의 자식 요소 중 이름({@link Node#getNodeName()}, 접두어 포함)이
	 * {@code childElmName}과 같은 첫 번째 요소를 반환한다.
	 *
	 * @param parent		상위 요소.
	 * @param childElmName	찾을 자식 요소 이름.
	 * @return	찾은 자식 요소. 없으면 {@link FOption#empty()}.
	 * @throws IllegalArgumentException	{@code parent} 또는 {@code childElmName}이 {@code null}인 경우.
	 */
	public static FOption<Element> getFirstChildElement(Element parent, String childElmName) {
		Preconditions.checkNotNullArgument(parent, "parent should not be null");
		Preconditions.checkNotNullArgument(childElmName, "childElmName should not be null");

		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE
				&& child.getNodeName().equals(childElmName)) {
				return FOption.of((Element) child);
			}
		}

		return FOption.empty();
	}
	
	/**
	 * 주어진 요소의 모든 자식 노드(요소가 아닌 노드 포함)를 순회하는 {@link Iterable}을 반환한다.
	 *
	 * @param elm	상위 요소.
	 * @return	자식 노드 {@link Iterable}.
	 */
	public static NodeIterable getChildren(Element elm) {
		return new NodeIterable(elm.getChildNodes());
	}
	
	/**
	 * 주어진 요소의 자식 요소들을 순회하는 {@link Iterable}을 반환한다.
	 * 요소가 아닌 자식 노드는 제외되며, 결과는 순회 시점에 지연 계산된다.
	 *
	 * @param elm	상위 요소.
	 * @return	자식 요소 {@link Iterable}.
	 */
	public static Iterable<Element> getChildElements(Element elm) {
		return Iterables.transform(
				Iterables.filter(getChildren(elm), n -> n.getNodeType() == Node.ELEMENT_NODE),
				n -> (Element)n);
	}
	
	/**
	 * 주어진 요소의 자식 요소들을 리스트로 반환한다.
	 *
	 * @param parent	상위 요소.
	 * @return	자식 요소 리스트.
	 */
	public static List<Element> getChildElementList(Element parent) {
		return Lists.newArrayList(getChildElements(parent));
	}
	
	/**
	 * 주어진 요소의 자식 요소 중 local name({@link Node#getLocalName()})이 {@code childName}과 같은
	 * 요소들을 순회하는 {@link Iterable}을 반환한다. 결과는 순회 시점에 지연 계산된다.
	 * <p>
	 * <b>주의:</b> {@link #getFirstChildElement(Element, String)}이 접두어를 포함한 node name으로
	 * 비교하는 것과 달리 local name으로 비교한다. 또한 namespace 없이(DOM Level 1 방식으로) 생성된
	 * 자식 요소는 local name이 {@code null}이므로 순회 시 {@link NullPointerException}이 발생한다.
	 *
	 * @param elm		상위 요소.
	 * @param childName	찾을 자식 요소의 local name.
	 * @return	이름이 일치하는 자식 요소 {@link Iterable}.
	 */
	public static Iterable<Element> getChildElements(Element elm, String childName) {
		Iterable<Node> it = getChildren(elm);
		it = Iterables.filter(it, n -> n.getNodeType() == Node.ELEMENT_NODE
									&& n.getLocalName().equals(childName));
		return Iterables.transform(it, n -> (Element)n);
	}
	
	/**
	 * 주어진 요소의 자식 요소 중 local name이 {@code childName}과 같은 요소들을 리스트로 반환한다.
	 * 비교 방식은 {@link #getChildElements(Element, String)}과 같다.
	 *
	 * @param parent	상위 요소.
	 * @param childName	찾을 자식 요소의 local name.
	 * @return	이름이 일치하는 자식 요소 리스트.
	 */
	public static List<Element> getChildElementList(Element parent, String childName) {
		return Lists.newArrayList(getChildElements(parent, childName));
	}
	
	/**
	 * 주어진 요소의 모든 자식 노드(요소가 아닌 노드 포함)로 구성된 순차 {@link Stream}을 반환한다.
	 *
	 * @param elm	상위 요소.
	 * @return	자식 노드 스트림.
	 */
	public static Stream<Node> getChildrenStream(Element elm) {
		NodeIterable nodes = getChildren(elm);
		return StreamSupport.stream(nodes.spliterator(), false);
	}

	/**
	 * 이름이 {@code childTagName}인 첫 번째 자식 요소의 텍스트를 반환한다.
	 * 자식 요소 탐색은 {@link #getFirstChildElement(Element, String)}을, 텍스트 추출은
	 * {@link #getText(Element)}를 따른다.
	 *
	 * @param parent		상위 요소.
	 * @param childTagName	자식 요소 이름.
	 * @return	자식 요소의 텍스트. 해당 자식 요소가 없으면 {@link FOption#empty()}.
	 * @throws IllegalArgumentException	{@code parent} 또는 {@code childTagName}이 {@code null}인 경우.
	 */
	public static FOption<String> getChildElementText(Element parent, String childTagName) {
		return getFirstChildElement(parent, childTagName).flatMap(XmlUtils::getText);
	}
	
	/**
	 * 주어진 요소의 속성 값을 반환한다.
	 * 속성이 없거나 값이 빈 문자열인 경우는 모두 {@link FOption#empty()}를 반환한다.
	 *
	 * @param elm		대상 요소.
	 * @param attrName	속성 이름.
	 * @return	속성 값.
	 */
	public static FOption<String> getAttribute(Element elm, String attrName) {
		String attrValue = elm.getAttribute(attrName);
		return (attrValue.length() == 0) ? FOption.empty() : FOption.of(attrValue);
	}
	
	/**
	 * 주어진 요소의 속성 값을 {@link Boolean#parseBoolean(String)}으로 변환하여 반환한다.
	 * 값이 {@code "true"}(대소문자 무시)가 아니면 {@code false}로 변환되며,
	 * 속성이 없거나 값이 빈 문자열인 경우는 {@link FOption#empty()}를 반환한다.
	 *
	 * @param elm		대상 요소.
	 * @param attrName	속성 이름.
	 * @return	속성의 boolean 값.
	 */
	public static FOption<Boolean> getAttributeAsBoolean(Element elm, String attrName) {
		String attrValue = elm.getAttribute(attrName);
		return (attrValue.length() == 0) ? FOption.empty()
										: FOption.of(Boolean.parseBoolean(attrValue));
	}
	
	/**
	 * 주어진 {@link Element}에 정의된 텍스트 문자열을 반환한다.
	 * 텍스트가 정의되지 않은 경우는 {@link FOption#empty()}를 반환한다.
	 * 
	 * @param elm	element 객체
	 * @return	FOption 텍스트 객체
	 */
	public static FOption<String> getText(Element elm) {
		NodeList nodeList = elm.getChildNodes();
		if ( nodeList.getLength() == 0 ) {
//			return FOption.empty();
			return FOption.of("");
		}

		StringBuilder buf = new StringBuilder();

		for ( int i = 0; i < nodeList.getLength(); ++i ) {
			Node node = nodeList.item(i);

        	if ( node.getNodeType() == Node.TEXT_NODE
        		|| node.getNodeType() == Node.CDATA_SECTION_NODE ) {
        		buf.append(node.getNodeValue().trim());
        	}
        	else {
        		break;
        	}
        }
        return FOption.of(buf.toString());
	}
	
	/**
	 * 주어진 텍스트로 텍스트 노드를 생성하여 {@code parent}의 마지막 자식으로 추가한다.
	 *
	 * @param parent	텍스트를 추가할 요소.
	 * @param text		추가할 텍스트.
	 * @throws IllegalArgumentException	{@code text}가 {@code null}인 경우.
	 */
	public static void appendText(Element parent, String text) {
		Preconditions.checkArgument(text != null, "text should not be null");
		parent.appendChild(parent.getOwnerDocument().createTextNode(text));
	}
	
	/**
	 * 주어진 텍스트로 CDATA section 노드를 생성하여 {@code parent}의 마지막 자식으로 추가한다.
	 *
	 * @param parent	CDATA section을 추가할 요소.
	 * @param text		CDATA section의 내용.
	 */
	public static void appendCDATASection(Element parent, String text) {
		parent.appendChild(parent.getOwnerDocument().createCDATASection(text));
	}
	
	static class NodeIterable implements Iterable<Node> {
		private final NodeList m_list;
		
		public NodeIterable(NodeList list) {
			m_list = list;
		}

		@Override
		public Iterator<Node> iterator() {
			return new NodeIterator();
		}
		
		class NodeIterator implements Iterator<Node> {
			private int m_nextIdx = 0;

			@Override
			public boolean hasNext() {
				return m_nextIdx < m_list.getLength();
			}

			@Override
			public Node next() {
				int idx = m_nextIdx;
				++m_nextIdx;
				return m_list.item(idx);
			}
		}
	}
}
