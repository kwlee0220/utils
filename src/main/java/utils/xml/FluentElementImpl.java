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

import io.vavr.control.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FluentElementImpl implements FluentElement {
	private Element m_elm;

	@Override
	public boolean exists() {
		return true;
	}
	
	public static FluentElementImpl root(Document doc, String name) {
		Element elm = doc.createElementNS(null, name);
		doc.appendChild(elm);
		
		return new FluentElementImpl(elm);
	}
	
	public static FluentElementImpl create(String name) {
		return root(XmlUtils.createDocument(), name);
	}
	
	public static FluentElementImpl of(Element elm) {
		return new FluentElementImpl(elm);
	}
	
	public static FluentElementImpl of(File file) throws SAXException, IOException{
		return of(XmlUtils.parse(file).getDocumentElement());
	}
	
	public static FluentElementImpl of(String xmlString) throws SAXException {
		return of(XmlUtils.parse(xmlString).getDocumentElement());
	}
	
	public FluentElementImpl(Element elm) {
		m_elm = elm;
	}
	
	@Override
	public Element get() {
		return m_elm;
	}
	
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

	@Override
	public Option<String> attr(String name) {
		String attr = get().getAttribute(name);
		return ( attr.length() > 0 ) ? Option.some(attr) : Option.none();
	}
	
	@Override
	public FluentElement attr(String name, Object value) {
		get().setAttribute(name, value.toString());
		return this;
	}
	
	@Override
	public Option<String> text() {
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
