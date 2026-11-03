package utils.xml;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.google.common.collect.Iterables;

import utils.func.FOption;


/**
 * DOM {@link Element}를 감싸는 {@link FluentElement}의 기본 구현.
 * <p>
 * 본 객체는 항상 존재하는 element를 나타내므로 {@link #exists()}는 항상 {@code true}를 반환한다.
 * 생성자나 {@link #of(Element)}는 인자를 검사하지 않으므로 {@code null} element를 감쌀 수 있으며,
 * 이 경우 이후 접근 시 {@link NullPointerException}이 발생한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FluentElementImpl implements FluentElement {
	private Element m_elm;

	@Override
	public boolean exists() {
		return true;
	}
	
	/**
	 * 주어진 이름의 element를 생성하여 문서의 루트 element로 추가한다.
	 * <p>
	 * element는 namespace 없이 생성된다. 문서에 이미 루트 element가 있으면
	 * {@link org.w3c.dom.DOMException}이 발생한다.
	 * 
	 * @param doc	루트 element를 추가할 DOM 문서.
	 * @param name	루트 element의 이름.
	 * @return	추가된 루트 element.
	 */
	public static FluentElementImpl root(Document doc, String name) {
		Element elm = doc.createElementNS(null, name);
		doc.appendChild(elm);
		
		return new FluentElementImpl(elm);
	}
	
	/**
	 * 새 빈 문서를 만들고 주어진 이름의 루트 element를 추가하여 반환한다.
	 * 
	 * @param name	루트 element의 이름.
	 * @return	추가된 루트 element.
	 */
	public static FluentElementImpl create(String name) {
		return root(XmlUtils.createDocument(), name);
	}
	
	/**
	 * 주어진 DOM element를 감싸는 객체를 생성한다.
	 * 
	 * @param elm	감쌀 DOM element.
	 * @return	생성된 객체.
	 */
	public static FluentElementImpl of(Element elm) {
		return new FluentElementImpl(elm);
	}
	
	/**
	 * XML 파일을 파싱하여 그 루트 element를 감싸는 객체를 생성한다.
	 * 
	 * @param file	파싱할 XML 파일.
	 * @return	루트 element.
	 * @throws SAXException	XML 파싱에 실패한 경우.
	 * @throws IOException	파일을 읽는 중 오류가 발생한 경우.
	 */
	public static FluentElementImpl of(File file) throws SAXException, IOException{
		return of(XmlUtils.parse(file).getDocumentElement());
	}
	
	/**
	 * XML 문자열을 UTF-8로 인코딩한 뒤 파싱하여 그 루트 element를 감싸는 객체를 생성한다.
	 * 
	 * @param xmlString	파싱할 XML 문자열.
	 * @return	루트 element.
	 * @throws SAXException	XML 파싱에 실패한 경우.
	 */
	public static FluentElementImpl of(String xmlString) throws SAXException {
		return of(XmlUtils.parse(xmlString).getDocumentElement());
	}
	
	/**
	 * 주어진 DOM element를 감싸는 객체를 생성한다.
	 * 
	 * @param elm	감쌀 DOM element.
	 */
	public FluentElementImpl(Element elm) {
		m_elm = elm;
	}
	
	@Override
	public Element get() {
		return m_elm;
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * <b>주의:</b> 부모가 element가 아니면 부모가 {@code null}인 존재하지 않는 element를 반환하므로,
	 * 그 객체의 {@link #parent()}는 {@code null}을 반환한다.
	 */
	@Override
	public FluentElement parent() {
		Node parent = m_elm.getParentNode();
		if ( parent != null && parent.getNodeType() == Node.ELEMENT_NODE ) {
			return FluentElementImpl.of((Element)parent); 
		}
		else {
			return new NonExistentElement(null);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * <b>주의:</b> DOM은 정의되지 않은 속성에 대해 빈 문자열을 반환하므로, 값이 빈 문자열로 정의된
	 * 속성도 {@link FOption#empty()}로 반환된다.
	 */
	@Override
	public FOption<String> attr(String name) {
		String attr = get().getAttribute(name);
		return ( attr.length() > 0 ) ? FOption.of(attr) : FOption.empty();
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * 값은 {@code value.toString()}으로 변환되어 설정되며, {@code value}가 {@code null}이면
	 * {@link NullPointerException}이 발생한다.
	 */
	@Override
	public FluentElement attr(String name, Object value) {
		get().setAttribute(name, value.toString());
		return this;
	}
	
	@Override
	public FOption<String> text() {
		return XmlUtils.getText(get());
	}
	
	@Override
	public FluentElement text(String text) {
		XmlUtils.appendText(get(), text);
		return this;
	}
	
	@Override
	public FluentElement cdata(String text) {
		XmlUtils.appendCDATASection(get(), text);
		return this;
	}

	@Override
	public Stream<FluentElement> children() {
		return XmlUtils.getChildrenStream(m_elm)
						.filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
						.map(node -> FluentElementImpl.of((Element)node));
	}

	@Override
	public Stream<FluentElement> children(String name) {
		return XmlUtils.getChildrenStream(m_elm)
						.filter(node -> node.getNodeType() == Node.ELEMENT_NODE
										&& name.equals(node.getLocalName()))
						.map(node -> FluentElementImpl.of((Element)node));
	}

	@Override
	public Iterable<FluentElement> iterateChildren() {
		Iterable<Node> elms = Iterables.filter(XmlUtils.getChildren(m_elm),
												node->node.getNodeType() == Node.ELEMENT_NODE);
		return Iterables.transform(elms, node->FluentElementImpl.of((Element)node));
	}

	@Override
	public Iterable<FluentElement> iterateChildren(String name) {
		Iterable<Node> elms = Iterables.filter(XmlUtils.getChildren(m_elm),
												node-> node.getNodeType() == Node.ELEMENT_NODE
												&& name.equals(node.getLocalName()));
		return Iterables.transform(elms, node->FluentElementImpl.of((Element)node));
	}

	@Override
	public FluentElement firstChild() {
		Optional<FluentElement> ochild = children().findFirst();
		return ochild.orElseThrow(()->new XmlSerializationException("no child element"));
	}
	
	@Override
	public FluentElement firstChild(String name) {
		Optional<FluentElement> ochild = children(name).findFirst();
		return ochild.orElseThrow(()->new XmlSerializationException("no child element: name="+ name));
	}

	@Override
	public FluentElement with(Consumer<FluentElement> consumer) {
		consumer.accept(this);
		return this;
	}

	@Override
	public FluentElementImpl appendChild(String childName) {
		return of(XmlUtils.appendChildElement(m_elm, childName));
	}

	@Override
	public String toString() {
		try {
			StringWriter writer = new StringWriter();
			XmlWrite.create()
					.from(m_elm)
					.to(writer)
					.indent(2)
					.omitXmlDeclaration(true)
					.run();
			
			return writer.toString();
		}
		catch ( Exception e ) {
			return "toString failed: cause=" + e;
		}
	}
}
