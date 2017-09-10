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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import io.vavr.control.Option;


/**
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
	
	public static Document createDocument() {
		return s_builder.newDocument();
	}
	
	public static Document parse(File file) throws SAXException, IOException {
		return s_builder.parse(file);
	}
	
	public static Document parse(InputStream is) throws SAXException, IOException {
		return s_builder.parse(is);
	}
	
	public static Document parse(String xmlString) throws SAXException {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(xmlString.getBytes(Charset.forName("UTF-8")));
			return s_builder.parse(bais);
		}
		catch ( IOException e ) {
			throw new RuntimeException(e);
		}
	}
	
	public static Element appendChildElement(Element parent, String childElmName) {
		Element child = parent.getOwnerDocument().createElementNS(null, childElmName);
		parent.appendChild(child);
		
		return child;
	}
	
	public static Option<Element> getFirstChildElement(Element parent) {
		Preconditions.checkArgument(parent != null, "Parent Element was null");

		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i) {
			Node child = children.item(i);

			if (child.getNodeType() == Node.ELEMENT_NODE) {
				return Option.some((Element) child);
			}
		}
		
		return Option.none();
	}
	
	public static Option<Element> getFirstChildElement(Element parent, String childElmName) {
		Preconditions.checkNotNull(parent, "parent should not be null");
		Preconditions.checkNotNull(childElmName, "childElmName should not be null");

		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE
				&& child.getNodeName().equals(childElmName)) {
				return Option.some((Element) child);
			}
		}

		return Option.none();
	}
	
	public static NodeIterable getChildren(Element elm) {
		return new NodeIterable(elm.getChildNodes());
	}
	
	public static Iterable<Element> getChildElements(Element elm) {
		return Iterables.transform(
				Iterables.filter(getChildren(elm), n -> n.getNodeType() == Node.ELEMENT_NODE),
				n -> (Element)n);
	}
	
	public static List<Element> getChildElementList(Element parent) {
		return Lists.newArrayList(getChildElements(parent));
	}
	
	public static Iterable<Element> getChildElements(Element elm, String childName) {
		Iterable<Node> it = getChildren(elm);
		it = Iterables.filter(it, n -> n.getNodeType() == Node.ELEMENT_NODE
									&& n.getLocalName().equals(childName));
		return Iterables.transform(it, n -> (Element)n);
	}
	
	public static List<Element> getChildElementList(Element parent, String childName) {
		return Lists.newArrayList(getChildElements(parent, childName));
	}
	
	public static Stream<Node> getChildrenStream(Element elm) {
		NodeIterable nodes = getChildren(elm);
		return StreamSupport.stream(nodes.spliterator(), false);
	}

	public static Option<String> getChildElementText(Element parent, String childTagName) {
		return getFirstChildElement(parent, childTagName).flatMap(XmlUtils::getText);
	}
	
	public static Option<String> getAttribute(Element elm, String attrName) {
		String attrValue = elm.getAttribute(attrName);
		return (attrValue.length() == 0) ? Option.none() : Option.some(attrValue);
	}
	
	public static Option<Boolean> getAttributeAsBoolean(Element elm, String attrName) {
		String attrValue = elm.getAttribute(attrName);
		return (attrValue.length() == 0) ? Option.none()
										: Option.some(Boolean.parseBoolean(attrValue));
	}
	
	/**
	 * 주어진 {@link Element}에 정의된 텍스트 문자열을 반환한다.
	 * 텍스트가 정의되지 않은 경우는 {@link Option#none()}를 반환한다.
	 * 
	 * @param elm	element 객체
	 * @return	Option 텍스트 객체
	 */
	public static Option<String> getText(Element elm) {
		NodeList nodeList = elm.getChildNodes();
		if ( nodeList.getLength() == 0 ) {
			return Option.none();
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
        return Option.some(buf.toString());
	}
	
	public static void appendText(Element parent, String text) {
		Preconditions.checkArgument(text != null, "text should not be null");
		parent.appendChild(parent.getOwnerDocument().createTextNode(text));
	}
	
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
